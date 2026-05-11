import { useState, useEffect } from 'react';

export default function Admin() {
  const [me, setMe] = useState(null);
  const [authError, setAuthError] = useState(false);
  const [configs, setConfigs] = useState([]);
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState(null);
  const [editState, setEditState] = useState(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  useEffect(() => {
    fetch('/api/v1/admin/me')
      .then(r => {
        if (r.status === 403) { setAuthError(true); throw new Error('forbidden'); }
        return r.json();
      })
      .then(data => {
        setMe(data);
        return fetch('/api/v1/admin/configs');
      })
      .then(r => r.json())
      .then(data => setConfigs(data.configs ?? []))
      .catch(e => { if (e.message !== 'forbidden') console.error(e); });
  }, []);

  function selectConfig(cfg) {
    setSelected(cfg);
    setEditState({
      profile_name: cfg.profile_name,
      description: cfg.description ?? '',
      tags: (cfg.tags ?? []).join(', '),
      category: cfg.category ?? '',
    });
    setSaveError(null);
    setSaveSuccess(false);
    setConfirmDelete(false);
  }

  async function handleSave() {
    if (!selected || !editState) return;
    setSaving(true);
    setSaveError(null);
    setSaveSuccess(false);
    try {
      const body = {
        profile_name: editState.profile_name,
        description: editState.description || null,
        tags: editState.tags.split(',').map(t => t.trim()).filter(Boolean),
        category: editState.category || null,
      };
      const res = await fetch(`/api/v1/admin/configs/${selected.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error ?? `${res.status}`);
      setConfigs(prev => prev.map(c => c.id === data.id ? data : c));
      setSelected(data);
      setSaveSuccess(true);
    } catch (e) {
      setSaveError(e.message);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!selected) return;
    const res = await fetch(`/api/v1/admin/configs/${selected.id}`, { method: 'DELETE' });
    if (res.ok) {
      setConfigs(prev => prev.filter(c => c.id !== selected.id));
      setSelected(null);
      setEditState(null);
      setConfirmDelete(false);
    } else {
      const data = await res.json().catch(() => ({}));
      setSaveError(data.error ?? 'Delete failed.');
      setConfirmDelete(false);
    }
  }

  const filtered = configs.filter(c => {
    const q = search.toLowerCase();
    return !q ||
      c.profile_name.toLowerCase().includes(q) ||
      (c.device_name ?? '').toLowerCase().includes(q) ||
      (c.category ?? '').toLowerCase().includes(q) ||
      (c.tags ?? []).some(t => t.toLowerCase().includes(q));
  });

  if (authError) {
    return (
      <div className="admin-page">
        <div className="admin-auth-error">
          <h2>Access Denied</h2>
          <p>This page is restricted to administrators. Authenticate via your organisation's access portal.</p>
        </div>
      </div>
    );
  }

  if (!me) {
    return <div className="admin-page"><div className="admin-loading">Checking access…</div></div>;
  }

  return (
    <div className="admin-page">
      <div className="admin-header">
        <div>
          <h1 className="admin-title">Admin</h1>
          <span className="admin-user">{me.email}</span>
        </div>
        <span className="admin-count">{configs.length} configs</span>
      </div>

      <div className="admin-layout">
        {/* Left: list */}
        <div className="admin-sidebar">
          <input
            className="admin-search"
            type="search"
            placeholder="Search configs…"
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          <div className="admin-list">
            {filtered.map(cfg => (
              <div
                key={cfg.id}
                className={'admin-list-item' + (selected?.id === cfg.id ? ' active' : '')}
                onClick={() => selectConfig(cfg)}
              >
                <div className="admin-item-name">{cfg.profile_name}</div>
                <div className="admin-item-meta">
                  <span className={'cfg-card-mode-badge ' + cfg.mode}>{cfg.mode === 'gamepad' ? 'Gamepad' : 'Touch Mouse'}</span>
                  <span>{cfg.platform}</span>
                  {cfg.download_count > 0 && <span>{cfg.download_count} dl</span>}
                </div>
              </div>
            ))}
            {filtered.length === 0 && <div className="admin-empty">No configs match.</div>}
          </div>
        </div>

        {/* Right: edit panel */}
        <div className="admin-editor">
          {!selected ? (
            <div className="admin-empty-state">Select a config to edit</div>
          ) : (
            <div className="admin-edit-form">
              <div className="admin-edit-id">ID: <code>{selected.id}</code></div>

              <label className="cfg-opt-label">
                Profile Name
                <input
                  className="cfg-opt-input"
                  value={editState.profile_name}
                  maxLength={80}
                  onChange={e => setEditState(p => ({ ...p, profile_name: e.target.value }))}
                />
              </label>
              <label className="cfg-opt-label">
                Description
                <textarea
                  className="cfg-opt-input cfg-opt-textarea"
                  value={editState.description}
                  maxLength={400}
                  rows={4}
                  onChange={e => setEditState(p => ({ ...p, description: e.target.value }))}
                />
              </label>
              <label className="cfg-opt-label">
                Tags <span className="cfg-opt-hint">(comma-separated)</span>
                <input
                  className="cfg-opt-input"
                  value={editState.tags}
                  onChange={e => setEditState(p => ({ ...p, tags: e.target.value }))}
                />
              </label>
              <label className="cfg-opt-label">
                Category
                <input
                  className="cfg-opt-input"
                  value={editState.category}
                  maxLength={40}
                  onChange={e => setEditState(p => ({ ...p, category: e.target.value }))}
                />
              </label>

              <div className="admin-edit-readonly">
                <div><strong>Platform:</strong> {selected.platform}</div>
                <div><strong>Mode:</strong> {selected.mode}</div>
                <div><strong>Device:</strong> {selected.device_name ?? '—'}</div>
                <div><strong>Uploaded:</strong> {new Date(selected.uploaded_at).toLocaleString()}</div>
                <div><strong>Downloads:</strong> {selected.download_count}</div>
              </div>

              {saveError && <p className="admin-save-error">{saveError}</p>}
              {saveSuccess && <p className="admin-save-ok">Saved.</p>}

              <div className="admin-edit-actions">
                {!confirmDelete ? (
                  <>
                    <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
                      {saving ? 'Saving…' : 'Save Changes'}
                    </button>
                    <button className="btn btn-outline admin-delete-btn" onClick={() => setConfirmDelete(true)}>
                      Delete
                    </button>
                  </>
                ) : (
                  <>
                    <span className="admin-confirm-text">Delete permanently?</span>
                    <button className="btn btn-primary admin-delete-confirm" onClick={handleDelete}>Yes, delete</button>
                    <button className="btn btn-outline" onClick={() => setConfirmDelete(false)}>Cancel</button>
                  </>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
