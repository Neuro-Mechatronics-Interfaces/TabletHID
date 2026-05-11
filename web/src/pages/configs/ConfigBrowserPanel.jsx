import { useState, useEffect, useCallback } from 'react';
import ConfigCard from './ConfigCard.jsx';

const LIMIT = 20;

export default function ConfigBrowserPanel({ onSelect, selectedId }) {
  const [configs, setConfigs] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [mode, setMode] = useState('');
  const [platform, setPlatform] = useState('');
  const [sort, setSort] = useState('recent');

  const fetchConfigs = useCallback(async (append = false, currentLength = 0) => {
    setLoading(true);
    setError(null);
    const params = new URLSearchParams({ sort, limit: LIMIT, offset: append ? currentLength : 0 });
    if (mode) params.set('mode', mode);
    if (platform) params.set('platform', platform);
    try {
      const res = await fetch(`/api/v1/configs?${params}`);
      if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
      const data = await res.json();
      setConfigs(prev => append ? [...prev, ...data.configs] : data.configs);
      setTotal(data.total);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [mode, platform, sort]);

  useEffect(() => {
    fetchConfigs(false);
  }, [fetchConfigs]);

  const loadMore = () => fetchConfigs(true, configs.length);

  const modeChips = [['', 'All'], ['gamepad', 'Gamepad'], ['touch_mouse', 'Touch Mouse']];
  const platformChips = [['', 'All'], ['android', 'Android'], ['ios', 'iOS']];
  const sortChips = [['recent', 'Recent'], ['popular', 'Popular']];

  return (
    <div className="cfg-browser">
      <div className="cfg-filters">
        <FilterRow label="Mode" chips={modeChips} value={mode} onChange={setMode} />
        <FilterRow label="Platform" chips={platformChips} value={platform} onChange={setPlatform} />
        <FilterRow label="Sort" chips={sortChips} value={sort} onChange={setSort} />
      </div>

      <div className="cfg-list">
        {configs.map(cfg => (
          <ConfigCard
            key={cfg.id}
            config={cfg}
            isActive={cfg.id === selectedId}
            onClick={() => onSelect(cfg)}
          />
        ))}
        {loading && <div className="cfg-loading">Loading...</div>}
        {!loading && !error && configs.length === 0 && (
          <div className="cfg-empty">No configs found.</div>
        )}
        {error && <div className="cfg-error">Error: {error}</div>}
      </div>

      {!loading && configs.length > 0 && configs.length < total && (
        <button className="cfg-load-more" onClick={loadMore}>
          Load more ({configs.length} of {total})
        </button>
      )}
    </div>
  );
}

function FilterRow({ label, chips, value, onChange }) {
  return (
    <div className="cfg-filter-row">
      <span className="cfg-filter-label">{label}</span>
      <div className="cfg-chips">
        {chips.map(([v, text]) => (
          <button
            key={v}
            className={'cfg-chip' + (value === v ? ' active' : '')}
            onClick={() => onChange(v)}
          >
            {text}
          </button>
        ))}
      </div>
    </div>
  );
}
