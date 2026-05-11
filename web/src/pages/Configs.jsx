import { useState } from 'react';
import ConfigBrowserPanel from './configs/ConfigBrowserPanel.jsx';
import DeviceFrame from './configs/DeviceFrame.jsx';
import GamepadCanvas from './configs/GamepadCanvas.jsx';
import DEVICE_PRESETS from './configs/constants/devicePresets.js';

const DEFAULT_DEVICE_ID = 'pixel-tablet';

export default function Configs() {
  const [tab, setTab] = useState('browser');
  const [selectedConfig, setSelectedConfig] = useState(null);
  const [deviceId, setDeviceId] = useState(DEFAULT_DEVICE_ID);
  const [landscape, setLandscape] = useState(true);

  const device = DEVICE_PRESETS.find(d => d.id === deviceId) ?? DEVICE_PRESETS[0];
  const canvasW = landscape ? device.heightDp : device.widthDp;
  const canvasH = landscape ? device.widthDp : device.heightDp;

  const configJson = selectedConfig?.config_json ?? null;
  const isGamepad = !selectedConfig || selectedConfig.mode === 'gamepad';

  return (
    <div className="configs-page">
      <div className="configs-hero">
        <h1>Community Configs</h1>
        <p>Browse and preview controller configs shared by the community.</p>
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
            />
          </aside>

          <div className="configs-canvas-area">
            <div className="configs-toolbar">
              <select
                className="configs-device-picker"
                value={deviceId}
                onChange={e => setDeviceId(e.target.value)}
                aria-label="Device"
              >
                {DEVICE_PRESETS.map(d => (
                  <option key={d.id} value={d.id}>{d.name}</option>
                ))}
              </select>
              <button
                className={'configs-orient-btn' + (landscape ? ' active' : '')}
                onClick={() => setLandscape(l => !l)}
                title="Toggle orientation"
              >
                {landscape ? '⟷ Landscape' : '↕ Portrait'}
              </button>
              {selectedConfig && (
                <span className="configs-config-label">{selectedConfig.profile_name}</span>
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
          </div>
        </div>
      )}
    </div>
  );
}
