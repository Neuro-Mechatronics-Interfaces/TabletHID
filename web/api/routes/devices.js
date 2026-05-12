import crypto from 'crypto';
import db from '../db.js';
import { stripControlChars, trimAndClamp } from '../sanitize.js';

function parseDevice(row) {
  return {
    id: row.id,
    name: row.name,
    class: row.class,
    widthDp: row.width_dp,
    heightDp: row.height_dp,
    density: row.density,
    isBuiltin: row.is_builtin === 1,
  };
}

function slug(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 48);
}

export function listDevices(_req, res) {
  try {
    const rows = db.prepare(`
      SELECT * FROM device_presets
      ORDER BY is_builtin DESC, name COLLATE NOCASE
    `).all();
    return res.json({ devices: rows.map(parseDevice) });
  } catch {
    return res.status(500).json({ error: 'Internal server error.' });
  }
}

export function createDevice(req, res) {
  try {
    const body = req.body ?? {};
    const name = stripControlChars(trimAndClamp(body.name ?? '', 80));
    const deviceClass = body.class === 'phone' ? 'phone' : 'tablet';
    const widthDp = Number(body.widthDp);
    const heightDp = Number(body.heightDp);
    const density = Number(body.density ?? 2);

    if (!name) return res.status(400).json({ error: 'Device name is required.' });
    if (!Number.isInteger(widthDp) || widthDp < 120 || widthDp > 3000) {
      return res.status(400).json({ error: 'widthDp must be an integer between 120 and 3000.' });
    }
    if (!Number.isInteger(heightDp) || heightDp < 120 || heightDp > 3000) {
      return res.status(400).json({ error: 'heightDp must be an integer between 120 and 3000.' });
    }
    if (!Number.isFinite(density) || density < 0.5 || density > 5) {
      return res.status(400).json({ error: 'density must be between 0.5 and 5.' });
    }

    const baseId = slug(name) || 'custom-device';
    let id = baseId;
    if (db.prepare('SELECT 1 FROM device_presets WHERE id = ?').get(id)) {
      id = `${baseId}-${crypto.randomUUID().slice(0, 8)}`;
    }

    const now = new Date().toISOString();
    db.prepare(`
      INSERT INTO device_presets (
        id, name, class, width_dp, height_dp, density, is_builtin, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
    `).run(id, name, deviceClass, widthDp, heightDp, density, now, now);

    const row = db.prepare('SELECT * FROM device_presets WHERE id = ?').get(id);
    return res.status(201).json(parseDevice(row));
  } catch {
    return res.status(500).json({ error: 'Internal server error.' });
  }
}
