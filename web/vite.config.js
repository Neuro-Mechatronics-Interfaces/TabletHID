import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
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
