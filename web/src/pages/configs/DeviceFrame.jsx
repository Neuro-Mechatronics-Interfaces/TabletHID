export default function DeviceFrame({ device, landscape = false, maxHeight = 480, children }) {
  const canvasW = landscape ? device.heightDp : device.widthDp;
  const canvasH = landscape ? device.widthDp : device.heightDp;

  const scale = Math.min(1, maxHeight / canvasH);
  const screenW = Math.round(canvasW * scale);
  const screenH = Math.round(canvasH * scale);

  const bezelPx = Math.round((device.class === 'tablet' ? 10 : 14) * scale);
  const cornerPx = Math.round((device.class === 'tablet' ? 16 : 32) * scale);

  return (
    <div style={{
      padding: bezelPx,
      borderRadius: cornerPx + bezelPx,
      background: '#1f2937',
      boxShadow: '0 8px 32px rgba(0,0,0,.45)',
      display: 'inline-block',
      flexShrink: 0,
    }}>
      <div style={{
        width: screenW,
        height: screenH,
        borderRadius: cornerPx,
        overflow: 'hidden',
        position: 'relative',
        background: '#0f172a',
      }}>
        <div style={{
          width: canvasW,
          height: canvasH,
          transform: `scale(${scale})`,
          transformOrigin: 'top left',
          position: 'absolute',
          top: 0,
          left: 0,
        }}>
          {children}
        </div>
      </div>
    </div>
  );
}
