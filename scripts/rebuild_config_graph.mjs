#!/usr/bin/env node
import path from 'path';
import { fileURLToPath } from 'url';
import Database from '../web/node_modules/better-sqlite3/lib/index.js';
import { rebuildGraph } from '../web/api/graph.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_DB = path.join(__dirname, '..', 'web', 'data', 'tablethid.db');

function parseArgs(argv) {
  const args = {
    db: DEFAULT_DB,
    threshold: 0.08,
    maxEdges: 32,
  };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--db') args.db = argv[++i];
    else if (arg === '--threshold') args.threshold = Number.parseFloat(argv[++i]);
    else if (arg === '--max-edges') args.maxEdges = Number.parseInt(argv[++i], 10);
    else if (arg === '--help' || arg === '-h') {
      console.log(`Usage: node scripts/rebuild_config_graph.mjs [options]

Options:
  --db PATH         SQLite DB path (default: web/data/tablethid.db)
  --threshold N     Minimum cosine similarity edge strength (default: 0.08)
  --max-edges N     Max outgoing edges kept per config (default: 32)
`);
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  if (!Number.isFinite(args.threshold) || args.threshold < 0 || args.threshold > 1) {
    throw new Error('--threshold must be a number between 0 and 1');
  }
  if (!Number.isInteger(args.maxEdges) || args.maxEdges < 1) {
    throw new Error('--max-edges must be a positive integer');
  }
  return args;
}

try {
  const args = parseArgs(process.argv.slice(2));
  const db = new Database(args.db);
  const result = rebuildGraph(db, {
    threshold: args.threshold,
    maxEdgesPerConfig: args.maxEdges,
  });
  db.close();
  console.log(`Rebuilt config graph: ${result.configs} configs, ${result.edges} directed edges, ${result.clusters} clusters`);
  console.log(`DB: ${args.db}`);
} catch (error) {
  console.error(error.message);
  process.exit(1);
}
