import rateLimit from 'express-rate-limit';

export const uploadRateLimiter = rateLimit({
  windowMs: 60 * 60 * 1000,
  max: 5,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many uploads from this IP, please try again later.' },
});

export function bodySizeLimit(req, res, next) {
  const contentLength = req.headers['content-length'];
  if (contentLength !== undefined && Number(contentLength) > 65536) {
    res.status(413).json({ error: 'Request body too large.' });
    return;
  }
  next();
}

export function securityHeaders(_req, res, next) {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  next();
}
