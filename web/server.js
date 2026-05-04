import express from 'express';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
const PORT = 12122;

app.use(express.static(path.join(__dirname, 'dist')));

// SPA fallback — let React Router handle all routes
app.get('*', (_req, res) => {
  res.sendFile(path.join(__dirname, 'dist', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`TabletHID web running on http://localhost:${PORT}`);
});
