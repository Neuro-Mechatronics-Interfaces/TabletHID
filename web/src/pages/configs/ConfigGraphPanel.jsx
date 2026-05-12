import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

const GRAPH_VISIBLE_NEIGHBORS = 24;
const GRAPH_FETCH_LIMIT = 96;
const WORLD_SIZE = 2400;
const MIN_ZOOM = 0.35;
const MAX_ZOOM = 2.6;
const CLUSTER_STRENGTH = 0.86;
const CLUSTER_MIN_SIZE = 3;
const CLUSTER_SPLIT_SIZE = 12;
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

function memberKey(members) {
  return [...members].sort().join('|');
}

function isUuid(value) {
  return /^[0-9a-f-]{36}$/i.test(value ?? '');
}

function average(values) {
  if (!values.length) return 0;
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function edgeStrengthMap(edges) {
  const map = new Map();
  for (const edge of edges ?? []) {
    const key = pairKey(edge.source, edge.target);
    map.set(key, Math.max(map.get(key) ?? 0, edge.strength ?? 0));
  }
  return map;
}

function connectedComponents(ids, strengthByPair, threshold) {
  const idSet = new Set(ids);
  const adjacency = new Map(ids.map(id => [id, []]));
  for (let i = 0; i < ids.length; i++) {
    for (let j = i + 1; j < ids.length; j++) {
      const strength = strengthByPair.get(pairKey(ids[i], ids[j])) ?? 0;
      if (strength < threshold) continue;
      adjacency.get(ids[i]).push(ids[j]);
      adjacency.get(ids[j]).push(ids[i]);
    }
  }

  const visited = new Set();
  const components = [];
  for (const id of idSet) {
    if (visited.has(id)) continue;
    const stack = [id];
    const component = [];
    visited.add(id);
    while (stack.length) {
      const current = stack.pop();
      component.push(current);
      for (const next of adjacency.get(current) ?? []) {
        if (visited.has(next)) continue;
        visited.add(next);
        stack.push(next);
      }
    }
    components.push(component);
  }
  return components.sort((a, b) => b.length - a.length);
}

function splitCluster(cluster, graph, nodeById) {
  if (cluster.members.length <= CLUSTER_SPLIT_SIZE) return [cluster];

  const strengthByPair = edgeStrengthMap(graph.edges);
  for (const threshold of [0.96, 0.93, 0.9, 0.86]) {
    const components = connectedComponents(cluster.members, strengthByPair, threshold);
    if (components.length <= 1) continue;
    return components.flatMap((members, index) => {
      if (members.length <= CLUSTER_SPLIT_SIZE) {
        return [{ ...cluster, id: `${cluster.id}:sub:${threshold}:${index}`, parentClusterId: cluster.id, name: null, description: null, members }];
      }
      return splitCluster(
        { ...cluster, id: `${cluster.id}:sub:${threshold}:${index}`, parentClusterId: cluster.id, name: null, description: null, members },
        graph,
        nodeById,
      );
    });
  }

  return cluster.members
    .map(id => nodeById.get(id))
    .filter(Boolean)
    .sort((a, b) => (b.strength ?? 0) - (a.strength ?? 0))
    .reduce((chunks, node, index) => {
      const chunkIndex = Math.floor(index / CLUSTER_SPLIT_SIZE);
      chunks[chunkIndex] = chunks[chunkIndex] ?? [];
      chunks[chunkIndex].push(node.config.id);
      return chunks;
    }, [])
    .map((members, index) => ({ ...cluster, id: `${cluster.id}:chunk:${index}`, parentClusterId: cluster.id, name: null, description: null, members }));
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

  clusters = clusters
    .flatMap(cluster => splitCluster(cluster, graph, nodeById))
    .filter(cluster => cluster.members.length >= CLUSTER_MIN_SIZE);
  clusters = [...clusters.reduce((byMembers, cluster) => {
    const key = memberKey(cluster.members);
    const existing = byMembers.get(key);
    if (!existing || (!existing.name && cluster.name) || (!isUuid(existing.id) && isUuid(cluster.id))) {
      byMembers.set(key, cluster);
    }
    return byMembers;
  }, new Map()).values()];

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
    const splitLabel = cluster.parentClusterId ? ` group ${cluster.id.split(':').at(-1)}` : '';
    const displayName = cluster.name
      ? `${cluster.name}${splitLabel}`
      : `${memberNodes.length} similar configs${splitLabel}`;
    visibleNodeById.set(cluster.id, {
      cluster: true,
      clusterId: cluster.id,
      parentClusterId: cluster.parentClusterId,
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
  const [clusterEditor, setClusterEditor] = useState(null);
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
          setClusterEditor(null);
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

  const handleWheel = useCallback(e => {
    const nativeEvent = e.nativeEvent ?? e;
    if (nativeEvent.__tablethidGraphWheelHandled) return;
    nativeEvent.__tablethidGraphWheelHandled = true;

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
  }, []);

  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) return undefined;

    stage.addEventListener('wheel', handleWheel, { passive: false });
    return () => stage.removeEventListener('wheel', handleWheel);
  }, [handleWheel]);

  useEffect(() => {
    function onKeyDown(event) {
      if (event.key === 'Escape') setClusterEditor(null);
    }

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

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
  const childEdges = visibleEdges.filter(edge => edge.source === selectedConfig.id || edge.target === selectedConfig.id);
  const collapsedClusterCount = visibleGraph.collapsedClusterCount ?? 0;

  function openClusterEditor(event, node) {
    event.preventDefault();
    event.stopPropagation();
    if (!node.cluster) return;

    const stageRect = stageRef.current?.getBoundingClientRect();
    const x = stageRect ? clamp(event.clientX - stageRect.left, 12, Math.max(12, stageRect.width - 332)) : 24;
    const y = stageRect ? clamp(event.clientY - stageRect.top, 12, Math.max(12, stageRect.height - 190)) : 24;
    const members = node.members.map(member => member.config.id).sort();
    const targetClusterId = isUuid(node.clusterId) ? node.clusterId : null;
    setClusterEditor({
      x,
      y,
      node,
      targetClusterId,
      signature: `client-subcluster-v1:${memberKey(members)}`,
      members,
      name: node.clusterName ?? '',
      saving: false,
      error: null,
      confirmation: null,
    });
  }

  async function submitClusterName(event) {
    event.preventDefault();
    if (!clusterEditor || clusterEditor.saving) return;

    const name = clusterEditor.name;
    setClusterEditor(prev => ({ ...prev, saving: true, error: null, confirmation: null }));
    const res = await fetch(clusterEditor.targetClusterId ? `/api/v1/graph/clusters/${clusterEditor.targetClusterId}` : '/api/v1/graph/clusters', {
      method: clusterEditor.targetClusterId ? 'PATCH' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name,
        signature: clusterEditor.signature,
        algorithm: 'client-subcluster-v1',
        members: clusterEditor.members,
        threshold: CLUSTER_STRENGTH,
      }),
    });
    if (!res.ok) {
      setClusterEditor(prev => ({ ...prev, saving: false, error: 'Cluster name could not be saved.' }));
      return;
    }
    const updated = await res.json();
    setGraph(prev => ({
      ...prev,
      clusters: [
        ...(prev?.clusters ?? []).filter(cluster => cluster.id !== updated.id),
        {
          ...updated,
          members: clusterEditor.members.map(configId => ({ config_id: configId, strength: 1 })),
        },
      ],
    }));
    setClusterEditor(prev => ({
      ...prev,
      targetClusterId: updated.id,
      name: updated.name ?? '',
      saving: false,
      error: null,
      confirmation: `Saved cluster ${updated.id}`,
    }));
  }

  function handleNodeClick(event, node) {
    if (node.cluster) {
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
        onWheelCapture={handleWheel}
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
            {childEdges.map(edge => {
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
                onContextMenu={node.cluster ? event => openClusterEditor(event, node) : undefined}
                title={node.cluster ? 'Click to expand; right-click to name cluster' : `${cfg.profile_name} (${Math.round(node.strength * 100)}%)`}
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

        {clusterEditor && (
          <form
            className="graph-cluster-editor"
            style={{ left: clusterEditor.x, top: clusterEditor.y }}
            onSubmit={submitClusterName}
            onPointerDown={event => event.stopPropagation()}
            onClick={event => event.stopPropagation()}
            onWheel={event => event.stopPropagation()}
          >
            <div className="graph-cluster-editor-head">
              <div>
                <strong>Name Cluster</strong>
                <span>{clusterEditor.node.members.length} configs</span>
              </div>
              <button type="button" className="graph-cluster-editor-close" onClick={() => setClusterEditor(null)} aria-label="Close cluster editor">
                x
              </button>
            </div>
            <label>
              <span>Cluster name</span>
              <input
                autoFocus
                value={clusterEditor.name}
                maxLength={80}
                onChange={event => setClusterEditor(prev => ({ ...prev, name: event.target.value, confirmation: null, error: null }))}
                placeholder="e.g. Assassin-style touch layouts"
              />
            </label>
            <div className="graph-cluster-editor-id">
              UUID: <code>{clusterEditor.targetClusterId ?? 'created on save'}</code>
            </div>
            {clusterEditor.error && <div className="graph-cluster-editor-error">{clusterEditor.error}</div>}
            {clusterEditor.confirmation && <div className="graph-cluster-editor-confirm">{clusterEditor.confirmation}</div>}
            <div className="graph-cluster-editor-actions">
              <button type="button" className="secondary" onClick={() => setClusterEditor(null)}>Close</button>
              <button type="submit" disabled={clusterEditor.saving}>
                {clusterEditor.saving ? 'Saving...' : 'Save Name'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
