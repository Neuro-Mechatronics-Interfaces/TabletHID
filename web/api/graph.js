import crypto from 'crypto';

const DEFAULT_THRESHOLD = 0.08;
const DEFAULT_MAX_EDGES_PER_CONFIG = 32;
const DEFAULT_CLUSTER_THRESHOLD = 0.86;
const DEFAULT_CLUSTER_MIN_SIZE = 3;
const CLUSTER_ALGORITHM = 'connected-components-v1';

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
      dissimilarity    REAL NOT NULL DEFAULT 1,
      cluster_id       TEXT REFERENCES config_graph_clusters(id) ON DELETE SET NULL,
      shared_tokens    TEXT NOT NULL DEFAULT '[]',
      updated_at       TEXT NOT NULL,
      PRIMARY KEY (source_config_id, target_config_id),
      CHECK (source_config_id <> target_config_id),
      CHECK (strength >= 0 AND strength <= 1),
      CHECK (dissimilarity >= 0 AND dissimilarity <= 1)
    );

    CREATE INDEX IF NOT EXISTS idx_config_graph_source_strength
      ON config_graph_edges (source_config_id, strength DESC);
    CREATE INDEX IF NOT EXISTS idx_config_graph_target
      ON config_graph_edges (target_config_id);

    CREATE TABLE IF NOT EXISTS config_graph_clusters (
      id           TEXT PRIMARY KEY,
      signature    TEXT NOT NULL UNIQUE,
      name         TEXT,
      description  TEXT,
      algorithm    TEXT NOT NULL,
      threshold    REAL NOT NULL,
      member_count INTEGER NOT NULL,
      created_at   TEXT NOT NULL,
      updated_at   TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS config_graph_cluster_members (
      cluster_id TEXT NOT NULL REFERENCES config_graph_clusters(id) ON DELETE CASCADE,
      config_id  TEXT NOT NULL REFERENCES configs(id) ON DELETE CASCADE,
      strength   REAL NOT NULL DEFAULT 1,
      PRIMARY KEY (cluster_id, config_id)
    );

    CREATE INDEX IF NOT EXISTS idx_config_graph_cluster_members_config
      ON config_graph_cluster_members (config_id);
  `);

  const columns = db.prepare('PRAGMA table_info(config_graph_edges)').all().map(column => column.name);
  if (!columns.includes('dissimilarity')) {
    db.exec('ALTER TABLE config_graph_edges ADD COLUMN dissimilarity REAL NOT NULL DEFAULT 1');
  }
  if (!columns.includes('cluster_id')) {
    db.exec('ALTER TABLE config_graph_edges ADD COLUMN cluster_id TEXT REFERENCES config_graph_clusters(id) ON DELETE SET NULL');
  }
  db.exec('CREATE INDEX IF NOT EXISTS idx_config_graph_cluster ON config_graph_edges (cluster_id)');
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
    return { strength: 0, dissimilarity: 1, sharedTokens: [] };
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
  const strength = Math.round((dot / (a.magnitude * b.magnitude)) * 10000) / 10000;
  return {
    strength,
    dissimilarity: Math.round((1 - strength) * 10000) / 10000,
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
      source_config_id, target_config_id, strength, dissimilarity, shared_tokens, updated_at
    ) VALUES (?, ?, ?, ?, ?, ?)
  `);

  for (const edge of edges) {
    insert.run(edge.source, edge.target, edge.strength, edge.dissimilarity, JSON.stringify(edge.sharedTokens), now);
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

function stableClusterId(signature) {
  const bytes = crypto.createHash('sha1').update(`tablethid:graph-cluster:${signature}`).digest().subarray(0, 16);
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function averageMemberStrength(db, member, members) {
  const others = JSON.stringify(members.filter(id => id !== member));
  return db.prepare(`
    SELECT AVG(strength) AS strength
    FROM config_graph_edges
    WHERE source_config_id = ? AND target_config_id IN (
      SELECT value FROM json_each(?)
    )
  `).get(member, others)?.strength ?? 1;
}

function reconcileGraphClusters(db, options = {}) {
  ensureGraphSchema(db);
  const threshold = options.clusterThreshold ?? DEFAULT_CLUSTER_THRESHOLD;
  const minSize = options.clusterMinSize ?? DEFAULT_CLUSTER_MIN_SIZE;
  const edges = db.prepare(`
    SELECT source_config_id AS source, target_config_id AS target, strength
    FROM config_graph_edges
    WHERE strength >= ?
  `).all(threshold);

  const adjacency = new Map();
  for (const edge of edges) {
    adjacency.set(edge.source, [...(adjacency.get(edge.source) ?? []), edge.target]);
    adjacency.set(edge.target, [...(adjacency.get(edge.target) ?? []), edge.source]);
  }

  const visited = new Set();
  const clusters = [];
  for (const id of adjacency.keys()) {
    if (visited.has(id)) continue;
    const stack = [id];
    const members = [];
    visited.add(id);
    while (stack.length) {
      const current = stack.pop();
      members.push(current);
      for (const next of adjacency.get(current) ?? []) {
        if (visited.has(next)) continue;
        visited.add(next);
        stack.push(next);
      }
    }
    if (members.length >= minSize) {
      const sortedMembers = members.sort();
      const signature = `${CLUSTER_ALGORITHM}:${threshold}:${sortedMembers.join('|')}`;
      clusters.push({
        id: stableClusterId(signature),
        signature,
        members: sortedMembers,
      });
    }
  }

  const now = new Date().toISOString();
  const upsertCluster = db.prepare(`
    INSERT INTO config_graph_clusters (
      id, signature, name, description, algorithm, threshold, member_count, created_at, updated_at
    ) VALUES (
      @id, @signature, NULL, NULL, @algorithm, @threshold, @member_count, @now, @now
    )
    ON CONFLICT(signature) DO UPDATE SET
      member_count = excluded.member_count,
      threshold = excluded.threshold,
      algorithm = excluded.algorithm,
      updated_at = excluded.updated_at
  `);
  const insertMember = db.prepare(`
    INSERT OR REPLACE INTO config_graph_cluster_members (cluster_id, config_id, strength)
    VALUES (?, ?, ?)
  `);
  const avgStrength = db.prepare(`
    SELECT AVG(strength) AS strength
    FROM config_graph_edges
    WHERE source_config_id = ? AND target_config_id IN (
      SELECT value FROM json_each(?)
    )
  `);

  db.prepare('UPDATE config_graph_edges SET cluster_id = NULL').run();
  db.prepare(`
    DELETE FROM config_graph_cluster_members
    WHERE cluster_id IN (
      SELECT id FROM config_graph_clusters WHERE algorithm = ?
    )
  `).run(CLUSTER_ALGORITHM);

  for (const cluster of clusters) {
    upsertCluster.run({
      id: cluster.id,
      signature: cluster.signature,
      algorithm: CLUSTER_ALGORITHM,
      threshold,
      member_count: cluster.members.length,
      now,
    });
    for (const member of cluster.members) {
      const others = JSON.stringify(cluster.members.filter(id => id !== member));
      insertMember.run(cluster.id, member, avgStrength.get(member, others)?.strength ?? 1);
    }
    const placeholders = cluster.members.map(() => '?').join(',');
    db.prepare(`
      UPDATE config_graph_edges
      SET cluster_id = ?
      WHERE source_config_id IN (${placeholders})
        AND target_config_id IN (${placeholders})
    `).run(cluster.id, ...cluster.members, ...cluster.members);
  }

  return { clusters: clusters.length };
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
        dissimilarity: result.dissimilarity,
        sharedTokens: result.sharedTokens,
      });
    }
  }

  const directedEdges = [];
  for (const edge of undirectedEdges) {
    directedEdges.push({ source: edge.a, target: edge.b, strength: edge.strength, dissimilarity: edge.dissimilarity, sharedTokens: edge.sharedTokens });
    directedEdges.push({ source: edge.b, target: edge.a, strength: edge.strength, dissimilarity: edge.dissimilarity, sharedTokens: edge.sharedTokens });
  }

  const pruned = pruneEdgesForSource(directedEdges, maxEdgesPerConfig);
  let clusterResult = { clusters: 0 };
  db.transaction(() => {
    db.prepare('DELETE FROM config_graph_edges').run();
    insertEdges(db, pruned);
    clusterResult = reconcileGraphClusters(db, options);
  })();

  return { configs: vectors.length, edges: pruned.length, clusters: clusterResult.clusters };
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
    edges.push({ source: source.id, target: candidate.id, strength: result.strength, dissimilarity: result.dissimilarity, sharedTokens: result.sharedTokens });
    edges.push({ source: candidate.id, target: source.id, strength: result.strength, dissimilarity: result.dissimilarity, sharedTokens: result.sharedTokens });
  }

  const pruned = pruneEdgesForSource(edges, maxEdgesPerConfig);
  let clusterResult = { clusters: 0 };
  db.transaction(() => {
    db.prepare('DELETE FROM config_graph_edges WHERE source_config_id = ? OR target_config_id = ?').run(configId, configId);
    insertEdges(db, pruned);
    clusterResult = reconcileGraphClusters(db, options);
  })();

  return { configs: rows.length, edges: pruned.length, clusters: clusterResult.clusters };
}

