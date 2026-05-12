import { useState, useEffect, useCallback, useMemo } from 'react';
import ConfigCard from './ConfigCard.jsx';

const FETCH_LIMIT = 100;
const MAX_CARDS = 50;

export default function ConfigBrowserPanel({ onSelect, selectedId, activeTag, onTagSelect }) {
  const [configs, setConfigs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [mode, setMode] = useState('');
  const [platform, setPlatform] = useState('');
  const [sort, setSort] = useState('recent');
  const [category, setCategory] = useState('');
  const [search, setSearch] = useState('');

  const fetchConfigs = useCallback(async () => {
    setLoading(true);
    setError(null);
    const params = new URLSearchParams({ sort, limit: FETCH_LIMIT, offset: 0 });
    if (mode) params.set('mode', mode);
    if (platform) params.set('platform', platform);
    if (category) params.set('category', category);
    if (activeTag) params.set('tags', activeTag);
    try {
      const res = await fetch(`/api/v1/configs?${params}`);
      if (!res.ok) throw new Error(`${res.status}`);
      const data = await res.json();
      setConfigs(data.configs);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [mode, platform, sort, category, activeTag]);

  useEffect(() => { fetchConfigs(); }, [fetchConfigs]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    const base = q
      ? configs.filter(c => {
          const hay = [
            c.profile_name,
            c.description ?? '',
            (c.tags ?? []).join(' '),
            c.category ?? '',
          ].join(' ').toLowerCase();
          return hay.includes(q);
        })
      : configs;
    return base.slice(0, MAX_CARDS);
  }, [configs, search]);

  const toTitleCase = s => s.replace(/\b\w/g, c => c.toUpperCase());

  const categories = useMemo(() => {
    const seen = new Set();
    for (const c of configs) if (c.category) seen.add(toTitleCase(c.category.trim()));
    return [...seen].sort();
  }, [configs]);

  const allTags = useMemo(() => {
    const counts = {};
    for (const c of configs) for (const t of c.tags ?? []) {
      const lower = t.toLowerCase().trim();
      if (lower) counts[lower] = (counts[lower] ?? 0) + 1;
    }
    return Object.entries(counts)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 12)
      .map(([t]) => t);
  }, [configs]);

  const modeChips = [['', 'All'], ['gamepad', 'Gamepad'], ['touch_mouse', 'Touch Mouse']];
  const platformChips = [['', 'All'], ['android', 'Android'], ['ios', 'iOS']];
  const sortChips = [['recent', 'Recent'], ['popular', 'Popular']];

  return (
    <div className="cfg-browser">
      <div className="cfg-filters">
        <div className="cfg-search-row">
          <input
            className="cfg-search"
            type="search"
            placeholder="Search name, tags, category…"
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
        </div>

        <FilterRow label="Mode" chips={modeChips} value={mode} onChange={setMode} />
        <FilterRow label="Platform" chips={platformChips} value={platform} onChange={setPlatform} />
        <FilterRow label="Sort" chips={sortChips} value={sort} onChange={setSort} />

        {categories.length > 0 && (
          <div className="cfg-filter-row">
            <span className="cfg-filter-label">Category</span>
            <select
              className="cfg-cat-select"
              value={category}
              onChange={e => setCategory(e.target.value)}
            >
              <option value="">All</option>
              {categories.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
        )}

        {allTags.length > 0 && (
          <div className="cfg-filter-row cfg-filter-row--wrap">
            <span className="cfg-filter-label">Tags</span>
            <div className="cfg-chips">
              {allTags.map(t => (
                <button
                  key={t}
                  className={'cfg-chip cfg-tag-chip' + (activeTag === t ? ' active' : '')}
                  onClick={() => onTagSelect(activeTag === t ? '' : t)}
                >
                  {t}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      <div className="cfg-list">
        {filtered.map(cfg => (
          <ConfigCard
            key={cfg.id}
            config={cfg}
            isActive={cfg.id === selectedId}
            onClick={() => onSelect(cfg)}
          />
        ))}
        {loading && <div className="cfg-loading">Loading…</div>}
        {!loading && !error && filtered.length === 0 && (
          <div className="cfg-empty">
            {search || activeTag || category ? 'No matching configs.' : 'No configs yet.'}
          </div>
        )}
        {error && <div className="cfg-error">Error: {error}</div>}
      </div>

      {!loading && filtered.length > 0 && (
        <div className="cfg-count">
          {filtered.length}{filtered.length === MAX_CARDS ? '+' : ''} config{filtered.length !== 1 ? 's' : ''}
        </div>
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
