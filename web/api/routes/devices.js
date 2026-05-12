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

function normalizeName(value) {
  return String(value ?? '').toLowerCase().replace(/[^a-z0-9]+/g, '');
}

function slug(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 48);
}

export function listDevices(_req, res) {
  try {
    const presetRows = db.prepare(`
      SELECT * FROM device_presets
      ORDER BY is_builtin DESC, name COLLATE NOCASE
    `).all();

    const devices = presetRows.map(parseDevice);
    const seenNames = new Set(devices.map(device => normalizeName(device.name)));

    const configRows = db.prepare(`
      SELECT
        device_name,
        device_screen_width_px,
        device_screen_height_px,
        device_screen_density_dpi,
        device_screen_diagonal_in,
        uploaded_at
      FROM configs
      WHERE device_name IS NOT NULL
        AND TRIM(device_name) <> ''
        AND device_screen_width_px IS NOT NULL
        AND device_screen_height_px IS NOT NULL
        AND device_screen_density_dpi IS NOT NULL
      ORDER BY uploaded_at DESC
    `).all();

    for (const row of configRows) {
      const normalized = normalizeName(row.device_name);
      if (!normalized || seenNames.has(normalized)) continue;

      const density = row.device_screen_density_dpi / 160;
      if (!Number.isFinite(density) || density <= 0) continue;

      const widthDpObserved = Math.round(row.device_screen_width_px / density);
      const heightDpObserved = Math.round(row.device_screen_height_px / density);
      const shortDp = Math.min(widthDpObserved, heightDpObserved);
      const longDp = Math.max(widthDpObserved, heightDpObserved);
      const deviceClass = row.device_screen_diagonal_in !== null && row.device_screen_diagonal_in < 7 ? 'phone' : 'tablet';

      devices.push({
        id: `config-${slug(row.device_name)}`,
        name: row.device_name,
        class: deviceClass,
        widthDp: shortDp,
        heightDp: longDp,
        density,
        isBuiltin: false,
        source: 'configs',
      });
      seenNames.add(normalized);
    }

    devices.sort((a, b) => {
      if (a.isBuiltin !== b.isBuiltin) return a.isBuiltin ? -1 : 1;
      return a.name.localeCompare(b.name);
    });

    return res.json({ devices });
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
