const DEFAULT_THRESHOLD = 0.08;
const DEFAULT_MAX_EDGES_PER_CONFIG = 32;

const STOPWORDS = new Set([
  'a', 'an', 'and', 'are', 'as', 'at', 'be', 'by', 'config', 'controller', 'for',
  'from', 'in', 'into', 'is', 'layout', 'mode', 'of', 'on', 'or', 'the', 'to',
  'with', 'sheet', 'quadstick', 'derived',
]);

export function ensureGraphSchema(db) {
  db.exec(`
    CREATE TABLE IF NOT EXISTS config_graph_edges (
      source_config_id TEXT NOT NULL REFERENCES configs(id) ON DELETE CASCADE,
      target_config_id TEXT NOT NULL REFERENCES configs(id) ON DELETE CASCADE,
      strength         REAL NOT NULL,
      shared_tokens    TEXT NOT NULL DEFAULT '[]',
      updated_at       TEXT NOT NULL,
      PRIMARY KEY (source_config_id, target_config_id),
      CHECK (source_config_id <> target_config_id),
      CHECK (strength >= 0 AND strength <= 1)
    );

    CREATE INDEX IF NOT EXISTS idx_config_graph_source_strength
      ON config_graph_edges (source_config_id, strength DESC);
    CREATE INDEX IF NOT EXISTS idx_config_graph_target
      ON config_graph_edges (target_config_id);
  `);
}

function parseJson(value, fallback) {
  try {
    return value ? JSON.parse(value) : fallback;
  } catch {
    return fallback;
  }
}

