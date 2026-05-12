import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ConfigBrowserPanel from './configs/ConfigBrowserPanel.jsx';
import ConfigGraphPanel from './configs/ConfigGraphPanel.jsx';
import DevicePreviewEditor from './configs/DevicePreviewEditor.jsx';
import GamepadCanvas from './configs/GamepadCanvas.jsx';
import TouchMouseCanvas from './configs/TouchMouseCanvas.jsx';
import useDevicePresets from './configs/useDevicePresets.js';

const DEFAULT_DEVICE_ID = 'pixel-tablet';
const SELECTED_CONFIG_KEY = 'configs:selectedConfigId';

function norm(value) {
  return String(value ?? '').toLowerCase().replace(/[^a-z0-9]+/g, '');
}

function diagonalForPreset(device) {
  return Math.sqrt(device.widthDp ** 2 + device.heightDp ** 2) / 160;
}

function inferLandscapeFromConfig(config) {
  const pref = config?.config_json?.orientationPreference;
  if (pref === 'LANDSCAPE') return true;
  if (pref === 'PORTRAIT') return false;

  const w = config?.device_screen_width_px;
  const h = config?.device_screen_height_px;
  if (Number.isFinite(w) && Number.isFinite(h) && w > 0 && h > 0 && w !== h) {
    return w > h;
  }
  return null;
}

function matchDevicePreset(config, devices) {
  if (!config) return null;

  const deviceName = norm(config.device_name);
  if (deviceName) {
    const byName = devices.find(device => {
      const presetName = norm(device.name);
      return deviceName.includes(presetName) || presetName.includes(deviceName);
    });
    if (byName) return byName;
  }

  const w = config.device_screen_width_px;
  const h = config.device_screen_height_px;
  const dpi = config.device_screen_density_dpi;
  const diag = config.device_screen_diagonal_in;

  if (Number.isFinite(w) && Number.isFinite(h) && w > 0 && h > 0) {
    const observedShort = Math.min(w, h);
    const observedLong = Math.max(w, h);
    let best = null;

    for (const device of devices) {
      const presetW = device.widthDp * device.density;
      const presetH = device.heightDp * device.density;
      const presetShort = Math.min(presetW, presetH);
      const presetLong = Math.max(presetW, presetH);
      const dimensionScore =
        Math.abs(observedShort - presetShort) / Math.max(observedShort, presetShort) +
        Math.abs(observedLong - presetLong) / Math.max(observedLong, presetLong);
      const densityScore = Number.isFinite(dpi)
        ? Math.abs(dpi - device.density * 160) / Math.max(dpi, device.density * 160)
        : 0;
      const diagonalScore = Number.isFinite(diag)
        ? Math.abs(diag - diagonalForPreset(device)) / Math.max(diag, diagonalForPreset(device))
        : 0;
      const score = dimensionScore + densityScore * 0.65 + diagonalScore * 0.5;
      if (!best || score < best.score) best = { device, score };
    }

    if (best && best.score < 0.7) return best.device;
  }

  if (Number.isFinite(diag) && diag > 0) {
    return devices
      .map(device => ({ device, score: Math.abs(diag - diagonalForPreset(device)) }))
      .sort((a, b) => a.score - b.score)[0]?.device ?? null;
  }

  return null;
}

