import { useMemo, useRef, useState } from 'react';
import { resolveLayout } from './constants/layoutResolver.js';

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

function applyOffset(config, key, ox, oy) {
  if (key === 'leftJoystick' || key === 'rightJoystick') {
    return { ...config, [key]: { ...(config?.[key] ?? {}), offsetX: ox, offsetY: oy } };
  }
  if (key === 'singleJoystickSideBtn') {
    return {
      ...config,
      buttons: {
        ...(config?.buttons ?? {}),
        singleJoystickToggle: { ...(config?.buttons?.singleJoystickToggle ?? {}), offsetX: ox, offsetY: oy },
      },
    };
  }
  return {
    ...config,
    buttons: {
      ...(config?.buttons ?? {}),
      [key]: { ...(config?.buttons?.[key] ?? {}), offsetX: ox, offsetY: oy },
    },
  };
}

export default function GamepadCanvas({
  canvasW, canvasH, config,
  editMode = false, canvasScale = 1, onConfigChange,
}) {
  const layout = useMemo(() => resolveLayout(canvasW, canvasH), [canvasW, canvasH]);
  const dragRef = useRef(null);
  const [activeKey, setActiveKey] = useState(null);

  const isSingle = config?.singleJoystickMode ?? false;
  const sideToggle = config?.singleJoystickSideToggleEnabled ?? false;
  const outputSide = config?.singleJoystickOutputSide ?? 'LEFT';
  const customLabels = config?.customButtonLabels ?? {};

  const leftJoyColors = (isSingle && sideToggle)
    ? (outputSide === 'LEFT' ? JOYSTICK_COLORS.outputLeft : JOYSTICK_COLORS.outputRight)
    : JOYSTICK_COLORS.default;

  function getOffset(key) {
    if (key === 'leftJoystick')  return { ox: config?.leftJoystick?.offsetX  ?? 0, oy: config?.leftJoystick?.offsetY  ?? 0, sx: config?.leftJoystick?.scaleX  ?? 1, sy: config?.leftJoystick?.scaleY  ?? 1 };
    if (key === 'rightJoystick') return { ox: config?.rightJoystick?.offsetX ?? 0, oy: config?.rightJoystick?.offsetY ?? 0, sx: config?.rightJoystick?.scaleX ?? 1, sy: config?.rightJoystick?.scaleY ?? 1 };
    if (key === 'singleJoystickSideBtn') return { ox: config?.buttons?.singleJoystickToggle?.offsetX ?? 0, oy: config?.buttons?.singleJoystickToggle?.offsetY ?? 0, sx: 1, sy: 1 };
    const b = config?.buttons?.[key] ?? {};
    return { ox: b.offsetX ?? 0, oy: b.offsetY ?? 0, sx: b.scaleX ?? 1, sy: b.scaleY ?? 1 };
  }

  function startDrag(e, key) {
    if (!editMode) return;
    e.stopPropagation();
    const { ox, oy } = getOffset(key);
    dragRef.current = { key, startX: e.clientX, startY: e.clientY, startOX: ox, startOY: oy };
    setActiveKey(key);
  }

  function handleMove(e) {
    if (!dragRef.current) return;
    const { key, startX, startY, startOX, startOY } = dragRef.current;
    const nat = layout[key];
    if (!nat) return;
    const { sx, sy } = getOffset(key);
    const dx = (e.clientX - startX) / canvasScale;
    const dy = (e.clientY - startY) / canvasScale;
    const { x, y } = clampOffset(nat, startOX + dx, startOY + dy, sx, sy, canvasW, canvasH);
    onConfigChange?.(applyOffset(config, key, x, y));
  }

  function handleUp() {
    dragRef.current = null;
    setActiveKey(null);
  }

  function editProps(key) {
    if (!editMode) return {};
    return {
      onPointerDown: (e) => startDrag(e, key),
      style_extra: { cursor: activeKey === key ? 'grabbing' : 'grab', filter: activeKey === key ? 'brightness(1.5)' : undefined },
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
    const { ox, oy, sx, sy } = getOffset(key);
    const isActive = editMode && activeKey === key;

    return (
      <div
        key={key}
        onPointerDown={editMode ? (e) => startDrag(e, key) : undefined}
        style={{
          position: 'absolute',
          left: nat.left,
          top: nat.top,
          width: nat.w,
          height: nat.h,
          transform: `translate(${ox}px, ${oy}px) scale(${sx}, ${sy})`,
          transformOrigin: 'center center',
          background: color + '22',
          border: `2px solid ${isActive ? color : color + '99'}`,
          borderRadius: isTrigger ? 8 : isDpad ? 6 : 28,
          boxShadow: isActive ? `0 0 0 2px ${color}66` : undefined,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: nat.w > 70 ? 12 : 10,
          fontWeight: 700, color: '#ffffff', letterSpacing: '0.02em',
          userSelect: 'none',
          cursor: editMode ? (isActive ? 'grabbing' : 'grab') : 'default',
        }}
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

    const { ox, oy, sx, sy } = getOffset(key);
    const colors = isLeft ? leftJoyColors : JOYSTICK_COLORS.default;
    const knobSize = nat.w * 0.38;
    const isActive = editMode && activeKey === key;

    return (
      <div
        key={key}
        onPointerDown={editMode ? (e) => startDrag(e, key) : undefined}
        style={{
          position: 'absolute',
          left: nat.left, top: nat.top,
          width: nat.w, height: nat.h,
          transform: `translate(${ox}px, ${oy}px) scale(${sx}, ${sy})`,
          transformOrigin: 'center center',
          cursor: editMode ? (isActive ? 'grabbing' : 'grab') : 'default',
        }}
      >
        <div style={{
          position: 'absolute', inset: 0, borderRadius: '50%',
          background: colors.fill,
          border: `2.5px solid ${isActive ? colors.ring.replace(')', ', 1.0)').replace('rgba', 'rgba') : colors.ring}`,
          boxShadow: isActive ? `0 0 0 2px ${colors.ring}` : undefined,
        }} />
        <div style={{
          position: 'absolute', left: '50%', top: '50%',
          width: knobSize, height: knobSize,
          transform: 'translate(-50%, -50%)',
          borderRadius: '50%',
          background: 'rgba(255,255,255,0.75)',
        }} />
      </div>
    );
  }

  function renderSideToggle() {
    if (!isSingle || !sideToggle) return null;
    const nat = layout['singleJoystickSideBtn'];
    if (!nat) return null;
    const key = 'singleJoystickSideBtn';
    const { ox, oy, sx, sy } = getOffset(key);
    const isActive = editMode && activeKey === key;

    return (
      <div
        key="sideToggle"
        onPointerDown={editMode ? (e) => startDrag(e, key) : undefined}
        style={{
          position: 'absolute',
          left: nat.left, top: nat.top, width: nat.w, height: nat.h,
          transform: `translate(${ox}px, ${oy}px) scale(${sx}, ${sy})`,
          transformOrigin: 'center center',
          background: 'rgba(255,255,255,0.10)',
          border: '2px solid rgba(255,255,255,0.40)',
          boxShadow: isActive ? '0 0 0 2px rgba(255,255,255,0.6)' : undefined,
          borderRadius: 6,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 13, fontWeight: 700, color: '#ffffff',
          userSelect: 'none',
          cursor: editMode ? (isActive ? 'grabbing' : 'grab') : 'default',
        }}
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
      {BUTTON_KEYS.map(renderButton)}
      {renderJoystick('leftJoystick', true)}
      {renderJoystick('rightJoystick', false)}
      {renderSideToggle()}
    </div>
  );
}
