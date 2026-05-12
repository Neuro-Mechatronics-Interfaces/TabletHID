export function gridForCanvas(canvasW, canvasH, target = 48) {
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

export function snapScaleForSize(size, naturalSize, grid, axis = 'x') {
  const halfStep = (axis === 'y' ? grid.stepY : grid.stepX) / 2;
  if (!Number.isFinite(naturalSize) || naturalSize <= 0) return 1;
  return Math.max(0.2, Math.min(4, snap(size, halfStep) / naturalSize));
}

export function keyboardGridDelta(event, grid) {
  const key = event.key.toLowerCase();
  if (key === 'arrowleft' || key === 'a') return { dx: -grid.stepX, dy: 0 };
  if (key === 'arrowright' || key === 'd') return { dx: grid.stepX, dy: 0 };
  if (key === 'arrowup' || key === 'w') return { dx: 0, dy: -grid.stepY };
  if (key === 'arrowdown' || key === 's') return { dx: 0, dy: grid.stepY };
  return null;
}

export function shouldIgnoreEditorKey(event) {
  const tag = event.target?.tagName?.toLowerCase();
  return tag === 'input' || tag === 'textarea' || tag === 'select' || event.target?.isContentEditable;
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
