export default function ConfigCard({ config, isActive, onClick }) {
  const {
    profile_name, device_name, device_os_version, platform,
    device_screen_diagonal_in, mode, download_count,
  } = config;

  const modeLabel = mode === 'gamepad' ? 'Gamepad' : 'Touch Mouse';
  const platformLabel = platform === 'ios' ? 'iOS' : 'Android';

  return (
    <div className={'cfg-card' + (isActive ? ' active' : '')} onClick={onClick} role="button" tabIndex={0}
      onKeyDown={e => e.key === 'Enter' && onClick()}>
      <div className="cfg-card-header">
        <span className="cfg-card-name">{profile_name}</span>
        <span className={'cfg-card-mode-badge ' + mode}>{modeLabel}</span>
      </div>
      {device_name && (
        <div className="cfg-card-device">
          {device_name}
          {device_os_version && <span className="cfg-card-os"> · {platformLabel} {device_os_version}</span>}
          {device_screen_diagonal_in != null && (
            <span className="cfg-card-diag"> · {device_screen_diagonal_in.toFixed(1)}"</span>
          )}
        </div>
      )}
      <div className="cfg-card-footer">
        <span className="cfg-card-platform">{platformLabel}</span>
        {download_count > 0 && (
          <span className="cfg-card-dl">{download_count.toLocaleString()} dl</span>
        )}
      </div>
    </div>
  );
}
