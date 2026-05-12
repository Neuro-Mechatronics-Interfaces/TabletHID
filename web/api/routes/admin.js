import db from '../db.js';
import { trimAndClamp, sanitizeTags, stripControlChars } from '../sanitize.js';
import { validateGamepadConfig, validateTouchMouseConfig } from '../validate.js';
import { deleteConfigGraph, updateConfigGraph } from '../graph.js';

function parseRow(row) {
  if (!row) return row;
  return { ...row, config_json: JSON.parse(row.config_json), tags: JSON.parse(row.tags) };
}

export function adminMe(req, res) {
  return res.json({ email: req.adminEmail });
}

export function adminListConfigs(req, res) {
  try {
    const rows = db.prepare('SELECT * FROM configs ORDER BY uploaded_at DESC').all();
    return res.json({ configs: rows.map(parseRow), total: rows.length });
  } catch (err) {
    return res.status(500).json({ error: 'Internal server error.' });
  }
}

export function adminGetConfig(req, res) {
  try {
    const row = db.prepare('SELECT * FROM configs WHERE id = ?').get(req.params.id);
    if (!row) return res.status(404).json({ error: 'Not found.' });
    return res.json(parseRow(row));
  } catch (err) {
    return res.status(500).json({ error: 'Internal server error.' });
  }
}

export function adminPatchConfig(req, res) {
  try {
    const { id } = req.params;
    const row = db.prepare('SELECT id, mode FROM configs WHERE id = ?').get(id);
    if (!row) return res.status(404).json({ error: 'Not found.' });

    const body = req.body ?? {};
    const updates = {};
    const params = [];

    if ('profile_name' in body) {
      const name = stripControlChars(trimAndClamp(body.profile_name ?? '', 80));
      if (!name) return res.status(400).json({ error: 'profile_name must not be empty.' });
      updates.profile_name = name;
    }
    if ('description' in body) {
      updates.description = body.description
        ? (stripControlChars(trimAndClamp(body.description, 400)) || null)
        : null;
    }
    if ('tags' in body) {
      updates.tags = JSON.stringify(sanitizeTags(body.tags ?? []));
    }
    if ('category' in body) {
      updates.category = body.category
        ? (stripControlChars(trimAndClamp(body.category, 40)) || null)
        : null;
    }
    if ('device_name' in body) {
      updates.device_name = body.device_name
        ? (stripControlChars(trimAndClamp(body.device_name, 80)) || null)
        : null;
    }
    if ('device_screen_width_px' in body) {
      updates.device_screen_width_px = Number.isInteger(body.device_screen_width_px) && body.device_screen_width_px > 0
        ? body.device_screen_width_px
        : null;
    }
    if ('device_screen_height_px' in body) {
      updates.device_screen_height_px = Number.isInteger(body.device_screen_height_px) && body.device_screen_height_px > 0
        ? body.device_screen_height_px
        : null;
    }
    if ('device_screen_density_dpi' in body) {
      updates.device_screen_density_dpi = Number.isInteger(body.device_screen_density_dpi) && body.device_screen_density_dpi > 0
        ? body.device_screen_density_dpi
        : null;
    }
    if (
      'device_screen_width_px' in body ||
      'device_screen_height_px' in body ||
      'device_screen_density_dpi' in body
    ) {
      const w = updates.device_screen_width_px;
      const h = updates.device_screen_height_px;
      const dpi = updates.device_screen_density_dpi;
      updates.device_screen_diagonal_in = (
        Number.isInteger(w) && w > 0 &&
        Number.isInteger(h) && h > 0 &&
        Number.isInteger(dpi) && dpi > 0
      )
        ? Math.round(Math.sqrt((w / dpi) ** 2 + (h / dpi) ** 2) * 100) / 100
        : null;
    }

    if ('config_json' in body) {
      const validator = row.mode === 'gamepad' ? validateGamepadConfig : validateTouchMouseConfig;
      const err = validator(body.config_json);
      if (err) return res.status(400).json({ error: `config_json invalid: ${err}` });
      updates.config_json = JSON.stringify(body.config_json);
    }

    if (Object.keys(updates).length === 0) {
      return res.status(400).json({ error: 'No updatable fields provided.' });
    }

    const setClauses = Object.keys(updates).map(k => `${k} = ?`).join(', ');
    Object.values(updates).forEach(v => params.push(v));
    params.push(id);

    db.prepare(`UPDATE configs SET ${setClauses} WHERE id = ?`).run(...params);
    updateConfigGraph(db, id);

    const updated = db.prepare('SELECT * FROM configs WHERE id = ?').get(id);
    return res.json(parseRow(updated));
  } catch (err) {
    return res.status(500).json({ error: 'Internal server error.' });
  }
}

export function adminDeleteConfig(req, res) {
  try {
    const { id } = req.params;
    const row = db.prepare('SELECT id FROM configs WHERE id = ?').get(id);
    if (!row) return res.status(404).json({ error: 'Not found.' });
    deleteConfigGraph(db, id);
    db.prepare('DELETE FROM configs WHERE id = ?').run(id);
    return res.status(204).send();
  } catch (err) {
    return res.status(500).json({ error: 'Internal server error.' });
  }
}
