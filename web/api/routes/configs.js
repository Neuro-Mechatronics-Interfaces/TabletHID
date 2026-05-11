import { createRequire } from 'module';
import { fileURLToPath } from 'url';
import path from 'path';
import crypto from 'crypto';
import db from '../db.js';
import {
  trimAndClamp,
  sanitizeTags,
  isValidMode,
  isValidPlatform,
  isValidIso8601,
  stripControlChars,
} from '../sanitize.js';
import { validateTouchMouseConfig, validateGamepadConfig } from '../validate.js';

const require = createRequire(import.meta.url);
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const hwMachineNames = require(path.join(__dirname, '..', 'hw-machine-names.json'));

// Parse config_json and tags from stored JSON strings in a DB row before returning it.
function parseRow(row) {
  if (!row) return row;
  return {
    ...row,
    config_json: JSON.parse(row.config_json),
    tags: JSON.parse(row.tags),
  };
}

export async function listConfigs(req, res) {
  try {
    const { mode, platform, tags: tagsParam, category, sort, limit: limitParam, offset: offsetParam, since } = req.query;

    // Validate mode
    if (mode !== undefined && !isValidMode(mode)) {
      return res.status(400).json({ error: 'Invalid mode. Must be "touch_mouse" or "gamepad".' });
    }

    // Validate platform
    if (platform !== undefined && !isValidPlatform(platform)) {
      return res.status(400).json({ error: 'Invalid platform. Must be "android" or "ios".' });
    }

    // Validate sort
    const sortValue = sort ?? 'recent';
    if (sortValue !== 'recent' && sortValue !== 'popular') {
      return res.status(400).json({ error: 'Invalid sort. Must be "recent" or "popular".' });
    }

    // Validate limit
    const limitRaw = limitParam !== undefined ? Number(limitParam) : 20;
    if (!Number.isInteger(limitRaw) || limitRaw < 1 || limitRaw > 100) {
      return res.status(400).json({ error: 'Invalid limit. Must be an integer between 1 and 100.' });
    }
    const limit = limitRaw;

    // Validate offset
    const offsetRaw = offsetParam !== undefined ? Number(offsetParam) : 0;
    if (!Number.isInteger(offsetRaw) || offsetRaw < 0) {
      return res.status(400).json({ error: 'Invalid offset. Must be a non-negative integer.' });
    }
    const offset = offsetRaw;

    // Validate since
    if (since !== undefined && since !== '' && !isValidIso8601(since)) {
      return res.status(400).json({ error: 'Invalid since. Must be an ISO 8601 timestamp.' });
    }

    // Parse tags
    const tagList = tagsParam
      ? tagsParam.split(',').map((t) => t.trim()).filter((t) => t.length > 0)
      : [];

    // Build WHERE clause
    const conditions = [];
    const params = [];

    if (mode !== undefined) {
      conditions.push('mode = ?');
      params.push(mode);
    }
    if (platform !== undefined) {
      conditions.push('platform = ?');
      params.push(platform);
    }
    for (const tag of tagList) {
      conditions.push('EXISTS (SELECT 1 FROM json_each(configs.tags) WHERE json_each.value = ?)');
      params.push(tag);
    }
    if (category !== undefined && category !== '') {
      conditions.push('category = ?');
      params.push(trimAndClamp(category, 40));
    }
    if (since !== undefined && since !== '') {
      conditions.push('uploaded_at > ?');
      params.push(since);
    }

    const whereClause = conditions.length > 0 ? 'WHERE ' + conditions.join(' AND ') : '';
    const orderClause = sortValue === 'popular' ? 'ORDER BY download_count DESC' : 'ORDER BY uploaded_at DESC';

    // COUNT query
    const countSql = `SELECT COUNT(*) AS total FROM configs ${whereClause}`;
    const countRow = db.prepare(countSql).get(...params);
    const total = countRow.total;

    // SELECT query
    const selectSql = `SELECT * FROM configs ${whereClause} ${orderClause} LIMIT ? OFFSET ?`;
    const rows = db.prepare(selectSql).all(...params, limit, offset);

    // Compute latest_at from returned rows
    let latest_at = null;
    if (rows.length > 0) {
      latest_at = rows.reduce((max, row) => (row.uploaded_at > max ? row.uploaded_at : max), rows[0].uploaded_at);
    }

    return res.json({
      configs: rows.map(parseRow),
      total,
      latest_at,
    });
  } catch (err) {
    console.log(JSON.stringify({
      level: 'error',
      event: 'list_configs_error',
      error: err?.message ?? String(err),
    }));
    return res.status(500).json({ error: 'Internal server error.' });
  }
}

export async function getConfig(req, res) {
  try {
    const { id } = req.params;

    if (!id || !/^[0-9a-f-]{36}$/i.test(id)) {
      return res.status(400).json({ error: 'Invalid config ID format.' });
    }

    const row = db.prepare('SELECT * FROM configs WHERE id = ?').get(id);
    if (!row) {
      return res.status(404).json({ error: 'Not found.' });
    }

    db.prepare('UPDATE configs SET download_count = download_count + 1 WHERE id = ?').run(id);

    return res.json(parseRow(row));
  } catch (err) {
    console.log(JSON.stringify({
      level: 'error',
      event: 'get_config_error',
      error: err?.message ?? String(err),
    }));
    return res.status(500).json({ error: 'Internal server error.' });
  }
}

