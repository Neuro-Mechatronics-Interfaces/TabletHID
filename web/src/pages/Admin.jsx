import { useState, useEffect } from 'react';
import GamepadCanvas, { getElementOffset, applyOffset, applyScale } from './configs/GamepadCanvas.jsx';
import MacroEditor from './configs/MacroEditor.jsx';
import TouchMouseCanvas, {
  getTouchMouseElementOffset,
  applyTouchMouseOffset,
  applyTouchMouseScale,
} from './configs/TouchMouseCanvas.jsx';
import DevicePreviewEditor from './configs/DevicePreviewEditor.jsx';
import useDevicePresets from './configs/useDevicePresets.js';

const MAX_CANVAS_H = 400;

function deepClone(obj) {
  return JSON.parse(JSON.stringify(obj));
}

function inferLandscape(config, record) {
  const pref = config?.orientationPreference;
  if (pref === 'LANDSCAPE') return true;
  if (pref === 'PORTRAIT') return false;
  const w = record?.device_screen_width_px;
  const h = record?.device_screen_height_px;
  if (Number.isFinite(w) && Number.isFinite(h) && w !== h) return w > h;
  return true;
}

function withOrientationPreference(config, landscape) {
  if (!config) return config;
  return { ...config, orientationPreference: landscape ? 'LANDSCAPE' : 'PORTRAIT' };
}

