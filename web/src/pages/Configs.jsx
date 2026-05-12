import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ConfigBrowserPanel from './configs/ConfigBrowserPanel.jsx';
import DeviceFrame from './configs/DeviceFrame.jsx';
import GamepadCanvas from './configs/GamepadCanvas.jsx';
import DEVICE_PRESETS from './configs/constants/devicePresets.js';

const DEFAULT_DEVICE_ID = 'pixel-tablet';
const toTitleCase = s => s.replace(/\b\w/g, c => c.toUpperCase());

export default function Configs() {
  const navigate = useNavigate();
  const [tab, setTab] = useState('browser');
  const [selectedConfig, setSelectedConfig] = useState(null);
  const [activeTag, setActiveTag] = useState('');
  const [deviceId, setDeviceId] = useState(
    () => localStorage.getItem('configs:deviceId') ?? DEFAULT_DEVICE_ID,
  );
  const [landscape, setLandscape] = useState(
    () => localStorage.getItem('configs:landscape') !== 'false',
  );

  const device = DEVICE_PRESETS.find(d => d.id === deviceId) ?? DEVICE_PRESETS[0];
  const canvasW = landscape ? device.heightDp : device.widthDp;
  const canvasH = landscape ? device.widthDp : device.heightDp;

  const configJson = selectedConfig?.config_json ?? null;
  const isGamepad = !selectedConfig || selectedConfig.mode === 'gamepad';

  const configTags = (selectedConfig?.tags ?? []).map(t => t.toLowerCase().trim()).filter(Boolean);

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
        <button className="configs-tab" disabled title="Coming in a future update">
          Graph <span className="configs-tab-soon">Soon</span>
        </button>
      </div>

      {tab === 'browser' && (
        <div className="configs-layout">
          <aside className="configs-sidebar">
            <ConfigBrowserPanel
              onSelect={setSelectedConfig}
              selectedId={selectedConfig?.id}
              activeTag={activeTag}
              onTagSelect={setActiveTag}
            />
          </aside>

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
                {DEVICE_PRESETS.map(d => (
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
                <DeviceFrame device={device} landscape={landscape} maxHeight={500}>
                  {isGamepad ? (
                    <GamepadCanvas canvasW={canvasW} canvasH={canvasH} config={configJson} />
                  ) : (
                    <div className="canvas-phase-stub">
                      Touch Mouse canvas — coming soon
                    </div>
                  )}
                </DeviceFrame>
              ) : (
                <div className="canvas-empty">
                  <div className="canvas-empty-icon">🎮</div>
                  <p>Select a config from the list to preview it.</p>
                </div>
              )}
            </div>

            {selectedConfig && (
              <div className="configs-meta">
                <span className={'configs-meta-badge mode-' + selectedConfig.mode}>
                  {selectedConfig.mode === 'gamepad' ? 'Gamepad' : 'Touch Mouse'}
                </span>
                {selectedConfig.platform && (
                  <span className={'configs-meta-badge platform-' + selectedConfig.platform}>
                    {selectedConfig.platform === 'ios' ? 'iOS' : 'Android'}
                  </span>
                )}
                {selectedConfig.category && (
                  <span className="configs-meta-badge category">
                    {toTitleCase(selectedConfig.category.trim())}
                  </span>
                )}
                {configTags.map(t => (
                  <button
                    key={t}
                    className={'configs-meta-tag' + (activeTag === t ? ' active' : '')}
                    onClick={() => setActiveTag(activeTag === t ? '' : t)}
                    title={activeTag === t ? 'Clear tag filter' : `Filter by "${t}"`}
                  >
                    {t}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
