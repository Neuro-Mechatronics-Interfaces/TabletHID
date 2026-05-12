import { useEffect, useMemo, useState } from 'react';

const GRAPH_LIMIT = 24;

function shortName(name) {
  if (!name) return 'Config';
  return name.length > 30 ? `${name.slice(0, 27).trimEnd()}...` : name;
}

function modeLabel(mode) {
  return mode === 'gamepad' ? 'Gamepad' : 'Touch Mouse';
}

export default function ConfigGraphPanel({ selectedConfig, onSelect }) {
  const [graph, setGraph] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

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
        if (!cancelled) setGraph(data);
      })
      .catch(err => {
        if (!cancelled) setError(err.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [selectedConfig?.id]);

  const layout = useMemo(() => {
    if (!graph?.nodes?.length) return [];
    const neighbors = graph.nodes.slice(1);
    return [
      { node: graph.nodes[0], x: 50, y: 50, center: true },
      ...neighbors.map((node, index) => {
        const angle = (-Math.PI / 2) + (index / Math.max(1, neighbors.length)) * Math.PI * 2;
        const radius = 26 + (1 - Math.min(1, node.strength)) * 13;
        return {
          node,
          x: 50 + Math.cos(angle) * radius,
          y: 50 + Math.sin(angle) * radius,
          center: false,
        };
      }),
    ];
  }, [graph]);

  const byId = useMemo(() => {
    const map = new Map();
    for (const item of layout) map.set(item.node.config.id, item);
    return map;
  }, [layout]);

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

  const visibleEdges = graph.edges.filter(edge => byId.has(edge.source) && byId.has(edge.target));

  return (
    <div className="graph-panel">
      <div className="graph-header">
        <div>
          <h2>{selectedConfig.profile_name}</h2>
          <p>{graph.nodes.length - 1} nearby configs by metadata and layout-token similarity</p>
        </div>
      </div>

      <div className="graph-stage">
        <svg className="graph-edges" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
          {visibleEdges.map(edge => {
            const source = byId.get(edge.source);
            const target = byId.get(edge.target);
            const opacity = Math.max(0.14, Math.min(0.75, edge.strength));
            return (
              <line
                key={`${edge.source}:${edge.target}`}
                x1={source.x}
                y1={source.y}
                x2={target.x}
                y2={target.y}
                stroke="currentColor"
                strokeWidth={edge.source === selectedConfig.id ? 0.55 : 0.2}
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
              style={{ left: `${x}%`, top: `${y}%` }}
              onClick={() => onSelect(cfg)}
              title={`${cfg.profile_name} (${Math.round(node.strength * 100)}%)`}
            >
              <span className="graph-node-title">{shortName(cfg.profile_name)}</span>
              <span className="graph-node-meta">
                {modeLabel(cfg.mode)} · {Math.round(node.strength * 100)}%
              </span>
              {!center && node.shared_tokens?.length > 0 && (
                <span className="graph-node-tokens">{node.shared_tokens.slice(0, 3).join(', ')}</span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
