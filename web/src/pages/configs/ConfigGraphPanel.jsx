import { useEffect, useMemo, useRef, useState } from 'react';

const GRAPH_LIMIT = 24;
const WORLD_SIZE = 1600;
const MIN_ZOOM = 0.35;
const MAX_ZOOM = 2.6;

function shortName(name) {
  if (!name) return 'Config';
  return name.length > 30 ? `${name.slice(0, 27).trimEnd()}...` : name;
}

function modeLabel(mode) {
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

function forceLayout(graph) {
  if (!graph?.nodes?.length) return [];

  const nodes = graph.nodes.map((node, index) => {
    const angle = index === 0 ? 0 : (-Math.PI / 2) + ((index - 1) / Math.max(1, graph.nodes.length - 1)) * Math.PI * 2;
    const radius = index === 0 ? 0 : 250 + (1 - Math.min(1, node.strength)) * 180;
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

  for (let tick = 0; tick < 320; tick++) {
    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const a = nodes[i];
        const b = nodes[j];
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const distSq = Math.max(900, dx * dx + dy * dy);
        const dist = Math.sqrt(distSq);
        const force = 2400 / distSq;
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
      const targetDistance = 390 - strength * 240;
      const spring = (dist - targetDistance) * (0.0025 + strength * 0.018);
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
      node.vx += -node.x * 0.0008;
      node.vy += -node.y * 0.0008;
      node.vx *= 0.88;
      node.vy *= 0.88;
      node.x = clamp(node.x + node.vx, -WORLD_SIZE / 2 + 90, WORLD_SIZE / 2 - 90);
      node.y = clamp(node.y + node.vy, -WORLD_SIZE / 2 + 60, WORLD_SIZE / 2 - 60);
    }
  }

  return nodes;
}

export default function ConfigGraphPanel({ selectedConfig, onSelect }) {
  const [graph, setGraph] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [viewport, setViewport] = useState({ x: 0, y: 0, zoom: 1 });
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
    fetch(`/api/v1/configs/${selectedConfig.id}/graph?limit=${GRAPH_LIMIT}`)
      .then(res => {
        if (!res.ok) throw new Error(`${res.status}`);
        return res.json();
      })
      .then(data => {
        if (!cancelled) {
          setGraph(data);
          setViewport({ x: 0, y: 0, zoom: 1 });
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

  const layout = useMemo(() => forceLayout(graph), [graph]);

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

  function handleStageWheel(e) {
    e.preventDefault();
    e.stopPropagation();
    const factor = e.deltaY > 0 ? 0.9 : 1.1;
    setViewport(prev => ({ ...prev, zoom: clamp(prev.zoom * factor, MIN_ZOOM, MAX_ZOOM) }));
  }

  function handleNodeWheel(e) {
    const description = e.currentTarget.querySelector('.graph-node-description');
    if (!description || description.scrollHeight <= description.clientHeight) return;

    e.preventDefault();
    e.stopPropagation();
    description.scrollTop += e.deltaY;
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

  const visibleEdges = undirectedEdges(graph.edges).filter(edge => byId.has(edge.source) && byId.has(edge.target));

  return (
    <div className="graph-panel">
      <div className="graph-header">
        <div>
          <h2>{selectedConfig.profile_name}</h2>
          <p>{graph.nodes.length - 1} nearby configs by metadata and layout-token similarity</p>
        </div>
      </div>

      <div
        ref={stageRef}
        className="graph-stage"
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
        onWheel={handleStageWheel}
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
                className={'graph-node' + (center ? ' center' : '') + ` ${cfg.mode}`}
                style={{ left: x, top: y }}
                onClick={() => onSelect(cfg)}
                onWheel={handleNodeWheel}
                title={`${cfg.profile_name} (${Math.round(node.strength * 100)}%)`}
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
