import Database from 'better-sqlite3';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { ensureGraphSchema } from './graph.js';
import { DEFAULT_DEVICES } from './defaultDevices.js';

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

const hasMigration3 = db.prepare('SELECT 1 FROM schema_migrations WHERE version = 3').get();
if (!hasMigration3) {
  ensureGraphSchema(db);
  db.prepare(`
    INSERT INTO schema_migrations (version, applied_at, description)
    VALUES (3, ?, 'Added sparse community config graph edge table')
  `).run(new Date().toISOString());
} else {
  ensureGraphSchema(db);
}

const hasMigration5 = db.prepare('SELECT 1 FROM schema_migrations WHERE version = 5').get();
if (!hasMigration5) {
  ensureGraphSchema(db);
  db.prepare(`
    INSERT INTO schema_migrations (version, applied_at, description)
    VALUES (5, ?, 'Added dissimilarity score to config graph edges')
  `).run(new Date().toISOString());
}

db.exec(`
CREATE TABLE IF NOT EXISTS device_presets (
  id         TEXT PRIMARY KEY,
  name       TEXT NOT NULL,
  class      TEXT NOT NULL,
  width_dp   INTEGER NOT NULL,
  height_dp  INTEGER NOT NULL,
  density    REAL NOT NULL,
  is_builtin INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,

  CHECK (class IN ('phone', 'tablet')),
  CHECK (width_dp > 0),
  CHECK (height_dp > 0),
  CHECK (density > 0)
);

CREATE INDEX IF NOT EXISTS idx_device_presets_name ON device_presets (name);
`);

const hasMigration4 = db.prepare('SELECT 1 FROM schema_migrations WHERE version = 4').get();
const seedDefaultDevices = () => {
  const now = new Date().toISOString();
  const insert = db.prepare(`
    INSERT OR IGNORE INTO device_presets (
      id, name, class, width_dp, height_dp, density, is_builtin, created_at, updated_at
    ) VALUES (
      @id, @name, @class, @width_dp, @height_dp, @density, 1, @now, @now
    )
  `);
  for (const device of DEFAULT_DEVICES) insert.run({ ...device, now });
};

if (!hasMigration4) {
  const now = new Date().toISOString();
  seedDefaultDevices();
  db.prepare(`
    INSERT INTO schema_migrations (version, applied_at, description)
    VALUES (4, ?, 'Added device presets table with seeded defaults')
  `).run(now);
} else {
  seedDefaultDevices();
}

export default db;
