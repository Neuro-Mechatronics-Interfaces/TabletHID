import { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import ConfigOptionsPanel from './ConfigOptionsPanel.jsx';
import DeviceFrame from './DeviceFrame.jsx';
import GamepadCanvas from './GamepadCanvas.jsx';
import DEVICE_PRESETS from './constants/devicePresets.js';

const DEFAULT_DEVICE_ID = 'pixel-tablet';
const MAX_CANVAS_H = 480;

function deepClone(obj) {
  return JSON.parse(JSON.stringify(obj));
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
  const [deviceId, setDeviceId] = useState(DEFAULT_DEVICE_ID);
  const [landscape, setLandscape] = useState(true);
  const [editLayout, setEditLayout] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [submitSuccess, setSubmitSuccess] = useState(null);

  const device = DEVICE_PRESETS.find(d => d.id === deviceId) ?? DEVICE_PRESETS[0];
  const canvasW = landscape ? device.heightDp : device.widthDp;
  const canvasH = landscape ? device.widthDp : device.heightDp;
  const canvasScale = Math.min(1, MAX_CANVAS_H / canvasH);

  useEffect(() => {
    fetch(`/api/v1/configs/${id}`)
      .then(r => { if (!r.ok) throw new Error(`${r.status}`); return r.json(); })
      .then(data => {
        setSource(data);
        setConfig(deepClone(data.config_json));
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
        fresh.buttons[key].offsetX = 0;
        fresh.buttons[key].offsetY = 0;
      }
    }
    if (fresh.leftJoystick)  { fresh.leftJoystick.offsetX  = 0; fresh.leftJoystick.offsetY  = 0; }
    if (fresh.rightJoystick) { fresh.rightJoystick.offsetX = 0; fresh.rightJoystick.offsetY = 0; }
    setConfig(fresh);
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
        config_json: config,
      };
      if (meta.description.trim()) body.description = meta.description.trim();
      if (meta.tags.trim()) body.tags = meta.tags.split(',').map(t => t.trim()).filter(Boolean);
      if (meta.category.trim()) body.category = meta.category.trim();
      if (meta.device_name.trim()) body.device_name = meta.device_name.trim();
      if (meta.device_os_version.trim()) body.device_os_version = meta.device_os_version.trim();

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
              onChange={e => setDeviceId(e.target.value)}
            >
              {DEVICE_PRESETS.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
            <button
              className={'configs-orient-btn' + (landscape ? ' active' : '')}
              onClick={() => setLandscape(l => !l)}
            >
              {landscape ? '⟷ Landscape' : '↕ Portrait'}
            </button>
            {isGamepad && (
              <>
                <button
                  className={'configs-orient-btn' + (editLayout ? ' active' : '')}
                  onClick={() => setEditLayout(m => !m)}
                >
                  {editLayout ? 'Done Editing' : 'Edit Layout'}
                </button>
                {editLayout && (
                  <button className="configs-orient-btn" onClick={resetLayout}>Reset Layout</button>
                )}
              </>
            )}
          </div>

          {editLayout && (
            <div className="clone-edit-hint">
              Drag buttons and joysticks to reposition them.
            </div>
          )}

          <div className="configs-canvas-wrap">
            <DeviceFrame device={device} landscape={landscape} maxHeight={MAX_CANVAS_H}>
              {isGamepad ? (
                <GamepadCanvas
                  canvasW={canvasW}
                  canvasH={canvasH}
                  config={config}
                  editMode={editLayout}
                  canvasScale={canvasScale}
                  onConfigChange={setConfig}
                />
              ) : (
                <div className="canvas-phase-stub">Touch Mouse canvas — coming soon</div>
              )}
            </DeviceFrame>
          </div>

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
