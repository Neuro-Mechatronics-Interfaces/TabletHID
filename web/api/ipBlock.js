import net from 'net';

// Site-wide IP blocklist. Entries are single addresses or CIDR ranges.
// Extra entries can be supplied via BLOCKED_IPS (comma-separated).
const DEFAULT_BLOCKED = [
  '2a02:6ea0:1901:2::e02f',
];

function buildBlockList(entries) {
  const list = new net.BlockList();
  for (const raw of entries) {
    const entry = raw.trim();
    if (!entry) continue;
    const [addr, prefix] = entry.split('/');
    const family = net.isIPv6(addr) ? 'ipv6' : net.isIPv4(addr) ? 'ipv4' : null;
    if (!family) {
      console.error(JSON.stringify({ level: 'error', event: 'ip_block_invalid_entry', entry }));
      continue;
    }
    if (prefix !== undefined) list.addSubnet(addr, Number(prefix), family);
    else list.addAddress(addr, family);
  }
  return list;
}

const blockList = buildBlockList([
  ...DEFAULT_BLOCKED,
  ...(process.env.BLOCKED_IPS ?? '').split(','),
]);

// Case-insensitive substrings matched against the User-Agent header. Extra
// entries can be supplied via BLOCKED_USER_AGENTS (comma-separated).
const DEFAULT_BLOCKED_USER_AGENTS = [
  '+https://ousan.io/bot',
  '+https://crawler.exa.ai/',
];

const blockedUserAgents = [
  ...DEFAULT_BLOCKED_USER_AGENTS,
  ...(process.env.BLOCKED_USER_AGENTS ?? '').split(','),
].map(s => s.trim().toLowerCase()).filter(Boolean);

function blockedUserAgentMatch(userAgent) {
  if (typeof userAgent !== 'string' || !userAgent) return null;
  const ua = userAgent.toLowerCase();
  return blockedUserAgents.find(pattern => ua.includes(pattern)) ?? null;
}

function reject(req, res, fields) {
  console.log(JSON.stringify({
    timestamp: new Date().toISOString(),
    level: 'warn',
    path: req.originalUrl,
    ...fields,
  }));
  res.status(403).type('text/plain').send('Forbidden');
}

function normalizeCandidate(value) {
  if (typeof value !== 'string') return null;
  let ip = value.trim();
  if (ip.startsWith('[') && ip.includes(']')) ip = ip.slice(1, ip.indexOf(']'));
  if (ip.toLowerCase().startsWith('::ffff:') && net.isIPv4(ip.slice(7))) ip = ip.slice(7);
  return net.isIP(ip) ? ip : null;
}

function isBlocked(ip) {
  return blockList.check(ip, net.isIPv6(ip) ? 'ipv6' : 'ipv4');
}

// Checks the socket peer, Express's resolved client IP, Cloudflare's
// CF-Connecting-IP, and every X-Forwarded-For hop. A request is rejected if
// any of them matches, so a spoofed header can only cause a block, never
// bypass one.
export function ipBlock(req, res, next) {
  // robots.txt stays readable so compliant crawlers can see their Disallow rule.
  const uaMatch = req.path === '/robots.txt' ? null : blockedUserAgentMatch(req.get('user-agent'));
  if (uaMatch) {
    reject(req, res, { event: 'user_agent_blocked', ip: req.ip, match: uaMatch });
    return;
  }

  const candidates = [
    req.socket?.remoteAddress,
    req.ip,
    req.get('cf-connecting-ip'),
    ...(req.get('x-forwarded-for') ?? '').split(','),
  ];
  for (const candidate of candidates) {
    const ip = normalizeCandidate(candidate);
    if (ip && isBlocked(ip)) {
      reject(req, res, { event: 'ip_blocked', ip });
      return;
    }
  }
  next();
}
