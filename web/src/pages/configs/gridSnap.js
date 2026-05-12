export function gridForCanvas(canvasW, canvasH) {
  const target = 48;
  const columns = Math.max(8, Math.min(32, Math.round(canvasW / target)));
  const rows = Math.max(8, Math.min(32, Math.round(canvasH / target)));
  return {
    columns,
    rows,
    stepX: canvasW / columns,
    stepY: canvasH / rows,
  };
}

export function snap(value, step) {
  if (!Number.isFinite(value) || !Number.isFinite(step) || step <= 0) return value;
  return Math.round(value / step) * step;
}

export function snapPoint(x, y, grid) {
  return {
    x: snap(x, grid.stepX),
    y: snap(y, grid.stepY),
  };
}

export function gridOverlayStyle(grid) {
  return {
    position: 'absolute',
    inset: 0,
    pointerEvents: 'none',
    zIndex: 1,
    backgroundImage: `
      linear-gradient(rgba(255,255,255,.16) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255,255,255,.16) 1px, transparent 1px)
    `,
    backgroundSize: `${grid.stepX}px ${grid.stepY}px`,
  };
}