export async function uploadConfig(req, res) {
  try {
    const body = req.body ?? {};

    // 1. Validate required fields are present
    if (body.platform === undefined || body.platform === null) {
      return res.status(400).json({ error: 'Missing required field: platform.' });
    }
    if (body.mode === undefined || body.mode === null) {
      return res.status(400).json({ error: 'Missing required field: mode.' });
    }
    if (body.profile_name === undefined || body.profile_name === null) {
      return res.status(400).json({ error: 'Missing required field: profile_name.' });
    }
    if (body.config_json === undefined || body.config_json === null) {
      return res.status(400).json({ error: 'Missing required field: config_json.' });
    }

    // 2. Validate mode and platform
    if (!isValidMode(body.mode)) {
      return res.status(400).json({ error: 'Invalid mode. Must be "touch_mouse" or "gamepad".' });
    }
    if (!isValidPlatform(body.platform)) {
      return res.status(400).json({ error: 'Invalid platform. Must be "android" or "ios".' });
    }

    // 3. Sanitize string fields
    const profile_name = stripControlChars(trimAndClamp(body.profile_name, 80));
    if (profile_name.length === 0) {
      return res.status(400).json({ error: 'profile_name must not be empty after sanitisation.' });
    }

    const description = body.description !== undefined && body.description !== null
      ? (stripControlChars(trimAndClamp(body.description, 400)) || null)
      : null;

    const tags = sanitizeTags(body.tags ?? []);

    const category = body.category !== undefined && body.category !== null
      ? (stripControlChars(trimAndClamp(body.category, 40)) || null)
      : null;

    const app_version = body.app_version !== undefined && body.app_version !== null
      ? trimAndClamp(body.app_version, 20) || null
      : null;

    let device_name = body.device_name !== undefined && body.device_name !== null
      ? trimAndClamp(body.device_name, 80) || null
      : null;

    const device_hw_id = body.device_hw_id !== undefined && body.device_hw_id !== null
      ? trimAndClamp(body.device_hw_id, 40) || null
      : null;

    const device_os_version = body.device_os_version !== undefined && body.device_os_version !== null
      ? trimAndClamp(body.device_os_version, 20) || null
      : null;

    // 4. Validate config_json is a plain object
    const config_json = body.config_json;
    if (config_json === null || typeof config_json !== 'object' || Array.isArray(config_json)) {
      return res.status(400).json({ error: 'config_json must be a plain object (not an array or null).' });
    }
    const configJsonString = JSON.stringify(config_json);
    if (configJsonString.length > 32768) {
      return res.status(400).json({ error: 'config_json is too large (max 32768 characters when serialised).' });
    }

    // 5. Run the appropriate validator
    const validationError = body.mode === 'touch_mouse'
      ? validateTouchMouseConfig(config_json)
      : validateGamepadConfig(config_json);
    if (validationError !== null) {
      return res.status(400).json({ error: `config_json validation failed: ${validationError}` });
    }

    // 6. Look up hw_id for friendly device name (iOS)
    if (device_hw_id !== null && device_hw_id in hwMachineNames) {
      device_name = hwMachineNames[device_hw_id];
    }

    // 7. Compute device_screen_diagonal_in server-side
    let device_screen_diagonal_in = null;
    const w = body.device_screen_width_px;
    const h = body.device_screen_height_px;
    const dpi = body.device_screen_density_dpi;
    if (
      Number.isInteger(w) && w > 0 &&
      Number.isInteger(h) && h > 0 &&
      Number.isInteger(dpi) && dpi > 0
    ) {
      device_screen_diagonal_in = Math.round(Math.sqrt((w / dpi) ** 2 + (h / dpi) ** 2) * 100) / 100;
    }

    // Validate optional integer fields
    const device_os_api_level = body.device_os_api_level !== undefined && body.device_os_api_level !== null
      ? body.device_os_api_level
      : null;
    const device_screen_width_px = Number.isInteger(w) && w > 0 ? w : null;
    const device_screen_height_px = Number.isInteger(h) && h > 0 ? h : null;
    const device_screen_density_dpi = Number.isInteger(dpi) && dpi > 0 ? dpi : null;

    // 8. Generate UUID
    const id = crypto.randomUUID();

    // 9. Set uploaded_at
    const uploaded_at = new Date().toISOString();

    // 10. schema_version = 1
    const schema_version = 1;

    // 11. INSERT
    db.prepare(`
      INSERT INTO configs (
        id, schema_version, platform, mode, profile_name, description, tags, category,
        app_version, config_json, uploaded_at, download_count,
        device_name, device_hw_id, device_os_version, device_os_api_level,
        device_screen_width_px, device_screen_height_px, device_screen_density_dpi,
        device_screen_diagonal_in
      ) VALUES (
        ?, ?, ?, ?, ?, ?, ?, ?,
        ?, ?, ?, 0,
        ?, ?, ?, ?,
        ?, ?, ?,
        ?
      )
    `).run(
      id, schema_version, body.platform, body.mode, profile_name, description, JSON.stringify(tags), category,
      app_version, configJsonString, uploaded_at,
      device_name, device_hw_id, device_os_version, device_os_api_level,
      device_screen_width_px, device_screen_height_px, device_screen_density_dpi,
      device_screen_diagonal_in,
    );

    // 12. Return 201
    return res.status(201).json({ id });
  } catch (err) {
    console.log(JSON.stringify({
      level: 'error',
      event: 'upload_config_error',
      error: err?.message ?? String(err),
    }));
    return res.status(500).json({ error: 'Internal server error.' });
  }
}