export default function Configs() {
  const navigate = useNavigate();
  const [tab, setTab] = useState('browser');
  const [selectedConfig, setSelectedConfig] = useState(null);
  const [selectedConfigId, setSelectedConfigId] = useState(
    () => localStorage.getItem(SELECTED_CONFIG_KEY) ?? '',
  );
  const [activeTag, setActiveTag] = useState('');
  const initialDeviceId = localStorage.getItem('configs:deviceId') ?? DEFAULT_DEVICE_ID;
  const {
    devices, device, deviceId, setDeviceId, updateDevice, saveDraft, isDirty, loaded: devicesLoaded,
  } = useDevicePresets(initialDeviceId);
  const [landscape, setLandscape] = useState(
    () => localStorage.getItem('configs:landscape') !== 'false',
  );
  const orientationSyncedIdRef = useRef('');
  const deviceSyncedIdRef = useRef('');
  const canvasW = landscape ? device.heightDp : device.widthDp;
  const canvasH = landscape ? device.widthDp : device.heightDp;

  const configJson = selectedConfig?.config_json ?? null;
  const isGamepad = !selectedConfig || selectedConfig.mode === 'gamepad';

  function handleSelectConfig(config) {
    setSelectedConfig(config);
    setSelectedConfigId(config?.id ?? '');
    if (config?.id) localStorage.setItem(SELECTED_CONFIG_KEY, config.id);
    else localStorage.removeItem(SELECTED_CONFIG_KEY);
  }

  useEffect(() => {
    if (!selectedConfig) return;
    if (orientationSyncedIdRef.current === selectedConfig.id) return;
    orientationSyncedIdRef.current = selectedConfig.id;
    const matchedLandscape = inferLandscapeFromConfig(selectedConfig);
    if (matchedLandscape !== null) {
      setLandscape(matchedLandscape);
      localStorage.setItem('configs:landscape', String(matchedLandscape));
    }
  }, [selectedConfig]);

  useEffect(() => {
    if (!selectedConfig || !devicesLoaded) return;
    if (deviceSyncedIdRef.current === selectedConfig.id) return;

    const matchedDevice = matchDevicePreset(selectedConfig, devices);
    if (!matchedDevice) return;

    deviceSyncedIdRef.current = selectedConfig.id;
    setDeviceId(matchedDevice.id);
    localStorage.setItem('configs:deviceId', matchedDevice.id);
  }, [selectedConfig, devices, devicesLoaded, setDeviceId]);

  return (
    <div className="configs-page">
      <div className="configs-hero">
        <h1>Community Configs</h1>
        <p>Browse and preview controller configs. User-generated content.</p>
      </div>

      <div className="configs-tab-bar">
        <button
          className={'configs-tab' + (tab === 'browser' ? ' active' : '')}
          onClick={() => setTab('browser')}
        >
          Browser
        </button>
        <button
          className={'configs-tab' + (tab === 'graph' ? ' active' : '')}
          onClick={() => setTab('graph')}
        >
          Graph
        </button>
      </div>

      <div className="configs-layout">
        <aside className="configs-sidebar">
          <ConfigBrowserPanel
            onSelect={handleSelectConfig}
            selectedId={selectedConfig?.id ?? selectedConfigId}
            selectedConfig={selectedConfig}
            activeTag={activeTag}
            onTagSelect={setActiveTag}
          />
        </aside>

        {tab === 'browser' ? (
          <div className="configs-canvas-area">
            <div className="configs-toolbar">
              <select
                className="configs-device-picker"
                value={deviceId}
                onChange={e => {
                  setDeviceId(e.target.value);
                  localStorage.setItem('configs:deviceId', e.target.value);
                }}
                aria-label="Device"
              >
                {devices.map(d => (
                  <option key={d.id} value={d.id}>{d.name}</option>
                ))}
              </select>
              <button
                className={'configs-orient-btn' + (landscape ? ' active' : '')}
                onClick={() => setLandscape(l => {
                  localStorage.setItem('configs:landscape', String(!l));
                  return !l;
                })}
                title="Toggle orientation"
              >
                {landscape ? '⟷ Landscape' : '↕ Portrait'}
              </button>
              {selectedConfig && (
                <>
                  <span className="configs-config-label">{selectedConfig.profile_name}</span>
                  <button
                    className="btn btn-primary configs-clone-btn"
                    onClick={() => navigate(`/configs/clone/${selectedConfig.id}`)}
                  >
                    Clone &amp; Edit
                  </button>
                </>
              )}
            </div>

            <div className="configs-canvas-wrap">
              {selectedConfig ? (
                <DevicePreviewEditor
                  device={device}
                  landscape={landscape}
                  maxHeight={500}
                  isDirty={isDirty}
                  onDimensionChange={updateDevice}
                  onSaveDevice={saveDraft}
                >
                  {isGamepad ? (
                    <GamepadCanvas canvasW={canvasW} canvasH={canvasH} config={configJson} />
                  ) : (
                    <TouchMouseCanvas canvasW={canvasW} canvasH={canvasH} config={configJson} />
                  )}
                </DevicePreviewEditor>
              ) : (
                <div className="canvas-empty">
                  <div className="canvas-empty-icon">🎮</div>
                  <p>Select a config from the list to preview it.</p>
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="configs-canvas-area">
            <ConfigGraphPanel selectedConfig={selectedConfig} onSelect={handleSelectConfig} />
          </div>
        )}
      </div>
    </div>
  );
}