function addWeightedText(weights, text, weight) {
  if (!text) return;
  const normalized = String(text)
    .toLowerCase()
    .replace(/[_']/g, ' ')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
  if (!normalized) return;

  for (const token of normalized.split(/\s+/)) {
    if (token.length < 2 || STOPWORDS.has(token)) continue;
    weights.set(token, (weights.get(token) ?? 0) + weight);
  }
}

function collectConfigJsonText(configJson) {
  const parts = [];

  function walk(value, key = '') {
    if (value === null || value === undefined) return;
    if (typeof value === 'string') {
      if (key === 'label' || key.includes('Label')) parts.push(value);
      return;
    }
    if (Array.isArray(value)) {
      for (const item of value) walk(item, key);
      return;
    }
    if (typeof value === 'object') {
      for (const [childKey, child] of Object.entries(value)) walk(child, childKey);
    }
  }

  walk(configJson);
  return parts.join(' ');
}

export function vectorForConfig(row) {
  const weights = new Map();
  const tags = parseJson(row.tags, []);
  const configJson = parseJson(row.config_json, {});

  addWeightedText(weights, row.profile_name, 4);
  addWeightedText(weights, row.description, 1.5);
  addWeightedText(weights, tags.join(' '), 3);
  addWeightedText(weights, row.category, 2.5);
  addWeightedText(weights, row.mode, 1.5);
  addWeightedText(weights, row.platform, 1);
  addWeightedText(weights, row.device_name, 1);
  addWeightedText(weights, collectConfigJsonText(configJson), 1.25);

  let magnitude = 0;
  for (const value of weights.values()) magnitude += value * value;
  return { id: row.id, weights, magnitude: Math.sqrt(magnitude) };
}

function similarity(a, b) {
  if (a.magnitude === 0 || b.magnitude === 0) {
    return { strength: 0, sharedTokens: [] };
  }

  let dot = 0;
  const shared = [];
  const [small, large] = a.weights.size <= b.weights.size
    ? [a.weights, b.weights]
    : [b.weights, a.weights];

  for (const [token, leftWeight] of small.entries()) {
    const rightWeight = large.get(token);
    if (rightWeight === undefined) continue;
    dot += leftWeight * rightWeight;
    shared.push([token, leftWeight + rightWeight]);
  }

  shared.sort((x, y) => y[1] - x[1]);
  return {
    strength: Math.round((dot / (a.magnitude * b.magnitude)) * 10000) / 10000,
    sharedTokens: shared.slice(0, 8).map(([token]) => token),
  };
}

function allConfigRows(db) {
  return db.prepare('SELECT * FROM configs ORDER BY uploaded_at DESC').all();
}

function insertEdges(db, edges) {
  const now = new Date().toISOString();
  const insert = db.prepare(`
    INSERT OR REPLACE INTO config_graph_edges (
      source_config_id, target_config_id, strength, shared_tokens, updated_at
    ) VALUES (?, ?, ?, ?, ?)
  `);

  for (const edge of edges) {
    insert.run(edge.source, edge.target, edge.strength, JSON.stringify(edge.sharedTokens), now);
  }
}

function pruneEdgesForSource(edges, maxEdgesPerConfig) {
  const bySource = new Map();
  for (const edge of edges) {
    const list = bySource.get(edge.source) ?? [];
    list.push(edge);
    bySource.set(edge.source, list);
  }

  const pruned = [];
  for (const list of bySource.values()) {
    list.sort((a, b) => b.strength - a.strength);
    pruned.push(...list.slice(0, maxEdgesPerConfig));
  }
  return pruned;
}

export function rebuildGraph(db, options = {}) {
  ensureGraphSchema(db);
  const threshold = options.threshold ?? DEFAULT_THRESHOLD;
  const maxEdgesPerConfig = options.maxEdgesPerConfig ?? DEFAULT_MAX_EDGES_PER_CONFIG;
  const vectors = allConfigRows(db).map(vectorForConfig);
  const undirectedEdges = [];

  for (let i = 0; i < vectors.length; i++) {
    for (let j = i + 1; j < vectors.length; j++) {
      const result = similarity(vectors[i], vectors[j]);
      if (result.strength < threshold) continue;
      undirectedEdges.push({
        a: vectors[i].id,
        b: vectors[j].id,
        strength: result.strength,
        sharedTokens: result.sharedTokens,
      });
    }
  }

  const directedEdges = [];
  for (const edge of undirectedEdges) {
    directedEdges.push({ source: edge.a, target: edge.b, strength: edge.strength, sharedTokens: edge.sharedTokens });
    directedEdges.push({ source: edge.b, target: edge.a, strength: edge.strength, sharedTokens: edge.sharedTokens });
  }

  const pruned = pruneEdgesForSource(directedEdges, maxEdgesPerConfig);
  db.transaction(() => {
    db.prepare('DELETE FROM config_graph_edges').run();
    insertEdges(db, pruned);
  })();

  return { configs: vectors.length, edges: pruned.length };
}

export function updateConfigGraph(db, configId, options = {}) {
  ensureGraphSchema(db);
  const threshold = options.threshold ?? DEFAULT_THRESHOLD;
  const maxEdgesPerConfig = options.maxEdgesPerConfig ?? DEFAULT_MAX_EDGES_PER_CONFIG;
  const rows = allConfigRows(db);
  const sourceRow = rows.find(row => row.id === configId);
  if (!sourceRow) return { configs: rows.length, edges: 0 };

  const source = vectorForConfig(sourceRow);
  const candidates = rows.filter(row => row.id !== configId).map(vectorForConfig);
  const edges = [];

  for (const candidate of candidates) {
    const result = similarity(source, candidate);
    if (result.strength < threshold) continue;
    edges.push({ source: source.id, target: candidate.id, strength: result.strength, sharedTokens: result.sharedTokens });
    edges.push({ source: candidate.id, target: source.id, strength: result.strength, sharedTokens: result.sharedTokens });
  }

  const pruned = pruneEdgesForSource(edges, maxEdgesPerConfig);
  db.transaction(() => {
    db.prepare('DELETE FROM config_graph_edges WHERE source_config_id = ? OR target_config_id = ?').run(configId, configId);
    insertEdges(db, pruned);
  })();

  return { configs: rows.length, edges: pruned.length };
}

export function deleteConfigGraph(db, configId) {
  ensureGraphSchema(db);
  db.prepare('DELETE FROM config_graph_edges WHERE source_config_id = ? OR target_config_id = ?').run(configId, configId);
}

function parseRow(row) {
  if (!row) return row;
  return { ...row, config_json: parseJson(row.config_json, {}), tags: parseJson(row.tags, []) };
}

export function getConfigGraph(db, configId, limit = 24) {
  ensureGraphSchema(db);
  const center = db.prepare('SELECT * FROM configs WHERE id = ?').get(configId);
  if (!center) return null;

  const edges = db.prepare(`
    SELECT e.*, c.*
    FROM config_graph_edges e
    JOIN configs c ON c.id = e.target_config_id
    WHERE e.source_config_id = ?
    ORDER BY e.strength DESC
    LIMIT ?
  `).all(configId, limit);

  const nodeIds = [configId, ...edges.map(edge => edge.target_config_id)];
  const placeholders = nodeIds.map(() => '?').join(',');
  const neighborEdges = nodeIds.length > 1
    ? db.prepare(`
        SELECT source_config_id, target_config_id, strength, shared_tokens
        FROM config_graph_edges
        WHERE source_config_id IN (${placeholders})
          AND target_config_id IN (${placeholders})
      `).all(...nodeIds, ...nodeIds)
    : [];

  return {
    center: parseRow(center),
    nodes: [
      { config: parseRow(center), strength: 1, shared_tokens: [] },
      ...edges.map(edge => ({
        config: parseRow(edge),
        strength: edge.strength,
        shared_tokens: parseJson(edge.shared_tokens, []),
      })),
    ],
    edges: neighborEdges.map(edge => ({
      source: edge.source_config_id,
      target: edge.target_config_id,
      strength: edge.strength,
      shared_tokens: parseJson(edge.shared_tokens, []),
    })),
  };
}
