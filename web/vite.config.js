import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// PAGES_BASE is set by the GitHub Actions deploy workflow to '/TabletHID/' so
// the built assets resolve correctly under the GitHub Pages sub-path.  When
// serving from the root (cloudflared tunnel or a custom Pages domain) this env
// var is absent and the build defaults to '/'.
const base = process.env.PAGES_BASE ?? '/';

export default defineConfig({
  base,
  plugins: [react()],
  server: {
    port: 12122,
    host: true,
    allowedHosts: 'tablet.nml.wtf',
    proxy: {
      '/api': { target: 'http://localhost:12123', changeOrigin: true },
    },
  },
  preview: { port: 12122, host: true },
});
