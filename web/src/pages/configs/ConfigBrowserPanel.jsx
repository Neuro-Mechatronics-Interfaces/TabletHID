import { useState, useEffect, useCallback, useMemo } from 'react';
import ConfigCard from './ConfigCard.jsx';

const FETCH_LIMIT = 100;
const FILTERS_OPEN_KEY = 'configs:filtersOpen';

export default function ConfigBrowserPanel({ onSelect, selectedId, selectedConfig, activeTag, onTagSelect }) {
  const [configs, setConfigs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [mode, setMode] = useState('');
  const [platform, setPlatform] = useState('');
  const [sort, setSort] = useState('recent');
  const [category, setCategory] = useState('');
  const [search, setSearch] = useState('');
  const [filtersOpen, setFiltersOpen] = useState(
    () => localStorage.getItem(FILTERS_OPEN_KEY) !== 'false',
  );

  const fetchConfigs = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const allConfigs = [];
      let offset = 0;
      let total = null;

      while (total === null || allConfigs.length < total) {
        const params = new URLSearchParams({ sort, limit: FETCH_LIMIT, offset });
        if (mode) params.set('mode', mode);
        if (platform) params.set('platform', platform);
        if (category) params.set('category', category);
        if (activeTag) params.set('tags', activeTag);

        const res = await fetch(`/api/v1/configs?${params}`);
        if (!res.ok) throw new Error(`${res.status}`);
        const data = await res.json();
        const page = data.configs ?? [];
        allConfigs.push(...page);
        total = Number.isFinite(data.total) ? data.total : allConfigs.length;
        if (page.length < FETCH_LIMIT) break;
        offset += FETCH_LIMIT;
      }

      setConfigs(allConfigs);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [mode, platform, sort, category, activeTag]);

  useEffect(() => { fetchConfigs(); }, [fetchConfigs]);

  useEffect(() => {
    localStorage.setItem(FILTERS_OPEN_KEY, String(filtersOpen));
  }, [filtersOpen]);

  useEffect(() => {
    if (!selectedId || configs.length === 0) return;
    const selected = configs.find(c => c.id === selectedId);
    if (selected) onSelect(selected);
  }, [configs, onSelect, selectedId]);

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
    return base;
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
        <div className="cfg-filter-head">
          <span className="cfg-filter-title">Filters</span>
          <button
            className="cfg-filter-toggle"
            type="button"
            onClick={() => setFiltersOpen(v => !v)}
            aria-expanded={filtersOpen}
          >
            {filtersOpen ? 'Collapse' : 'Expand'}
          </button>
        </div>
        <div className="cfg-search-row">
          <input
            className="cfg-search"
            type="search"
            placeholder="Search name, tags, category…"
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
        </div>

        {filtersOpen && (
          <div className="cfg-filter-options">
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
          {filtered.length} config{filtered.length !== 1 ? 's' : ''}
        </div>
      )}

      <SelectedConfigMeta
        config={selectedConfig}
        activeTag={activeTag}
        onTagSelect={onTagSelect}
      />
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

function SelectedConfigMeta({ config, activeTag, onTagSelect }) {
  if (!config) {
    return (
      <div className="configs-meta configs-meta-sidebar configs-meta-empty">
        Select a config to see its tags and category.
      </div>
    );
  }

  const tags = (config.tags ?? []).map(t => t.toLowerCase().trim()).filter(Boolean);
  const toTitleCase = s => s.replace(/\b\w/g, c => c.toUpperCase());

  return (
    <div className="configs-meta configs-meta-sidebar">
      <span className={'configs-meta-badge mode-' + config.mode}>
        {config.mode === 'gamepad' ? 'Gamepad' : 'Touch Mouse'}
      </span>
      {config.platform && (
        <span className={'configs-meta-badge platform-' + config.platform}>
          {config.platform === 'ios' ? 'iOS' : 'Android'}
        </span>
      )}
      {config.category && (
        <span className="configs-meta-badge category">
          {toTitleCase(config.category.trim())}
        </span>
      )}
      {tags.map(t => (
        <button
          key={t}
          className={'configs-meta-tag' + (activeTag === t ? ' active' : '')}
          onClick={() => onTagSelect(activeTag === t ? '' : t)}
          title={activeTag === t ? 'Clear tag filter' : `Filter by "${t}"`}
        >
          {t}
        </button>
      ))}
    </div>
  );
}
