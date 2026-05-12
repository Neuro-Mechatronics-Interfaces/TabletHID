import { useEffect, useMemo, useRef, useState } from 'react';
import { resolveLayout } from './constants/layoutResolver.js';
import {
  gridForCanvas,
  gridOverlayStyle,
  keyboardGridDelta,
  shouldIgnoreEditorKey,
  snapPoint,
  snapScaleForSize,
} from './gridSnap.js';

const BUTTON_COLORS = {
  a: '#4ade80', b: '#f87171', x: '#60a5fa', y: '#fbbf24',
  lb: '#d1d5db', rb: '#d1d5db',
  lt: '#d1d5db', rt: '#d1d5db',
  back: '#d1d5db', start: '#d1d5db',
  dpadUp: '#d1d5db', dpadDown: '#d1d5db', dpadLeft: '#d1d5db', dpadRight: '#d1d5db',
};

const DEFAULT_LABELS = {
  a: 'A', b: 'B', x: 'X', y: 'Y',
  lb: 'LB', rb: 'RB', lt: 'LT', rt: 'RT',
  back: 'Back', start: 'Start',
  dpadUp: '▲', dpadDown: '▼', dpadLeft: '◀', dpadRight: '▶',
};

const JOYSTICK_COLORS = {
  default:     { fill: 'rgba(255,255,255,0.10)', ring: 'rgba(255,255,255,0.38)' },
  outputLeft:  { fill: 'rgba(102,153,255,0.12)', ring: 'rgba(102,153,255,0.65)' },
  outputRight: { fill: 'rgba(255,170,68,0.12)',  ring: 'rgba(255,170,68,0.65)'  },
};

const BUTTON_KEYS = [
  'a', 'b', 'x', 'y', 'lb', 'rb', 'lt', 'rt',
  'back', 'start', 'dpadUp', 'dpadDown', 'dpadLeft', 'dpadRight',
];

const DRAG_THRESHOLD = 5; // px screen movement before considered a drag

function clampOffset(nat, ox, oy, sx, sy, cW, cH) {
  const halfW = (nat.w * sx) / 2;
  const halfH = (nat.h * sy) / 2;
  const natCX = nat.left + nat.w / 2;
  const natCY = nat.top + nat.h / 2;
  return {
    x: Math.max(halfW - natCX, Math.min(cW - natCX - halfW, ox)),
    y: Math.max(halfH - natCY, Math.min(cH - natCY - halfH, oy)),
  };
}

export function applyOffset(config, key, ox, oy) {
  if (key === 'leftJoystick' || key === 'rightJoystick') {
    return { ...config, [key]: { ...(config?.[key] ?? {}), offsetX: ox, offsetY: oy } };
  }
  if (key === 'singleJoystickSideBtn') {
    return { ...config, buttons: { ...(config?.buttons ?? {}), singleJoystickToggle: { ...(config?.buttons?.singleJoystickToggle ?? {}), offsetX: ox, offsetY: oy } } };
  }
  return { ...config, buttons: { ...(config?.buttons ?? {}), [key]: { ...(config?.buttons?.[key] ?? {}), offsetX: ox, offsetY: oy } } };
}

export function applyScale(config, key, sx, sy) {
  if (key === 'leftJoystick' || key === 'rightJoystick') {
    return { ...config, [key]: { ...(config?.[key] ?? {}), scaleX: sx, scaleY: sy } };
  }
  if (key === 'singleJoystickSideBtn') {
    return { ...config, buttons: { ...(config?.buttons ?? {}), singleJoystickToggle: { ...(config?.buttons?.singleJoystickToggle ?? {}), scaleX: sx, scaleY: sy } } };
  }
  return { ...config, buttons: { ...(config?.buttons ?? {}), [key]: { ...(config?.buttons?.[key] ?? {}), scaleX: sx, scaleY: sy } } };
}

export function getElementOffset(config, key) {
  if (key === 'leftJoystick')  return { ox: config?.leftJoystick?.offsetX  ?? 0, oy: config?.leftJoystick?.offsetY  ?? 0, sx: config?.leftJoystick?.scaleX  ?? 1, sy: config?.leftJoystick?.scaleY  ?? 1 };
  if (key === 'rightJoystick') return { ox: config?.rightJoystick?.offsetX ?? 0, oy: config?.rightJoystick?.offsetY ?? 0, sx: config?.rightJoystick?.scaleX ?? 1, sy: config?.rightJoystick?.scaleY ?? 1 };
  if (key === 'singleJoystickSideBtn') {
    const b = config?.buttons?.singleJoystickToggle ?? {};
    return { ox: b.offsetX ?? 0, oy: b.offsetY ?? 0, sx: b.scaleX ?? 1, sy: b.scaleY ?? 1 };
  }
  const b = config?.buttons?.[key] ?? {};
  return { ox: b.offsetX ?? 0, oy: b.offsetY ?? 0, sx: b.scaleX ?? 1, sy: b.scaleY ?? 1 };
}

