import Database from 'better-sqlite3';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const dataDir = path.join(__dirname, '..', 'data');

fs.mkdirSync(dataDir, { recursive: true });

const db = new Database(path.join(dataDir, 'tablethid.db'));

db.exec(`
CREATE TABLE IF NOT EXISTS schema_migrations (
  version     INTEGER PRIMARY KEY,
  applied_at  TEXT    NOT NULL,
  description TEXT
);

CREATE TABLE IF NOT EXISTS configs (
  id                        TEXT    PRIMARY KEY,
  schema_version            INTEGER NOT NULL DEFAULT 1,
  platform                  TEXT    NOT NULL,
  mode                      TEXT    NOT NULL,
  profile_name              TEXT    NOT NULL,
  description               TEXT,
  tags                      TEXT    NOT NULL DEFAULT '[]',
  app_version               TEXT,
  config_json               TEXT    NOT NULL,
  uploaded_at               TEXT    NOT NULL,
  download_count            INTEGER NOT NULL DEFAULT 0,
  device_name               TEXT,
  device_hw_id              TEXT,
  device_os_version         TEXT,
  device_os_api_level       INTEGER,
  device_screen_width_px    INTEGER,
  device_screen_height_px   INTEGER,
  device_screen_density_dpi INTEGER,
  device_screen_diagonal_in REAL,

  CHECK (platform IN ('android', 'ios')),
  CHECK (mode     IN ('touch_mouse', 'gamepad'))
);

CREATE INDEX IF NOT EXISTS idx_configs_mode           ON configs (mode);
CREATE INDEX IF NOT EXISTS idx_configs_platform       ON configs (platform);
CREATE INDEX IF NOT EXISTS idx_configs_schema_version ON configs (schema_version);
CREATE INDEX IF NOT EXISTS idx_configs_popular        ON configs (download_count DESC);
CREATE INDEX IF NOT EXISTS idx_configs_recent         ON configs (uploaded_at DESC);
CREATE INDEX IF NOT EXISTS idx_configs_diagonal       ON configs (device_screen_diagonal_in);
`);

db.prepare(`
  INSERT OR IGNORE INTO schema_migrations (version, applied_at, description)
  VALUES (1, ?, 'Initial schema: touch_mouse and gamepad configs')
`).run(new Date().toISOString());

// Migration 2: add category column (additive — ALTER TABLE is idempotent via try/catch)
const hasMigration2 = db.prepare('SELECT 1 FROM schema_migrations WHERE version = 2').get();
if (!hasMigration2) {
  db.exec('ALTER TABLE configs ADD COLUMN category TEXT');
  db.exec('CREATE INDEX IF NOT EXISTS idx_configs_category ON configs (category)');
  db.prepare(`
    INSERT INTO schema_migrations (version, applied_at, description)
    VALUES (2, ?, 'Added category column and index')
  `).run(new Date().toISOString());
}

export default db;
