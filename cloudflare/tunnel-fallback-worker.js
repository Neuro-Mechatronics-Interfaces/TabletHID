/**
 * Cloudflare Worker: tunnel-fallback
 *
 * Deploy this on the tablet.nml.wtf route in Cloudflare Workers.
 * When the cloudflared tunnel is reachable it passes the request straight
 * through.  If the tunnel is down (connection refused, timeout, etc.) it
 * redirects the visitor to the GitHub Pages static mirror so the app-store
 * links and privacy page remain accessible.
 *
 * Subrequests from a Worker to the same zone go directly to the origin and do
 * NOT re-trigger this Worker, so there is no redirect loop.
 *
 * FALLBACK_ORIGIN must be set as a Worker environment variable (plain text):
 *   https://neuro-mechatronics-interfaces.github.io/TabletHID
 * (no trailing slash)
 */

export default {
  async fetch(request, env) {
    const fallback = (env.FALLBACK_ORIGIN ?? 'https://neuro-mechatronics-interfaces.github.io/TabletHID').replace(/\/$/, '');

    try {
      const response = await fetch(request.clone(), { redirect: 'manual' });
      return response;
    } catch (_) {
      // Tunnel is unreachable — send the visitor to the static mirror.
      // Preserve the path so e.g. /privacy lands on /TabletHID/privacy.
      const url = new URL(request.url);
      const dest = fallback + (url.pathname === '/' ? '/' : url.pathname);
      return Response.redirect(dest, 302);
    }
  },
};
