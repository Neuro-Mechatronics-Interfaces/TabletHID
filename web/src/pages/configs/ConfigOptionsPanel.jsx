import MacroEditor from './MacroEditor.jsx';

const BUTTON_KEYS = [
  ['a', 'A'], ['b', 'B'], ['x', 'X'], ['y', 'Y'],
  ['lb', 'LB'], ['rb', 'RB'], ['lt', 'LT'], ['rt', 'RT'],
  ['back', 'Back'], ['start', 'Start'],
  ['dpadUp', 'D-Up'], ['dpadDown', 'D-Dn'], ['dpadLeft', 'D-Lt'], ['dpadRight', 'D-Rt'],
];

export default function ConfigOptionsPanel({ meta, onMetaChange, config, onConfigChange, mode }) {
  function setMeta(key, val) {
    onMetaChange(prev => ({ ...prev, [key]: val }));
  }

  function setBtn(key, field, val) {
    onConfigChange(prev => ({
      ...prev,
      buttons: { ...(prev?.buttons ?? {}), [key]: { ...(prev?.buttons?.[key] ?? {}), [field]: val } },
    }));
  }

  function setJoy(side, field, val) {
    const joyKey = side + 'Joystick';
    onConfigChange(prev => ({
      ...prev,
      [joyKey]: { ...(prev?.[joyKey] ?? {}), [field]: val },
    }));
  }

  function setGlobal(field, val) {
    onConfigChange(prev => ({ ...prev, [field]: val }));
  }

  function setTouchButton(key, field, val) {
    onConfigChange(prev => ({
      ...prev,
      [key]: { ...(prev?.[key] ?? {}), [field]: val },
    }));
  }

  function setSniper(field, val) {
    onConfigChange(prev => ({
      ...prev,
      sniper: { ...(prev?.sniper ?? {}), [field]: val },
    }));
  }

  const isGamepad = mode === 'gamepad';

  return (
    <div className="cfg-options-panel">
      {/* ── Metadata ── */}
      <section className="cfg-opt-section">
        <h3 className="cfg-opt-heading">Profile Info</h3>
        <label className="cfg-opt-label">
          Name <span className="cfg-opt-req">*</span>
          <input
            className="cfg-opt-input"
            value={meta.profile_name}
            maxLength={80}
            onChange={e => setMeta('profile_name', e.target.value)}
            placeholder="My Custom Config"
          />
        </label>
        <label className="cfg-opt-label">
          Description
          <textarea
            className="cfg-opt-input cfg-opt-textarea"
            value={meta.description}
            maxLength={400}
            rows={3}
            onChange={e => setMeta('description', e.target.value)}
            placeholder="Optional description…"
          />
        </label>
        <label className="cfg-opt-label">
          Tags <span className="cfg-opt-hint">(comma-separated)</span>
          <input
            className="cfg-opt-input"
            value={meta.tags}
            onChange={e => setMeta('tags', e.target.value)}
            placeholder="gaming, fps, custom"
          />
        </label>
        <label className="cfg-opt-label">
          Category
          <input
            className="cfg-opt-input"
            value={meta.category}
            maxLength={40}
            onChange={e => setMeta('category', e.target.value)}
            placeholder="gaming"
          />
        </label>
        <label className="cfg-opt-label">
          Platform
          <select className="cfg-opt-input" value={meta.platform} onChange={e => setMeta('platform', e.target.value)}>
            <option value="android">Android</option>
            <option value="ios">iOS</option>
          </select>
        </label>
        <label className="cfg-opt-label">
          Device
          <input
            className="cfg-opt-input"
            value={meta.device_name}
            maxLength={80}
            onChange={e => setMeta('device_name', e.target.value)}
            placeholder="e.g. Pixel Tablet"
          />
        </label>
        <label className="cfg-opt-label">
          OS Version
          <input
            className="cfg-opt-input"
            value={meta.device_os_version}
            maxLength={20}
            onChange={e => setMeta('device_os_version', e.target.value)}
            placeholder="e.g. 15"
          />
        </label>
        <p className="cfg-opt-hint">
          Shared configs are public. Do not include personal information or inappropriate language in text fields.
        </p>
      </section>

      {isGamepad && config && (
        <>
          {/* ── Buttons ── */}
          <section className="cfg-opt-section">
            <h3 className="cfg-opt-heading">Buttons</h3>
            <div className="cfg-btn-grid">
              {BUTTON_KEYS.map(([key, label]) => {
                const enabled = config?.buttons?.[key]?.enabled !== false;
                return (
                  <label key={key} className="cfg-btn-toggle">
                    <input
                      type="checkbox"
                      checked={enabled}
                      onChange={e => setBtn(key, 'enabled', e.target.checked)}
                    />
                    <span>{label}</span>
                  </label>
                );
              })}
            </div>
          </section>

          {/* ── Joysticks ── */}
          <section className="cfg-opt-section">
            <h3 className="cfg-opt-heading">Joysticks</h3>
            <label className="cfg-btn-toggle cfg-opt-row">
              <input
                type="checkbox"
                checked={config?.leftJoystick?.enabled !== false}
                onChange={e => setJoy('left', 'enabled', e.target.checked)}
              />
              <span>Left Joystick</span>
            </label>
            <label className="cfg-btn-toggle cfg-opt-row">
              <input
                type="checkbox"
                checked={!config?.singleJoystickMode && config?.rightJoystick?.enabled !== false}
                disabled={config?.singleJoystickMode}
                onChange={e => setJoy('right', 'enabled', e.target.checked)}
              />
              <span>Right Joystick</span>
            </label>
            <label className="cfg-btn-toggle cfg-opt-row">
              <input
                type="checkbox"
                checked={config?.singleJoystickMode ?? false}
                onChange={e => setGlobal('singleJoystickMode', e.target.checked)}
              />
              <span>Single Joystick Mode</span>
            </label>
            {config?.singleJoystickMode && (
              <label className="cfg-btn-toggle cfg-opt-row" style={{ paddingLeft: 20 }}>
                <input
                  type="checkbox"
                  checked={config?.singleJoystickSideToggleEnabled ?? false}
                  onChange={e => setGlobal('singleJoystickSideToggleEnabled', e.target.checked)}
                />
                <span>Side Toggle Button</span>
              </label>
            )}
          </section>

          {/* ── Vibration ── */}
          <section className="cfg-opt-section">
            <h3 className="cfg-opt-heading">Vibration</h3>
            <div className="cfg-chips">
              {['OFF', 'LIGHT', 'MEDIUM', 'STRONG'].map(v => (
                <button
                  key={v}
                  className={'cfg-chip' + (config?.vibrationIntensity === v ? ' active' : '')}
                  onClick={() => setGlobal('vibrationIntensity', v)}
                >
                  {v[0] + v.slice(1).toLowerCase()}
                </button>
              ))}
            </div>
          </section>

          <MacroEditor config={config} onConfigChange={onConfigChange} />
        </>
      )}

      {!isGamepad && config && (
        <>
          <section className="cfg-opt-section">
            <h3 className="cfg-opt-heading">Touch Mouse</h3>
            <div className="cfg-chips">
              {['MOUSE', 'TOUCH'].map(v => (
                <button
                  key={v}
                  className={'cfg-chip' + (config?.mode === v ? ' active' : '')}
                  onClick={() => setGlobal('mode', v)}
                >
                  {v === 'MOUSE' ? 'Mouse' : 'Touch'}
                </button>
              ))}
            </div>
            <label className="cfg-opt-label">
              Sensitivity
              <input
                className="cfg-opt-input"
                type="number"
                min="1"
                max="10"
                value={config?.sensitivity ?? 5}
                onChange={e => setGlobal('sensitivity', Math.max(1, Math.min(10, Number(e.target.value))))}
              />
            </label>
            <label className="cfg-btn-toggle cfg-opt-row">
              <input
                type="checkbox"
                checked={config?.scrollEnabled ?? true}
                onChange={e => setGlobal('scrollEnabled', e.target.checked)}
              />
              <span>Scroll Enabled</span>
            </label>
            <label className="cfg-btn-toggle cfg-opt-row">
              <input
                type="checkbox"
                checked={config?.invertScroll ?? false}
                onChange={e => setGlobal('invertScroll', e.target.checked)}
              />
              <span>Invert Scroll</span>
            </label>
          </section>

          <section className="cfg-opt-section">
            <h3 className="cfg-opt-heading">Mouse Buttons</h3>
            {[
              ['leftButton', 'Left Button'],
              ['rightButton', 'Right Button'],
            ].map(([key, label]) => (
              <div key={key} className="cfg-touch-button-block">
                <label className="cfg-btn-toggle cfg-opt-row">
                  <input
                    type="checkbox"
                    checked={config?.[key]?.enabled ?? false}
                    onChange={e => setTouchButton(key, 'enabled', e.target.checked)}
                  />
                  <span>{label}</span>
                </label>
                <div className="cfg-chips">
                  {['STATIC', 'DYNAMIC'].map(v => (
                    <button
                      key={v}
                      className={'cfg-chip' + (config?.[key]?.zoneType === v ? ' active' : '')}
                      onClick={() => setTouchButton(key, 'zoneType', v)}
                    >
                      {v[0] + v.slice(1).toLowerCase()}
                    </button>
                  ))}
                </div>
              </div>
            ))}
            <p className="cfg-opt-hint">
              Use Edit Layout on the preview to drag static zones, dynamic zones, and macro buttons.
            </p>
          </section>

          <section className="cfg-opt-section">
            <h3 className="cfg-opt-heading">Sniper Zone</h3>
            <label className="cfg-btn-toggle cfg-opt-row">
              <input
                type="checkbox"
                checked={config?.sniper?.enabled ?? false}
                onChange={e => setSniper('enabled', e.target.checked)}
              />
              <span>Enable Sniper Zone</span>
            </label>
            <label className="cfg-opt-label">
              Divisor
              <input
                className="cfg-opt-input"
                type="number"
                min="1"
                max="12"
                step="0.5"
                value={config?.sniper?.divisor ?? 3}
                onChange={e => setSniper('divisor', Math.max(1, Math.min(12, Number(e.target.value))))}
              />
            </label>
          </section>

          <MacroEditor config={config} onConfigChange={onConfigChange} />
        </>
      )}
    </div>
  );
}
