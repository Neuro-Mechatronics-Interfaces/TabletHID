import { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import ConfigOptionsPanel from './ConfigOptionsPanel.jsx';
import DevicePreviewEditor from './DevicePreviewEditor.jsx';
import GamepadCanvas, { getElementOffset, applyOffset, applyScale } from './GamepadCanvas.jsx';
import TouchMouseCanvas, {
  getTouchMouseElementOffset,
  applyTouchMouseOffset,
  applyTouchMouseScale,
} from './TouchMouseCanvas.jsx';
import useDevicePresets from './useDevicePresets.js';

const DEFAULT_DEVICE_ID = 'pixel-tablet';
const MAX_CANVAS_H = 480;

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

export default function CloneConfigPage() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [source, setSource] = useState(null);
  const [loadError, setLoadError] = useState(null);
  const [config, setConfig] = useState(null);
  const [meta, setMeta] = useState({
    profile_name: '', description: '', tags: '',
    category: '', platform: 'android',
    device_name: '', device_os_version: '',
  });
  const {
    devices, device, deviceId, setDeviceId, updateDevice, saveDraft, isDirty,
  } = useDevicePresets(DEFAULT_DEVICE_ID);
  const [landscape, setLandscape] = useState(true);
  const [editLayout, setEditLayout] = useState(false);
  const [selectedKey, setSelectedKey] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [submitSuccess, setSubmitSuccess] = useState(null);

  const canvasW = landscape ? device.heightDp : device.widthDp;
  const canvasH = landscape ? device.widthDp : device.heightDp;
  const canvasScale = Math.min(1, MAX_CANVAS_H / canvasH);

  useEffect(() => {
    fetch(`/api/v1/configs/${id}`)
      .then(r => { if (!r.ok) throw new Error(`${r.status}`); return r.json(); })
      .then(data => {
        setSource(data);
        const nextConfig = deepClone(data.config_json);
        const nextLandscape = inferLandscape(nextConfig, data);
        if (data.mode === 'gamepad') nextConfig.orientationPreference = nextLandscape ? 'LANDSCAPE' : 'PORTRAIT';
        setConfig(nextConfig);
        setLandscape(nextLandscape);
        setMeta({
          profile_name: `${data.profile_name} (Copy)`,
          description: data.description ?? '',
          tags: (data.tags ?? []).join(', '),
          category: data.category ?? '',
          platform: data.platform ?? 'android',
          device_name: data.device_name ?? '',
          device_os_version: data.device_os_version ?? '',
        });
      })
      .catch(e => setLoadError(e.message));
  }, [id]);

  function resetLayout() {
    if (!source) return;
    const fresh = deepClone(source.config_json);
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
    setConfig(fresh);
    setSelectedKey(null);
  }

  async function handleSubmit() {
    if (!meta.profile_name.trim()) {
      setSubmitError('Profile name is required.');
      return;
    }
    setSubmitting(true);
    setSubmitError(null);
    try {
      const body = {
        platform: meta.platform,
        mode: source.mode,
        profile_name: meta.profile_name.trim(),
        config_json: source.mode === 'gamepad' ? withOrientationPreference(config, landscape) : config,
      };
      if (meta.description.trim()) body.description = meta.description.trim();
      if (meta.tags.trim()) body.tags = meta.tags.split(',').map(t => t.trim()).filter(Boolean);
      if (meta.category.trim()) body.category = meta.category.trim();
      if (meta.device_name.trim()) body.device_name = meta.device_name.trim();
      if (meta.device_os_version.trim()) body.device_os_version = meta.device_os_version.trim();
      body.device_name = meta.device_name.trim() || device.name;
      body.device_screen_width_px = Math.round(canvasW * device.density);
      body.device_screen_height_px = Math.round(canvasH * device.density);
      body.device_screen_density_dpi = Math.round(device.density * 160);

      const res = await fetch('/api/v1/configs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error ?? `${res.status}`);
      setSubmitSuccess(data.id);
    } catch (e) {
      setSubmitError(e.message);
    } finally {
      setSubmitting(false);
    }
  }

  if (loadError) {
    return (
      <div className="clone-page">
        <div className="clone-load-error">
          <p>Failed to load config: {loadError}</p>
          <Link to="/configs" className="btn btn-outline">Back to Configs</Link>
        </div>
      </div>
    );
  }

  if (!source) {
    return <div className="clone-page"><div className="clone-loading">Loading config…</div></div>;
  }

  if (submitSuccess) {
    return (
      <div className="clone-page">
        <div className="clone-success">
          <div className="clone-success-icon">✓</div>
          <h2>Config submitted!</h2>
          <p>ID: <code>{submitSuccess}</code></p>
          <div className="clone-success-actions">
            <button className="btn btn-primary" onClick={() => navigate('/configs')}>Browse Configs</button>
            <button className="btn btn-outline" onClick={() => { setSubmitSuccess(null); }}>Edit Again</button>
          </div>
        </div>
      </div>
    );
  }

  const isGamepad = source.mode === 'gamepad';

  return (
    <div className="clone-page">
      <div className="clone-header">
        <Link to="/configs" className="clone-back">← Back to Configs</Link>
        <div className="clone-title">
          <span className="clone-title-label">Cloning</span>
          <span className="clone-title-name">{source.profile_name}</span>
        </div>
      </div>

      <div className="clone-layout">
        {/* Left: options panel */}
        <aside className="clone-sidebar">
          <ConfigOptionsPanel
            meta={meta}
            onMetaChange={setMeta}
            config={config}
            onConfigChange={setConfig}
            mode={source.mode}
          />
        </aside>

        {/* Right: canvas area */}
        <div className="clone-canvas-area">
          <div className="configs-toolbar">
            <select
              className="configs-device-picker"
              value={deviceId}
              onChange={e => {
                const nextId = e.target.value;
                setDeviceId(nextId);
                const nextDevice = devices.find(d => d.id === nextId);
                if (nextDevice) setMeta(prev => ({ ...prev, device_name: nextDevice.name }));
              }}
            >
              {devices.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
            <button
              className={'configs-orient-btn' + (landscape ? ' active' : '')}
              onClick={() => {
                setLandscape(l => {
                  const next = !l;
                  if (isGamepad) setConfig(c => withOrientationPreference(c, next));
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
              Drag to reposition. <strong>Shift+drag</strong> to resize. Click to select.
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
              {isGamepad ? (
                <GamepadCanvas
                  canvasW={canvasW}
                  canvasH={canvasH}
                  config={config}
                  editMode={editLayout}
                  canvasScale={canvasScale}
                  onConfigChange={setConfig}
                  selectedKey={editLayout ? selectedKey : null}
                  onSelect={editLayout ? setSelectedKey : undefined}
                />
              ) : (
                <TouchMouseCanvas
                  canvasW={canvasW}
                  canvasH={canvasH}
                  config={config}
                  editMode={editLayout}
                  canvasScale={canvasScale}
                  onConfigChange={setConfig}
                  selectedKey={editLayout ? selectedKey : null}
                  onSelect={editLayout ? setSelectedKey : undefined}
                />
              )}
            </DevicePreviewEditor>
          </div>

          {editLayout && selectedKey && (() => {
            const offsetApi = isGamepad
              ? { get: getElementOffset, move: applyOffset, scale: applyScale }
              : { get: getTouchMouseElementOffset, move: applyTouchMouseOffset, scale: applyTouchMouseScale };
            const { ox, oy, sx, sy } = offsetApi.get(config, selectedKey);
            const canEditNumeric = isGamepad || selectedKey.startsWith('macro:');
            return (
              <div className="elem-props-bar">
                <span className="elem-props-key">{selectedKey}</span>
                {canEditNumeric ? (
                  <>
                    <label className="elem-props-field">
                      <span>X</span>
                      <input type="number" step="1" value={Math.round(ox)}
                        onChange={e => setConfig(c => offsetApi.move(c, selectedKey, Number(e.target.value), oy))} />
                    </label>
                    <label className="elem-props-field">
                      <span>Y</span>
                      <input type="number" step="1" value={Math.round(oy)}
                        onChange={e => setConfig(c => offsetApi.move(c, selectedKey, ox, Number(e.target.value)))} />
                    </label>
                    <label className="elem-props-field">
                      <span>Scale</span>
                      <input type="number" step="0.05" min="0.2" max="4"
                        value={Number(sx.toFixed(2))}
                        onChange={e => { const v = Math.max(0.2, Math.min(4, Number(e.target.value))); setConfig(c => offsetApi.scale(c, selectedKey, v, v)); }} />
                    </label>
                    <button className="elem-props-reset" onClick={() => setConfig(c => offsetApi.scale(offsetApi.move(c, selectedKey, 0, 0), selectedKey, 1, 1))}>
                      Reset
                    </button>
                  </>
                ) : (
                  <span className="elem-props-note">Drag this zone on the preview to reposition it.</span>
                )}
              </div>
            );
          })()}

          <div className="clone-actions">
            {submitError && <p className="clone-submit-error">{submitError}</p>}
            <button className="btn btn-outline" onClick={() => navigate('/configs')}>Cancel</button>
            <button
              className="btn btn-primary"
              onClick={handleSubmit}
              disabled={submitting}
            >
              {submitting ? 'Submitting…' : 'Submit Config'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