export default function GamepadCanvas({
  canvasW, canvasH, config,
  editMode = false, canvasScale = 1, onConfigChange,
  selectedKey = null, onSelect,
  snapToGrid = false,
  gridSize = 48,
}) {
  const layout = useMemo(() => resolveLayout(canvasW, canvasH), [canvasW, canvasH]);
  const grid = useMemo(() => gridForCanvas(canvasW, canvasH, gridSize), [canvasW, canvasH, gridSize]);
  const dragRef = useRef(null);
  const [activeKey, setActiveKey] = useState(null);

  const isSingle = config?.singleJoystickMode ?? false;
  const sideToggle = config?.singleJoystickSideToggleEnabled ?? false;
  const outputSide = config?.singleJoystickOutputSide ?? 'LEFT';
  const customLabels = config?.customButtonLabels ?? {};

  const leftJoyColors = (isSingle && sideToggle)
    ? (outputSide === 'LEFT' ? JOYSTICK_COLORS.outputLeft : JOYSTICK_COLORS.outputRight)
    : JOYSTICK_COLORS.default;

  function startDrag(e, key) {
    if (!editMode) return;
    e.stopPropagation();
    const { ox, oy, sx, sy } = getElementOffset(config, key);
    dragRef.current = {
      key, hasMoved: false,
      startX: e.clientX, startY: e.clientY,
      startOX: ox, startOY: oy, startSX: sx, startSY: sy,
    };
    setActiveKey(key);
  }

  function handleMove(e) {
    if (!dragRef.current) return;
    const { key, startX, startY, startOX, startOY, startSX, startSY } = dragRef.current;

    const screenDX = e.clientX - startX;
    const screenDY = e.clientY - startY;
    if (!dragRef.current.hasMoved && Math.sqrt(screenDX ** 2 + screenDY ** 2) > DRAG_THRESHOLD) {
      dragRef.current.hasMoved = true;
    }
    if (!dragRef.current.hasMoved) return;

    const nat = layout[key];
    if (!nat) return;

    if (e.shiftKey) {
      // Shift+drag: uniform scale via vertical movement
      // 120px up → ×1.5; 120px down → ×0.67
      const factor = Math.pow(1.5, -screenDY / 120);
      let newScale = Math.max(0.2, Math.min(4, startSX * factor));
      if (snapToGrid && !e.altKey) {
        newScale = snapScaleForSize(nat.w * newScale, nat.w, grid, 'x');
      }
      onConfigChange?.(applyScale(config, key, newScale, newScale));
    } else {
      // Normal drag: translate
      const { sx, sy } = getElementOffset(config, key);
      const dx = screenDX / canvasScale;
      const dy = screenDY / canvasScale;
      let nextOX = startOX + dx;
      let nextOY = startOY + dy;
      if (snapToGrid) {
        const snapped = snapPoint(nat.left + nat.w / 2 + nextOX, nat.top + nat.h / 2 + nextOY, grid);
        nextOX = snapped.x - (nat.left + nat.w / 2);
        nextOY = snapped.y - (nat.top + nat.h / 2);
      }
      const { x, y } = clampOffset(nat, nextOX, nextOY, sx, sy, canvasW, canvasH);
      onConfigChange?.(applyOffset(config, key, x, y));
    }
  }

  function handleUp() {
    if (!dragRef.current) return;
    const { key, hasMoved } = dragRef.current;
    dragRef.current = null;
    setActiveKey(null);
    if (!hasMoved) {
      // Click without drag → toggle selection
      onSelect?.(selectedKey === key ? null : key);
    }
  }

  useEffect(() => {
    if (!editMode || !snapToGrid || !selectedKey) return undefined;

    function handleKeyDown(event) {
      if (shouldIgnoreEditorKey(event)) return;
      const delta = keyboardGridDelta(event, grid);
      if (!delta) return;
      const nat = layout[selectedKey];
      if (!nat) return;
      const { ox, oy, sx, sy } = getElementOffset(config, selectedKey);
      const { x, y } = clampOffset(nat, ox + delta.dx, oy + delta.dy, sx, sy, canvasW, canvasH);
      event.preventDefault();
      onConfigChange?.(applyOffset(config, selectedKey, x, y));
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [canvasH, canvasW, config, editMode, grid, layout, onConfigChange, selectedKey, snapToGrid]);

  function elementStyle(key, base) {
    const isDragging = editMode && activeKey === key;
    const isSelected = editMode && selectedKey === key && !isDragging;
    return {
      ...base,
      cursor: editMode ? (isDragging ? 'grabbing' : 'grab') : 'default',
      boxShadow: isDragging
        ? `0 0 0 2px rgba(255,255,255,0.9), 0 0 12px rgba(255,255,255,0.4)`
        : isSelected
          ? `0 0 0 2px rgba(255,255,255,0.5), 0 0 0 4px rgba(255,255,255,0.15)`
          : base.boxShadow,
      filter: isDragging ? 'brightness(1.5)' : undefined,
    };
  }

  function renderButton(key) {
    const nat = layout[key];
    if (!nat) return null;
    const btn = config?.buttons?.[key] ?? {};
    if (btn.enabled === false) return null;

    const label = customLabels[key] || DEFAULT_LABELS[key] || key.toUpperCase();
    const color = BUTTON_COLORS[key] ?? '#d1d5db';
    const isTrigger = key === 'lt' || key === 'rt';
    const isDpad = key.startsWith('dpad');
    const { ox, oy, sx, sy } = getElementOffset(config, key);

    return (
      <div
        key={key}
        onPointerDown={editMode ? (e) => startDrag(e, key) : undefined}
        style={elementStyle(key, {
          position: 'absolute',
          left: nat.left, top: nat.top, width: nat.w, height: nat.h,
          transform: `translate(${ox}px, ${oy}px) scale(${sx}, ${sy})`,
          transformOrigin: 'center center',
          background: color + '22',
          border: `2px solid ${color}99`,
          borderRadius: isTrigger ? 8 : isDpad ? 6 : 28,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: nat.w > 70 ? 12 : 10,
          fontWeight: 700, color: '#ffffff', letterSpacing: '0.02em',
          userSelect: 'none',
        })}
      >
        {label}
      </div>
    );
  }

  function renderJoystick(key, isLeft) {
    const nat = layout[key];
    if (!nat) return null;
    const joy = isLeft ? config?.leftJoystick : config?.rightJoystick;
    if (joy?.enabled === false) return null;
    if (isSingle && !isLeft) return null;

    const { ox, oy, sx, sy } = getElementOffset(config, key);
    const colors = isLeft ? leftJoyColors : JOYSTICK_COLORS.default;
    const knobSize = nat.w * 0.38;

    return (
      <div
        key={key}
        onPointerDown={editMode ? (e) => startDrag(e, key) : undefined}
        style={elementStyle(key, {
          position: 'absolute',
          left: nat.left, top: nat.top, width: nat.w, height: nat.h,
          transform: `translate(${ox}px, ${oy}px) scale(${sx}, ${sy})`,
          transformOrigin: 'center center',
        })}
      >
        <div style={{
          position: 'absolute', inset: 0, borderRadius: '50%',
          background: colors.fill, border: `2.5px solid ${colors.ring}`,
        }} />
        <div style={{
          position: 'absolute', left: '50%', top: '50%',
          width: knobSize, height: knobSize,
          transform: 'translate(-50%, -50%)',
          borderRadius: '50%', background: 'rgba(255,255,255,0.75)',
        }} />
      </div>
    );
  }

  function renderSideToggle() {
    if (!isSingle || !sideToggle) return null;
    const nat = layout['singleJoystickSideBtn'];
    if (!nat) return null;
    const key = 'singleJoystickSideBtn';
    const { ox, oy, sx, sy } = getElementOffset(config, key);

    return (
      <div
        key="sideToggle"
        onPointerDown={editMode ? (e) => startDrag(e, key) : undefined}
        style={elementStyle(key, {
          position: 'absolute',
          left: nat.left, top: nat.top, width: nat.w, height: nat.h,
          transform: `translate(${ox}px, ${oy}px) scale(${sx}, ${sy})`,
          transformOrigin: 'center center',
          background: 'rgba(255,255,255,0.10)',
          border: '2px solid rgba(255,255,255,0.40)',
          borderRadius: 6,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 13, fontWeight: 700, color: '#ffffff', userSelect: 'none',
        })}
      >
        {outputSide === 'LEFT' ? 'L' : 'R'}
      </div>
    );
  }

  return (
    <div
      style={{ position: 'relative', width: canvasW, height: canvasH }}
      onPointerMove={editMode ? handleMove : undefined}
      onPointerUp={editMode ? handleUp : undefined}
      onPointerLeave={editMode ? handleUp : undefined}
    >
      {editMode && snapToGrid && <div style={gridOverlayStyle(grid)} />}
      {BUTTON_KEYS.map(renderButton)}
      {renderJoystick('leftJoystick', true)}
      {renderJoystick('rightJoystick', false)}
      {renderSideToggle()}
    </div>
  );
}
