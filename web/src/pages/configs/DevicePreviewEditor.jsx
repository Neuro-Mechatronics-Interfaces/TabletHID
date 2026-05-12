import DeviceFrame from './DeviceFrame.jsx';

function clampInt(value, min, max) {
  const n = Number.parseInt(value, 10);
  if (!Number.isFinite(n)) return min;
  return Math.max(min, Math.min(max, n));
}

export default function DevicePreviewEditor({
  device,
  landscape,
  maxHeight,
  isDirty,
  onDimensionChange,
  onSaveDevice,
  children,
}) {
  const renderedWidth = landscape ? device.heightDp : device.widthDp;
  const renderedHeight = landscape ? device.widthDp : device.heightDp;

  function setRenderedWidth(value) {
    const next = clampInt(value, 120, 3000);
    onDimensionChange(landscape ? { heightDp: next } : { widthDp: next });
  }

  function setRenderedHeight(value) {
    const next = clampInt(value, 120, 3000);
    onDimensionChange(landscape ? { widthDp: next } : { heightDp: next });
  }

  function handleSave() {
    const name = window.prompt('Name this device preset:', `${device.name} Custom`);
    if (!name?.trim()) return;
    onSaveDevice(name.trim());
  }

  return (
    <div className="device-editor">
      <label className="device-dim device-dim-width">
        <span>Width</span>
        <input
          type="number"
          min="120"
          max="3000"
          step="1"
          value={Math.round(renderedWidth)}
          onChange={e => setRenderedWidth(e.target.value)}
        />
      </label>

      <div className="device-frame-row">
        <DeviceFrame device={device} landscape={landscape} maxHeight={maxHeight}>
          {children}
        </DeviceFrame>
        <label className="device-dim device-dim-height">
          <span>Height</span>
          <input
            type="number"
            min="120"
            max="3000"
            step="1"
            value={Math.round(renderedHeight)}
            onChange={e => setRenderedHeight(e.target.value)}
          />
        </label>
      </div>

      {isDirty && (
        <button className="device-save-btn" type="button" onClick={handleSave}>
          Save Device
        </button>
      )}
    </div>
  );
}
