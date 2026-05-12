import { useEffect, useMemo, useRef, useState } from 'react';

const GRAPH_VISIBLE_NEIGHBORS = 24;
const GRAPH_FETCH_LIMIT = 60;
const WORLD_SIZE = 2400;
const MIN_ZOOM = 0.35;
const MAX_ZOOM = 2.6;
const CLUSTER_STRENGTH = 0.86;
const CLUSTER_MIN_SIZE = 3;
const LAYOUT_TICKS = 420;
const SEPARATION_TICKS = 90;

function shortName(name) {
  if (!name) return 'Config';
  return name.length > 30 ? `${name.slice(0, 27).trimEnd()}...` : name;
}

function modeLabel(mode) {
  if (mode === 'cluster') return 'Cluster';
  return mode === 'gamepad' ? 'Gamepad' : 'Touch Mouse';
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function undirectedEdges(edges) {
  const seen = new Set();
  const result = [];
  for (const edge of edges) {
    const key = [edge.source, edge.target].sort().join(':');
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(edge);
  }
  return result;
}

function pairKey(a, b) {
  return [a, b].sort().join(':');
}

function average(values) {
  if (!values.length) return 0;
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function clusterGraph(graph, expandedClusters) {
  if (!graph?.nodes?.length) return graph;

  const centerId = graph.nodes[0]?.config?.id;
  const nodeById = new Map(graph.nodes.map(node => [node.config.id, node]));

  let clusters = (graph.clusters ?? [])
    .map(cluster => ({
      ...cluster,
      members: (cluster.members ?? [])
        .map(member => member.config_id)
        .filter(id => id !== centerId && nodeById.has(id)),
    }))
    .filter(cluster => cluster.members.length >= CLUSTER_MIN_SIZE);

  if (!clusters.length) {
    const tightAdj = new Map();

    for (const edge of undirectedEdges(graph.edges)) {
      if (edge.source === centerId || edge.target === centerId) continue;
      if ((edge.strength ?? 0) < CLUSTER_STRENGTH) continue;
      if (!nodeById.has(edge.source) || !nodeById.has(edge.target)) continue;
      tightAdj.set(edge.source, [...(tightAdj.get(edge.source) ?? []), edge.target]);
      tightAdj.set(edge.target, [...(tightAdj.get(edge.target) ?? []), edge.source]);
    }

    const visited = new Set();
    clusters = [];
    for (const id of tightAdj.keys()) {
      if (visited.has(id)) continue;
      const stack = [id];
      const members = [];
      visited.add(id);
      while (stack.length) {
        const current = stack.pop();
        members.push(current);
        for (const next of tightAdj.get(current) ?? []) {
          if (visited.has(next)) continue;
          visited.add(next);
          stack.push(next);
        }
      }
      if (members.length >= CLUSTER_MIN_SIZE) {
        const clusterId = `cluster:${members.sort().join('|')}`;
        clusters.push({ id: clusterId, members });
      }
    }
  }

  const collapsedClusters = clusters.filter(cluster => !expandedClusters.has(cluster.id));
  if (!collapsedClusters.length) return graph;

  const visibleNodeById = new Map();
  const idMap = new Map();
  for (const node of graph.nodes) visibleNodeById.set(node.config.id, node);

  for (const cluster of collapsedClusters) {
    const memberNodes = cluster.members.map(id => nodeById.get(id)).filter(Boolean);
    for (const id of cluster.members) {
      idMap.set(id, cluster.id);
      visibleNodeById.delete(id);
    }

    const strengths = memberNodes.map(node => node.strength ?? 0);
    const modes = new Set(memberNodes.map(node => node.config.mode));
    const names = memberNodes.map(node => node.config.profile_name).slice(0, 5);
    const displayName = cluster.name || `${memberNodes.length} similar configs`;
    visibleNodeById.set(cluster.id, {
      cluster: true,
      clusterId: cluster.id,
      clusterName: cluster.name,
      members: memberNodes,
      strength: average(strengths),
      shared_tokens: [...new Set(memberNodes.flatMap(node => node.shared_tokens ?? []))].slice(0, 8),
      config: {
        id: cluster.id,
        mode: modes.size === 1 ? [...modes][0] : 'cluster',
        profile_name: displayName,
        description: names.join(', ') + (memberNodes.length > names.length ? `, +${memberNodes.length - names.length} more` : ''),
      },
    });
  }

  function mappedId(id) {
    return idMap.get(id) ?? id;
  }

  const edgeByPair = new Map();
  for (const edge of graph.edges) {
    const source = mappedId(edge.source);
    const target = mappedId(edge.target);
    if (source === target) continue;
    if (!visibleNodeById.has(source) || !visibleNodeById.has(target)) continue;
    const key = `${source}:${target}`;
    const existing = edgeByPair.get(key);
    if (!existing || edge.strength > existing.strength) {
      edgeByPair.set(key, {
        source,
        target,
        strength: edge.strength,
        dissimilarity: edge.dissimilarity ?? 1 - edge.strength,
        shared_tokens: edge.shared_tokens ?? [],
      });
    }
  }

  const dissimilarityBuckets = new Map();
  for (const item of graph.dissimilarities ?? []) {
    const source = mappedId(item.source);
    const target = mappedId(item.target);
    if (source === target) continue;
    if (!visibleNodeById.has(source) || !visibleNodeById.has(target)) continue;
    const key = pairKey(source, target);
    dissimilarityBuckets.set(key, [...(dissimilarityBuckets.get(key) ?? []), item.dissimilarity ?? 1]);
  }

  return {
    ...graph,
    nodes: [...visibleNodeById.values()],
    edges: [...edgeByPair.values()],
    dissimilarities: [...dissimilarityBuckets.entries()].map(([key, values]) => {
      const [source, target] = key.split(':');
      return { source, target, dissimilarity: average(values) };
    }),
    collapsedClusterCount: collapsedClusters.length,
  };
}

function limitVisibleGraph(graph, maxNeighbors) {
  if (!graph?.nodes?.length) return graph;
  const center = graph.nodes[0];
  const rest = graph.nodes
    .slice(1)
    .sort((a, b) => (b.strength ?? 0) - (a.strength ?? 0))
    .slice(0, maxNeighbors);
  const keep = new Set([center.config.id, ...rest.map(node => node.config.id)]);
  return {
    ...graph,
    nodes: [center, ...rest],
    edges: (graph.edges ?? []).filter(edge => keep.has(edge.source) && keep.has(edge.target)),
    dissimilarities: (graph.dissimilarities ?? []).filter(item => keep.has(item.source) && keep.has(item.target)),
  };
}

function forceLayout(graph) {
  if (!graph?.nodes?.length) return [];

  const nodes = graph.nodes.map((node, index) => {
    const angle = index === 0 ? 0 : (-Math.PI / 2) + ((index - 1) / Math.max(1, graph.nodes.length - 1)) * Math.PI * 2;
    const radius = index === 0 ? 0 : 330 + (1 - Math.min(1, node.strength)) * 320;
    return {
      node,
      id: node.config.id,
      center: index === 0,
      x: Math.cos(angle) * radius,
      y: Math.sin(angle) * radius,
      vx: 0,
      vy: 0,
    };
  });

  const byId = new Map(nodes.map(item => [item.id, item]));
  const edges = undirectedEdges(graph.edges)
    .map(edge => ({ ...edge, sourceNode: byId.get(edge.source), targetNode: byId.get(edge.target) }))
    .filter(edge => edge.sourceNode && edge.targetNode);
  const dissimilarityByPair = new Map(
    (graph.dissimilarities ?? []).map(item => [
      [item.source, item.target].sort().join(':'),
      clamp(item.dissimilarity ?? 0, 0, 1),
    ]),
  );

  const clampNode = node => {
    node.x = clamp(node.x, -WORLD_SIZE / 2 + 150, WORLD_SIZE / 2 - 150);
    node.y = clamp(node.y, -WORLD_SIZE / 2 + 110, WORLD_SIZE / 2 - 110);
  };

  for (let tick = 0; tick < LAYOUT_TICKS; tick++) {
    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const a = nodes[i];
        const b = nodes[j];
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const distSq = Math.max(2500, dx * dx + dy * dy);
        const dist = Math.sqrt(distSq);
        const pairKey = [a.id, b.id].sort().join(':');
        const dissimilarity = dissimilarityByPair.get(pairKey) ?? 0;
        const force = (9000 + dissimilarity * 65000) / distSq;
        const fx = (dx / dist) * force;
        const fy = (dy / dist) * force;
        if (!a.center) {
          a.vx -= fx;
          a.vy -= fy;
        }
        if (!b.center) {
          b.vx += fx;
          b.vy += fy;
        }
      }
    }

    for (const edge of edges) {
      const a = edge.sourceNode;
      const b = edge.targetNode;
      const dx = b.x - a.x;
      const dy = b.y - a.y;
      const dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));
      const strength = clamp(edge.strength, 0.05, 1);
      const targetDistance = 560 - strength * 330;
      const spring = (dist - targetDistance) * (0.0012 + strength * 0.01);
      const fx = (dx / dist) * spring;
      const fy = (dy / dist) * spring;
      if (!a.center) {
        a.vx += fx;
        a.vy += fy;
      }
      if (!b.center) {
        b.vx -= fx;
        b.vy -= fy;
      }
    }

    for (const node of nodes) {
      if (node.center) {
        node.x = 0;
        node.y = 0;
        node.vx = 0;
        node.vy = 0;
        continue;
      }
      node.vx += -node.x * 0.00035;
      node.vy += -node.y * 0.00035;
      node.vx *= 0.88;
      node.vy *= 0.88;
      node.x += node.vx;
      node.y += node.vy;
      clampNode(node);
    }
  }

  for (let tick = 0; tick < SEPARATION_TICKS; tick++) {
    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const a = nodes[i];
        const b = nodes[j];
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));
        const pairKey = [a.id, b.id].sort().join(':');
        const dissimilarity = dissimilarityByPair.get(pairKey) ?? 0;
        const baseDistance = a.node.cluster || b.node.cluster ? 330 : 235;
        const desiredDistance = baseDistance + dissimilarity * 170;
        if (dist >= desiredDistance) continue;

        const push = ((desiredDistance - dist) / dist) * 0.42;
        const fx = dx * push;
        const fy = dy * push;
        if (a.center) {
          b.x += fx;
          b.y += fy;
        } else if (b.center) {
          a.x -= fx;
          a.y -= fy;
        } else {
          a.x -= fx * 0.5;
          a.y -= fy * 0.5;
          b.x += fx * 0.5;
          b.y += fy * 0.5;
        }
        clampNode(a);
        clampNode(b);
      }
    }
  }

  return nodes;
}

