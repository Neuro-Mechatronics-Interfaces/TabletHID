const ADMIN_EMAILS = new Set(
  (process.env.ADMIN_EMAILS ?? '').split(',').map(e => e.trim().toLowerCase()).filter(Boolean),
);

export function requireAdmin(req, res, next) {
  // In development, allow override via env var
  if (process.env.NODE_ENV !== 'production') {
    const devEmail = (process.env.ADMIN_DEV_EMAIL ?? '').trim().toLowerCase();
    if (devEmail && ADMIN_EMAILS.has(devEmail)) {
      req.adminEmail = devEmail;
      return next();
    }
  }

  // Cloudflare Access injects this header after the user authenticates via Zero Trust
  const email = (req.get('Cf-Access-Authenticated-User-Email') ?? '').trim().toLowerCase();
  if (!email || !ADMIN_EMAILS.has(email)) {
    return res.status(403).json({ error: 'Forbidden.' });
  }
  req.adminEmail = email;
  next();
}