export default function Admin() {
  const [me, setMe] = useState(null);
  const [authError, setAuthError] = useState(false);
  const [configs, setConfigs] = useState([]);
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState(null);
  const [editState, setEditState] = useState(null);
  const [configJson, setConfigJson] = useState(null);
  const [editLayout, setEditLayout] = useState(false);
  const [selectedKey, setSelectedKey] = useState(null);
  const [gridSize, setGridSize] = useState(48);
  const {
    devices, device, deviceId, setDeviceId, updateDevice, saveDraft, isDirty,
  } = useDevicePresets('pixel-tablet');
  const [landscape, setLandscape] = useState(true);
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
    const nextConfig = cfg.config_json ? deepClone(cfg.config_json) : null;
    const nextLandscape = inferLandscape(nextConfig, cfg);
    if (cfg.mode === 'gamepad' && nextConfig) nextConfig.orientationPreference = nextLandscape ? 'LANDSCAPE' : 'PORTRAIT';
    setConfigJson(nextConfig);
    setLandscape(nextLandscape);
    setEditLayout(false);
    setSelectedKey(null);
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
      if (configJson !== null) {
        body.config_json = selected.mode === 'gamepad'
          ? withOrientationPreference(configJson, landscape)
          : configJson;
      }
      if (selected.mode === 'gamepad') {
        body.device_name = device.name;
        body.device_screen_width_px = Math.round(canvasW * device.density);
        body.device_screen_height_px = Math.round(canvasH * device.density);
        body.device_screen_density_dpi = Math.round(device.density * 160);
      }
      const res = await fetch(`/api/v1/admin/configs/${selected.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error ?? `${res.status}`);
      setConfigs(prev => prev.map(c => c.id === data.id ? data : c));
      setSelected(data);
      if (data.config_json) setConfigJson(deepClone(data.config_json));
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
      setConfigJson(null);
      setEditLayout(false);
      setSelectedKey(null);
      setConfirmDelete(false);
    } else {
      const data = await res.json().catch(() => ({}));
      setSaveError(data.error ?? 'Delete failed.');
      setConfirmDelete(false);
    }
  }

  function resetLayout() {
    if (!configJson) return;
    const fresh = deepClone(configJson);
    if (fresh.buttons) {
      for (const key of Object.keys(fresh.buttons)) {
        fresh.buttons[key].offsetX = 0; fresh.buttons[key].offsetY = 0;
        fresh.buttons[key].scaleX = 1;  fresh.buttons[key].scaleY = 1;
      }
    }
    if (fresh.leftJoystick)  { fresh.leftJoystick.offsetX  = 0; fresh.leftJoystick.offsetY  = 0; fresh.leftJoystick.scaleX  = 1; fresh.leftJoystick.scaleY  = 1; }
    if (fresh.rightJoystick) { fresh.rightJoystick.offsetX = 0; fresh.rightJoystick.offsetY = 0; fresh.rightJoystick.scaleX = 1; fresh.rightJoystick.scaleY = 1; }
    if (Array.isArray(fresh.macroButtons)) {
      fresh.macroButtons = fresh.macroButtons.map(macro => ({
        ...macro,
        layoutOffsetX: 0,
        layoutOffsetY: 0,
        layoutScaleX: 1,
        layoutScaleY: 1,
      }));
    }
    setConfigJson(fresh);
    setSelectedKey(null);
  }

  const canvasW = landscape ? device.heightDp : device.widthDp;
  const canvasH = landscape ? device.widthDp : device.heightDp;
  const canvasScale = Math.min(1, MAX_CANVAS_H / canvasH);

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
            <>
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

                {configJson && (
                  <MacroEditor config={configJson} onConfigChange={setConfigJson} />
                )}
              </div>

              {configJson && (
                <div className="admin-layout-section">
                  <div className="configs-toolbar">
                    <span className="admin-layout-heading">Layout</span>
                    <select
                      className="configs-device-picker"
                      value={deviceId}
                      onChange={e => setDeviceId(e.target.value)}
                    >
                      {devices.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                    </select>
                    <button
                      className={'configs-orient-btn' + (landscape ? ' active' : '')}
                      onClick={() => {
                        setLandscape(l => {
                          const next = !l;
                          if (selected.mode === 'gamepad') setConfigJson(c => withOrientationPreference(c, next));
                          return next;
                        });
                      }}
                    >
                      {landscape ? '⟷ Landscape' : '↕ Portrait'}
                    </button>
                    <button
                      className={'configs-orient-btn' + (editLayout ? ' active' : '')}
                      onClick={() => { setEditLayout(m => !m); setSelectedKey(null); }}
                    >
                      {editLayout ? 'Done Editing' : 'Edit Layout'}
                    </button>
                    {editLayout && (
                      <button className="configs-orient-btn" onClick={resetLayout}>Reset Layout</button>
                    )}
                  </div>

                  {editLayout && (
                    <div className="clone-edit-hint">
                      <span>Drag to reposition on the device grid. <strong>Shift+drag</strong> resizes in half-grid steps; add <strong>Alt</strong> for free resize. WASD/arrows nudge selection.</span>
                      <label className="layout-grid-slider">
                        <span>Grid {gridSize}px</span>
                        <input
                          type="range"
                          min="24"
                          max="96"
                          step="4"
                          value={gridSize}
                          onChange={event => setGridSize(Number(event.target.value))}
                        />
                      </label>
                    </div>
                  )}

                  <div className="configs-canvas-wrap">
                    <DevicePreviewEditor
                      device={device}
                      landscape={landscape}
                      maxHeight={MAX_CANVAS_H}
                      isDirty={isDirty}
                      onDimensionChange={updateDevice}
                      onSaveDevice={saveDraft}
                    >
                      {selected.mode === 'gamepad' ? (
                        <GamepadCanvas
                          canvasW={canvasW}
                          canvasH={canvasH}
                          config={configJson}
                          editMode={editLayout}
                          canvasScale={canvasScale}
                          onConfigChange={setConfigJson}
                          selectedKey={editLayout ? selectedKey : null}
                          onSelect={editLayout ? setSelectedKey : undefined}
                          snapToGrid={editLayout}
                          gridSize={gridSize}
                        />
                      ) : (
                        <TouchMouseCanvas
                          canvasW={canvasW}
                          canvasH={canvasH}
                          config={configJson}
                          editMode={editLayout}
                          canvasScale={canvasScale}
                          onConfigChange={setConfigJson}
                          selectedKey={editLayout ? selectedKey : null}
                          onSelect={editLayout ? setSelectedKey : undefined}
                          snapToGrid={editLayout}
                          gridSize={gridSize}
                        />
                      )}
                    </DevicePreviewEditor>
                  </div>

                  {editLayout && selectedKey && (() => {
                    const offsetApi = selected.mode === 'gamepad'
                      ? { get: getElementOffset, move: applyOffset, scale: applyScale }
                      : { get: getTouchMouseElementOffset, move: applyTouchMouseOffset, scale: applyTouchMouseScale };
                    const { ox, oy, sx } = offsetApi.get(configJson, selectedKey);
                    const canEditNumeric = selected.mode === 'gamepad' || selectedKey.startsWith('macro:');
                    return (
                      <div className="elem-props-bar">
                        <span className="elem-props-key">{selectedKey}</span>
                        {canEditNumeric ? (
                          <>
                            <label className="elem-props-field">
                              <span>X</span>
                              <input type="number" step="1" value={Math.round(ox)}
                                onChange={e => setConfigJson(c => offsetApi.move(c, selectedKey, Number(e.target.value), oy))} />
                            </label>
                            <label className="elem-props-field">
                              <span>Y</span>
                              <input type="number" step="1" value={Math.round(oy)}
                                onChange={e => setConfigJson(c => offsetApi.move(c, selectedKey, ox, Number(e.target.value)))} />
                            </label>
                            <label className="elem-props-field">
                              <span>Scale</span>
                              <input type="number" step="0.05" min="0.2" max="4"
                                value={Number(sx.toFixed(2))}
                                onChange={e => { const v = Math.max(0.2, Math.min(4, Number(e.target.value))); setConfigJson(c => offsetApi.scale(c, selectedKey, v, v)); }} />
                            </label>
                            <button className="elem-props-reset" onClick={() => setConfigJson(c => offsetApi.scale(offsetApi.move(c, selectedKey, 0, 0), selectedKey, 1, 1))}>
                              Reset
                            </button>
                          </>
                        ) : (
                          <span className="elem-props-note">Drag this zone on the preview to reposition it.</span>
                        )}
                      </div>
                    );
                  })()}
                </div>
              )}

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
            </>
          )}
        </div>
      </div>
    </div>
  );
}