export function deleteConfigGraph(db, configId) {
  ensureGraphSchema(db);
  db.transaction(() => {
    db.prepare('DELETE FROM config_graph_edges WHERE source_config_id = ? OR target_config_id = ?').run(configId, configId);
    reconcileGraphClusters(db);
  })();
}

export function patchGraphCluster(db, clusterId, patch = {}) {
  ensureGraphSchema(db);
  const row = db.prepare('SELECT * FROM config_graph_clusters WHERE id = ?').get(clusterId);
  if (!row) return null;
  const name = 'name' in patch ? String(patch.name ?? '').trim().slice(0, 80) || null : row.name;
  const description = 'description' in patch ? String(patch.description ?? '').trim().slice(0, 400) || null : row.description;
  db.prepare(`
    UPDATE config_graph_clusters
    SET name = ?, description = ?, updated_at = ?
    WHERE id = ?
  `).run(name, description, new Date().toISOString(), clusterId);
  return db.prepare('SELECT * FROM config_graph_clusters WHERE id = ?').get(clusterId);
}

export function upsertGraphCluster(db, patch = {}) {
  ensureGraphSchema(db);
  const members = Array.isArray(patch.members)
    ? [...new Set(patch.members.map(id => String(id)).filter(id => /^[0-9a-f-]{36}$/i.test(id)))].sort()
    : [];
  if (members.length < DEFAULT_CLUSTER_MIN_SIZE) return null;

  const existingMembers = db.prepare(`
    SELECT id FROM configs
    WHERE id IN (${members.map(() => '?').join(',')})
  `).all(...members).map(row => row.id);
  if (existingMembers.length !== members.length) return null;

  const algorithm = String(patch.algorithm ?? 'client-subcluster-v1').trim().slice(0, 80) || 'client-subcluster-v1';
  const threshold = Number.isFinite(Number(patch.threshold)) ? Number(patch.threshold) : DEFAULT_CLUSTER_THRESHOLD;
  const signature = String(patch.signature ?? `${algorithm}:${threshold}:${members.join('|')}`).trim().slice(0, 2000);
  const id = stableClusterId(signature);
  const name = String(patch.name ?? '').trim().slice(0, 80) || null;
  const description = String(patch.description ?? '').trim().slice(0, 400) || null;
  const now = new Date().toISOString();

  db.transaction(() => {
    db.prepare(`
      INSERT INTO config_graph_clusters (
        id, signature, name, description, algorithm, threshold, member_count, created_at, updated_at
      ) VALUES (
        @id, @signature, @name, @description, @algorithm, @threshold, @member_count, @now, @now
      )
      ON CONFLICT(signature) DO UPDATE SET
        name = excluded.name,
        description = excluded.description,
        algorithm = excluded.algorithm,
        threshold = excluded.threshold,
        member_count = excluded.member_count,
        updated_at = excluded.updated_at
    `).run({
      id,
      signature,
      name,
      description,
      algorithm,
      threshold,
      member_count: members.length,
      now,
    });
    db.prepare('DELETE FROM config_graph_cluster_members WHERE cluster_id = ?').run(id);
    const insertMember = db.prepare(`
      INSERT OR REPLACE INTO config_graph_cluster_members (cluster_id, config_id, strength)
      VALUES (?, ?, ?)
    `);
    for (const member of members) {
      insertMember.run(id, member, averageMemberStrength(db, member, members));
    }
  })();

  return db.prepare('SELECT * FROM config_graph_clusters WHERE id = ?').get(id);
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
        SELECT source_config_id, target_config_id, strength, dissimilarity, shared_tokens
        FROM config_graph_edges
        WHERE source_config_id IN (${placeholders})
          AND target_config_id IN (${placeholders})
      `).all(...nodeIds, ...nodeIds)
    : [];
  const nodeRows = [center, ...edges];
  const vectors = nodeRows.map(row => vectorForConfig(row));
  const dissimilarities = [];
  for (let i = 0; i < vectors.length; i++) {
    for (let j = i + 1; j < vectors.length; j++) {
      const result = similarity(vectors[i], vectors[j]);
      dissimilarities.push({
        source: vectors[i].id,
        target: vectors[j].id,
        dissimilarity: result.dissimilarity,
      });
    }
  }
  const clusters = nodeIds.length > 1
    ? db.prepare(`
        SELECT cl.*
        FROM config_graph_clusters cl
        WHERE EXISTS (
          SELECT 1 FROM config_graph_cluster_members m
          WHERE m.cluster_id = cl.id
            AND m.config_id IN (${placeholders})
        )
      `).all(...nodeIds)
    : [];
  const clusterMembers = clusters.length
    ? db.prepare(`
        SELECT cluster_id, config_id, strength
        FROM config_graph_cluster_members
        WHERE cluster_id IN (${clusters.map(() => '?').join(',')})
      `).all(...clusters.map(cluster => cluster.id))
    : [];
  const membersByCluster = new Map();
  for (const member of clusterMembers) {
    const list = membersByCluster.get(member.cluster_id) ?? [];
    list.push(member);
    membersByCluster.set(member.cluster_id, list);
  }

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
      dissimilarity: edge.dissimilarity,
      cluster_id: edge.cluster_id,
      shared_tokens: parseJson(edge.shared_tokens, []),
    })),
    dissimilarities,
    clusters: clusters.map(cluster => ({
      id: cluster.id,
      signature: cluster.signature,
      name: cluster.name,
      description: cluster.description,
      algorithm: cluster.algorithm,
      threshold: cluster.threshold,
      member_count: cluster.member_count,
      members: (membersByCluster.get(cluster.id) ?? []).map(member => ({
        config_id: member.config_id,
        strength: member.strength,
      })),
    })),
  };
}