export default function ConfigGraphPanel({ selectedConfig, onSelect }) {
  const [graph, setGraph] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [viewport, setViewport] = useState({ x: 0, y: 0, zoom: 1 });
  const [expandedClusters, setExpandedClusters] = useState(() => new Set());
  const stageRef = useRef(null);
  const dragRef = useRef(null);

  useEffect(() => {
    if (!selectedConfig?.id) {
      setGraph(null);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);
    fetch(`/api/v1/configs/${selectedConfig.id}/graph?limit=${GRAPH_FETCH_LIMIT}`)
      .then(res => {
        if (!res.ok) throw new Error(`${res.status}`);
        return res.json();
      })
      .then(data => {
        if (!cancelled) {
          setGraph(data);
          setViewport({ x: 0, y: 0, zoom: 1 });
          setExpandedClusters(new Set());
        }
      })
      .catch(err => {
        if (!cancelled) setError(err.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [selectedConfig?.id]);

  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) return undefined;

    function onWheel(e) {
      const node = e.target.closest?.('.graph-node');
      if (node) {
        const description = node.querySelector('.graph-node-description');
        if (description && description.scrollHeight > description.clientHeight) {
          e.preventDefault();
          e.stopPropagation();
          description.scrollTop += e.deltaY;
          return;
        }
      }

      e.preventDefault();
      e.stopPropagation();
      const factor = e.deltaY > 0 ? 0.9 : 1.1;
      setViewport(prev => ({ ...prev, zoom: clamp(prev.zoom * factor, MIN_ZOOM, MAX_ZOOM) }));
    }

    stage.addEventListener('wheel', onWheel, { passive: false });
    return () => stage.removeEventListener('wheel', onWheel);
  }, [graph?.nodes?.length]);

  const visibleGraph = useMemo(
    () => limitVisibleGraph(clusterGraph(graph, expandedClusters), GRAPH_VISIBLE_NEIGHBORS),
    [graph, expandedClusters],
  );

  const layout = useMemo(() => forceLayout(visibleGraph), [visibleGraph]);

  const byId = useMemo(() => {
    const map = new Map();
    for (const item of layout) map.set(item.node.config.id, item);
    return map;
  }, [layout]);

  function handlePointerDown(e) {
    if (e.target.closest('.graph-node')) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    dragRef.current = { pointerId: e.pointerId, x: e.clientX, y: e.clientY };
  }

  function handlePointerMove(e) {
    if (!dragRef.current) return;
    const dx = e.clientX - dragRef.current.x;
    const dy = e.clientY - dragRef.current.y;
    dragRef.current = { ...dragRef.current, x: e.clientX, y: e.clientY };
    setViewport(prev => ({ ...prev, x: prev.x + dx, y: prev.y + dy }));
  }

  function handlePointerUp(e) {
    if (dragRef.current?.pointerId === e.pointerId) {
      dragRef.current = null;
    }
  }

  if (!selectedConfig) {
    return (
      <div className="graph-empty">
        <h2>Pick A Config</h2>
        <p>Select a config in the Browser tab to see nearby layouts here.</p>
      </div>
    );
  }

  if (loading) return <div className="graph-empty"><p>Loading graph...</p></div>;
  if (error) return <div className="graph-empty"><p>Graph unavailable: {error}</p></div>;
  if (!graph || graph.nodes.length <= 1) {
    return (
      <div className="graph-empty">
        <h2>No Nearby Configs Yet</h2>
        <p>The selected config does not have graph edges above the current similarity threshold.</p>
      </div>
    );
  }

  const visibleEdges = undirectedEdges(visibleGraph.edges).filter(edge => byId.has(edge.source) && byId.has(edge.target));
  const collapsedClusterCount = visibleGraph.collapsedClusterCount ?? 0;

  async function nameCluster(node) {
    const name = window.prompt('Cluster name', node.clusterName ?? '');
    if (name === null) return;
    const res = await fetch(`/api/v1/graph/clusters/${node.clusterId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name }),
    });
    if (!res.ok) return;
    const updated = await res.json();
    setGraph(prev => ({
      ...prev,
      clusters: (prev?.clusters ?? []).map(cluster => (
        cluster.id === updated.id ? { ...cluster, name: updated.name, description: updated.description } : cluster
      )),
    }));
  }

  function handleNodeClick(event, node) {
    if (node.cluster) {
      if (event.shiftKey) {
        nameCluster(node);
        return;
      }
      setExpandedClusters(prev => {
        const next = new Set(prev);
        if (next.has(node.clusterId)) next.delete(node.clusterId);
        else next.add(node.clusterId);
        return next;
      });
      return;
    }
    onSelect(node.config);
  }

  return (
    <div className="graph-panel">
      <div className="graph-header">
        <div>
          <h2>{selectedConfig.profile_name}</h2>
          <p>
            {visibleGraph.nodes.length - 1} visible configs from {graph.nodes.length - 1} nearby configs
            {collapsedClusterCount > 0 ? `; ${collapsedClusterCount} tight cluster${collapsedClusterCount === 1 ? '' : 's'} collapsed` : ''}
          </p>
        </div>
      </div>

      <div
        ref={stageRef}
        className="graph-stage"
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
      >
        <div
          className="graph-viewport"
          style={{ transform: `translate(${viewport.x}px, ${viewport.y}px) scale(${viewport.zoom})` }}
        >
          <svg className="graph-edges" viewBox={`${-WORLD_SIZE / 2} ${-WORLD_SIZE / 2} ${WORLD_SIZE} ${WORLD_SIZE}`} aria-hidden="true">
            {visibleEdges.map(edge => {
              const source = byId.get(edge.source);
              const target = byId.get(edge.target);
              const opacity = Math.max(0.12, Math.min(0.82, edge.strength));
              return (
                <line
                  key={`${edge.source}:${edge.target}`}
                  x1={source.x}
                  y1={source.y}
                  x2={target.x}
                  y2={target.y}
                  stroke="currentColor"
                  strokeWidth={edge.source === selectedConfig.id ? 4 : 2}
                  opacity={opacity}
                />
              );
            })}
          </svg>

          {layout.map(({ node, x, y, center }) => {
            const cfg = node.config;
            return (
              <button
                key={cfg.id}
                className={'graph-node' + (center ? ' center' : '') + (node.cluster ? ' cluster' : '') + ` ${cfg.mode}`}
                style={{ left: x, top: y }}
                onClick={event => handleNodeClick(event, node)}
                title={node.cluster ? 'Click to expand; Shift-click to name cluster' : `${cfg.profile_name} (${Math.round(node.strength * 100)}%)`}
              >
                <span className="graph-node-title">{shortName(cfg.profile_name)}</span>
                <span className="graph-node-full-title">{cfg.profile_name}</span>
                <span className="graph-node-meta">
                  {modeLabel(cfg.mode)} · {Math.round(node.strength * 100)}%
                </span>
                {!center && node.shared_tokens?.length > 0 && (
                  <span className="graph-node-tokens">{node.shared_tokens.slice(0, 3).join(', ')}</span>
                )}
                <span className="graph-node-description">
                  {cfg.description || (node.shared_tokens?.length ? `Shared: ${node.shared_tokens.join(', ')}` : cfg.category || '')}
                </span>
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
