import crypto from 'crypto';

// Admin authentication via Cloudflare Access.
//
// Cloudflare Access signs every request it forwards with an RS256 JWT in the
// `Cf-Access-Jwt-Assertion` header. The email header Access also adds
// (`Cf-Access-Authenticated-User-Email`) is an unsigned plain header, so any
// client that reaches the origin without passing through Access (direct IP,
// a different tunnel hostname, a LAN client) can forge it. The admin email is
// therefore taken only from the verified JWT claims.
//
// Required environment:
//   ADMIN_EMAILS           comma-separated allowlist of admin emails
//   CF_ACCESS_TEAM_DOMAIN  e.g. https://<team>.cloudflareaccess.com
//   CF_ACCESS_AUD          Application Audience (AUD) tag of the admin Access
//                          application; comma-separate to allow several
// Development only (NODE_ENV=development):
//   ADMIN_DEV_EMAIL        skip JWT verification and act as this admin

const CLOCK_SKEW_SEC = 60;
const JWKS_TTL_MS = 60 * 60 * 1000;
const JWKS_MIN_REFRESH_MS = 30 * 1000;
const JWKS_FETCH_TIMEOUT_MS = 5000;

function adminEmails() {
  return new Set(
    (process.env.ADMIN_EMAILS ?? '').split(',').map(e => e.trim().toLowerCase()).filter(Boolean),
  );
}

function accessConfig() {
  const teamDomain = (process.env.CF_ACCESS_TEAM_DOMAIN ?? '').trim().replace(/\/+$/, '');
  const audiences = (process.env.CF_ACCESS_AUD ?? '').split(',').map(a => a.trim()).filter(Boolean);
  if (!teamDomain || audiences.length === 0) return null;
  const issuer = /^https?:\/\//i.test(teamDomain) ? teamDomain : `https://${teamDomain}`;
  return { issuer, audiences, certsUrl: `${issuer}/cdn-cgi/access/certs` };
}

// ── JWKS cache ────────────────────────────────────────────────────────────────

const jwksCache = { url: null, keys: new Map(), fetchedAt: 0, inflight: null };

async function fetchJwks(certsUrl) {
  const res = await fetch(certsUrl, { signal: AbortSignal.timeout(JWKS_FETCH_TIMEOUT_MS) });
  if (!res.ok) throw new Error(`JWKS fetch failed: HTTP ${res.status}`);
  const body = await res.json();
  const keys = new Map();
  for (const jwk of Array.isArray(body?.keys) ? body.keys : []) {
    if (jwk?.kty !== 'RSA' || typeof jwk.kid !== 'string') continue;
    try {
      keys.set(jwk.kid, crypto.createPublicKey({ key: jwk, format: 'jwk' }));
    } catch {
      // skip malformed key
    }
  }
  if (keys.size === 0) throw new Error('JWKS contained no usable RSA keys');
  return keys;
}

async function getSigningKey(certsUrl, kid) {
  const now = Date.now();
  if (jwksCache.url !== certsUrl) {
    jwksCache.url = certsUrl;
    jwksCache.keys = new Map();
    jwksCache.fetchedAt = 0;
  }
  const stale = now - jwksCache.fetchedAt > JWKS_TTL_MS;
  const unknownKid = !jwksCache.keys.has(kid);
  const mayRefresh = now - jwksCache.fetchedAt > JWKS_MIN_REFRESH_MS;
  if ((stale || unknownKid) && mayRefresh) {
    if (!jwksCache.inflight) {
      jwksCache.inflight = fetchJwks(certsUrl)
        .then((keys) => { jwksCache.keys = keys; jwksCache.fetchedAt = Date.now(); })
        .finally(() => { jwksCache.inflight = null; });
    }
    try {
      await jwksCache.inflight;
    } catch (err) {
      // Keep serving cached keys if a refresh fails; a cold cache fails closed below.
      logAuth('admin_jwks_error', { error: err?.message ?? String(err) });
    }
  }
  return jwksCache.keys.get(kid) ?? null;
}

// ── JWT verification ─────────────────────────────────────────────────────────

function decodeSegment(segment) {
  if (!/^[A-Za-z0-9_-]+$/.test(segment)) throw new Error('bad base64url');
  return JSON.parse(Buffer.from(segment, 'base64url').toString('utf8'));
}

/**
 * Verifies a Cloudflare Access JWT. Returns the payload on success, or throws
 * an Error whose message names the failed check.
 */
export async function verifyAccessJwt(token, config) {
  if (typeof token !== 'string' || token.length > 8192) throw new Error('missing token');
  const parts = token.split('.');
  if (parts.length !== 3) throw new Error('malformed token');
  const [h, p, s] = parts;

  const header = decodeSegment(h);
  if (header?.alg !== 'RS256') throw new Error('unexpected alg');
  if (typeof header.kid !== 'string') throw new Error('missing kid');

  const key = await getSigningKey(config.certsUrl, header.kid);
  if (!key) throw new Error('unknown signing key');

  if (!/^[A-Za-z0-9_-]+$/.test(s)) throw new Error('bad signature encoding');
  const valid = crypto.verify(
    'RSA-SHA256',
    Buffer.from(`${h}.${p}`, 'ascii'),
    key,
    Buffer.from(s, 'base64url'),
  );
  if (!valid) throw new Error('bad signature');

  const payload = decodeSegment(p);
  const now = Math.floor(Date.now() / 1000);
  if (payload?.iss !== config.issuer) throw new Error('bad issuer');
  const aud = Array.isArray(payload.aud) ? payload.aud : [payload.aud];
  if (!aud.some(a => config.audiences.includes(a))) throw new Error('bad audience');
  if (typeof payload.exp !== 'number' || payload.exp + CLOCK_SKEW_SEC < now) throw new Error('expired');
  if (typeof payload.nbf === 'number' && payload.nbf - CLOCK_SKEW_SEC > now) throw new Error('not yet valid');
  if (typeof payload.iat === 'number' && payload.iat - CLOCK_SKEW_SEC > now) throw new Error('issued in future');
  return payload;
}

function logAuth(event, fields) {
  console.log(JSON.stringify({ timestamp: new Date().toISOString(), level: 'warn', event, ...fields }));
}

// ── Middleware ───────────────────────────────────────────────────────────────

export async function requireAdmin(req, res, next) {
  const allowed = adminEmails();

  // Local development bypass. Requires an explicit NODE_ENV=development so an
  // unset NODE_ENV on the server never enables it.
  if (process.env.NODE_ENV === 'development') {
    const devEmail = (process.env.ADMIN_DEV_EMAIL ?? '').trim().toLowerCase();
    if (devEmail && allowed.has(devEmail)) {
      req.adminEmail = devEmail;
      return next();
    }
  }

  const config = accessConfig();
  if (!config) {
    logAuth('admin_auth_misconfigured', { error: 'CF_ACCESS_TEAM_DOMAIN and CF_ACCESS_AUD must be set' });
    return res.status(503).json({ error: 'Admin authentication is not configured.' });
  }

  let email;
  try {
    const payload = await verifyAccessJwt(req.get('Cf-Access-Jwt-Assertion'), config);
    email = typeof payload.email === 'string' ? payload.email.trim().toLowerCase() : '';
    if (!email || !allowed.has(email)) throw new Error('email not on admin allowlist');
  } catch (err) {
    logAuth('admin_auth_denied', {
      reason: err?.message ?? String(err),
      ip: req.ip,
      path: req.originalUrl,
    });
    return res.status(403).json({ error: 'Forbidden.' });
  }
  req.adminEmail = email;
  return next();
}
