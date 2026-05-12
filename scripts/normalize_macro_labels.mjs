#!/usr/bin/env node
import path from 'path';
import { fileURLToPath } from 'url';
import Database from '../web/node_modules/better-sqlite3/lib/index.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_DB = path.join(__dirname, '..', 'web', 'data', 'tablethid.db');
const MACRO_TEXT_FIELDS = ['label', 'displayText', 'displayName', 'name'];

function parseArgs(argv) {
  const args = {
    db: DEFAULT_DB,
    dryRun: false,
  };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--db') args.db = argv[++i];
    else if (arg === '--dry-run') args.dryRun = true;
    else if (arg === '--help' || arg === '-h') {
      console.log(`Usage: node scripts/normalize_macro_labels.mjs [options]

Options:
  --db PATH     SQLite DB path (default: web/data/tablethid.db)
  --dry-run     Print what would change without writing
`);
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  return args;
}

function stripAfterColon(value) {
  if (typeof value !== 'string') return value;
  const colon = value.indexOf(':');
  if (colon < 0) return value;
  return value.slice(0, colon).trim();
}

function normalizeConfig(config) {
  if (!Array.isArray(config?.macroButtons)) {
    return { config, changed: false, macroChanges: 0 };
  }

  let changed = false;
  let macroChanges = 0;
  const macroButtons = config.macroButtons.map(macro => {
    let nextMacro = macro;
    let macroChanged = false;
    for (const field of MACRO_TEXT_FIELDS) {
      const oldValue = nextMacro?.[field];
      const newValue = stripAfterColon(oldValue);
      if (newValue !== oldValue) {
        if (!macroChanged) nextMacro = { ...nextMacro };
        nextMacro[field] = newValue;
        macroChanged = true;
      }
    }
    if (macroChanged) {
      changed = true;
      macroChanges++;
    }
    return nextMacro;
  });

  return changed
    ? { config: { ...config, macroButtons }, changed, macroChanges }
    : { config, changed, macroChanges };
}

try {
  const args = parseArgs(process.argv.slice(2));
  const db = new Database(args.db);
  const rows = db.prepare('SELECT id, profile_name, config_json FROM configs').all();
  const updates = [];
  let changedMacros = 0;

  for (const row of rows) {
    const parsed = JSON.parse(row.config_json);
    const result = normalizeConfig(parsed);
    if (!result.changed) continue;
    updates.push({
      id: row.id,
      profile_name: row.profile_name,
      config_json: JSON.stringify(result.config),
      macroChanges: result.macroChanges,
    });
    changedMacros += result.macroChanges;
  }

  if (!args.dryRun && updates.length > 0) {
    const update = db.prepare('UPDATE configs SET config_json = ? WHERE id = ?');
    db.transaction(() => {
      for (const item of updates) update.run(item.config_json, item.id);
    })();
  }

  db.close();
  console.log(`${args.dryRun ? 'Would update' : 'Updated'} ${updates.length} config rows and ${changedMacros} macro labels.`);
  for (const item of updates.slice(0, 10)) {
    console.log(`  ${item.profile_name}: ${item.macroChanges} macro${item.macroChanges === 1 ? '' : 's'}`);
  }
  if (updates.length > 10) console.log(`  ...and ${updates.length - 10} more rows`);
} catch (error) {
  console.error(error.message);
  process.exit(1);
}
