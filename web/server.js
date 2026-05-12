import 'dotenv/config';
import express from 'express';
import path from 'path';
import { fileURLToPath } from 'url';
import apiRouter from './api/router.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
const PORT = Number(process.env.PORT ?? 12122);
const TIME_ZONE = 'America/New_York';

app.set('trust proxy', 1);

const supportedRoutes = new Map([
  ['/', { name: 'home' }],
  ['/support', { name: 'support', scope: 'general' }],
  ['/support/ios', { name: 'support', scope: 'ios', platform: 'ios' }],
  ['/support/android', { name: 'support', scope: 'android', platform: 'android' }],
  ['/privacy', { name: 'privacy' }],
  ['/configs', { name: 'configs' }],
  ['/beta', { name: 'beta' }],
  ['/admin', { name: 'admin' }],
]);

function normalizeRoute(url) {
  const pathname = new URL(url, 'http://localhost').pathname;
  if (pathname !== '/' && pathname.endsWith('/')) return pathname.slice(0, -1);
  return pathname;
}

function timeZoneOffsetMinutes(date, parts) {
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  const zonedTime = Date.UTC(
    Number(values.year),
    Number(values.month) - 1,
    Number(values.day),
    Number(values.hour),
    Number(values.minute),
    Number(values.second),
    Math.floor(date.getMilliseconds() / 100) * 100,
  );

  return Math.round((zonedTime - date.getTime()) / 60000);
}

function formatNewYorkIso(date = new Date()) {
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone: TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    fractionalSecondDigits: 1,
    hour12: false,
  });
  const parts = formatter.formatToParts(date);
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  const offset = timeZoneOffsetMinutes(date, parts);
  const sign = offset >= 0 ? '+' : '-';
  const absOffset = Math.abs(offset);
  const offsetHours = String(Math.floor(absOffset / 60)).padStart(2, '0');
  const offsetMinutes = String(absOffset % 60).padStart(2, '0');

  return `${values.year}-${values.month}-${values.day}T${values.hour}:${values.minute}:${values.second}.${values.fractionalSecond}${sign}${offsetHours}:${offsetMinutes}`;
}

function queryMetadata(req) {
  const entries = Object.entries(req.query ?? {});
  if (!entries.length) return undefined;
  return Object.fromEntries(entries.map(([key, value]) => [
    key,
    Array.isArray(value) ? value.map(String) : String(value),
  ]));
}

function logRouteVisit(req, res, route, routeMeta, startedAt) {
  const durationMs = Number(process.hrtime.bigint() - startedAt) / 1_000_000;
  const log = {
    timestamp: formatNewYorkIso(),
    level: 'info',
    event: 'route_visit',
    route,
    page: routeMeta.name,
    method: req.method,
    status: res.statusCode,
    duration_ms: Number(durationMs.toFixed(1)),
    ip: req.ip,
    forwarded_for: req.get('x-forwarded-for'),
    user_agent: req.get('user-agent'),
    referer: req.get('referer') || req.get('referrer'),
    accept_language: req.get('accept-language'),
  };

  if (routeMeta.scope) log.scope = routeMeta.scope;
  if (routeMeta.platform) log.platform = routeMeta.platform;

  const query = queryMetadata(req);
  if (query) log.query = query;

  console.log(JSON.stringify(log));
}

app.use((req, res, next) => {
  const route = normalizeRoute(req.originalUrl);
  const routeMeta = supportedRoutes.get(route);

  if (routeMeta && ['GET', 'HEAD'].includes(req.method)) {
    const startedAt = process.hrtime.bigint();
    res.on('finish', () => logRouteVisit(req, res, route, routeMeta, startedAt));
  }

  next();
});

app.use(express.static(path.join(__dirname, 'dist')));

app.use('/api/v1', express.json({ limit: '64kb' }), apiRouter);

// SPA fallback — let React Router handle all routes
app.get('*', (_req, res) => {
  res.sendFile(path.join(__dirname, 'dist', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`TabletHID web running on http://localhost:${PORT}`);
});
