import db from '../db.js';
import { trimAndClamp, sanitizeTags, stripControlChars } from '../sanitize.js';

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
    const row = db.prepare('SELECT id FROM configs WHERE id = ?').get(id);
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

    if (Object.keys(updates).length === 0) {
      return res.status(400).json({ error: 'No updatable fields provided.' });
    }

    const setClauses = Object.keys(updates).map(k => `${k} = ?`).join(', ');
    Object.values(updates).forEach(v => params.push(v));
    params.push(id);

    db.prepare(`UPDATE configs SET ${setClauses} WHERE id = ?`).run(...params);

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
    db.prepare('DELETE FROM configs WHERE id = ?').run(id);
    return res.status(204).send();
  } catch (err) {
    return res.status(500).json({ error: 'Internal server error.' });
  }
}
