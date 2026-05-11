import { useMemo } from 'react';
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
  default:      { fill: 'rgba(255,255,255,0.10)', ring: 'rgba(255,255,255,0.38)' },
  outputLeft:   { fill: 'rgba(102,153,255,0.12)', ring: 'rgba(102,153,255,0.65)' },
  outputRight:  { fill: 'rgba(255,170,68,0.12)',  ring: 'rgba(255,170,68,0.65)'  },
};

const BUTTON_KEYS = [
  'a', 'b', 'x', 'y', 'lb', 'rb', 'lt', 'rt',
  'back', 'start', 'dpadUp', 'dpadDown', 'dpadLeft', 'dpadRight',
];

export default function GamepadCanvas({ canvasW, canvasH, config }) {
  const layout = useMemo(() => resolveLayout(canvasW, canvasH), [canvasW, canvasH]);

  const isSingle = config?.singleJoystickMode ?? false;
  const sideToggle = config?.singleJoystickSideToggleEnabled ?? false;
  const outputSide = config?.singleJoystickOutputSide ?? 'LEFT';
  const customLabels = config?.customButtonLabels ?? {};

  const leftJoyColors = (isSingle && sideToggle)
    ? (outputSide === 'LEFT' ? JOYSTICK_COLORS.outputLeft : JOYSTICK_COLORS.outputRight)
    : JOYSTICK_COLORS.default;

  function getBtn(key) {
    return config?.buttons?.[key] ?? {};
  }

  function renderButton(key) {
    const nat = layout[key];
    if (!nat) return null;
    const btn = getBtn(key);
    if (btn.enabled === false) return null;

    const label = customLabels[key] || DEFAULT_LABELS[key] || key.toUpperCase();
    const color = BUTTON_COLORS[key] ?? '#d1d5db';
    const isTrigger = key === 'lt' || key === 'rt';
    const isDpad = key.startsWith('dpad');
    const ox = btn.offsetX ?? 0;
    const oy = btn.offsetY ?? 0;
    const sx = btn.scaleX ?? 1;
    const sy = btn.scaleY ?? 1;

    return (
      <div key={key} style={{
        position: 'absolute',
        left: nat.left,
        top: nat.top,
        width: nat.w,
        height: nat.h,
        transform: `translate(${ox}px, ${oy}px) scale(${sx}, ${sy})`,
        transformOrigin: 'center center',
        background: color + '22',
        border: `2px solid ${color}99`,
        borderRadius: isTrigger ? 8 : isDpad ? 6 : 28,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: nat.w > 70 ? 12 : 10,
        fontWeight: 700,
        color: '#ffffff',
        letterSpacing: '0.02em',
        userSelect: 'none',
      }}>
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

    const ox = joy?.offsetX ?? 0;
    const oy = joy?.offsetY ?? 0;
    const sx = joy?.scaleX ?? 1;
    const sy = joy?.scaleY ?? 1;
    const colors = isLeft ? leftJoyColors : JOYSTICK_COLORS.default;
    const knobR = nat.w * 0.38;

    return (
      <div key={key} style={{
        position: 'absolute',
        left: nat.left,
        top: nat.top,
        width: nat.w,
        height: nat.h,
        transform: `translate(${ox}px, ${oy}px) scale(${sx}, ${sy})`,
        transformOrigin: 'center center',
      }}>
        <div style={{
          position: 'absolute', inset: 0,
          borderRadius: '50%',
          background: colors.fill,
          border: `2.5px solid ${colors.ring}`,
        }} />
        <div style={{
          position: 'absolute',
          left: '50%', top: '50%',
          width: knobR * 2, height: knobR * 2,
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
    const btn = config?.buttons?.singleJoystickToggle ?? {};
    const ox = btn.offsetX ?? 0;
    const oy = btn.offsetY ?? 0;
    const sx = btn.scaleX ?? 1;
    const sy = btn.scaleY ?? 1;

    return (
      <div key="sideToggle" style={{
        position: 'absolute',
        left: nat.left, top: nat.top,
        width: nat.w, height: nat.h,
        transform: `translate(${ox}px, ${oy}px) scale(${sx}, ${sy})`,
        transformOrigin: 'center center',
        background: 'rgba(255,255,255,0.10)',
        border: '2px solid rgba(255,255,255,0.40)',
        borderRadius: 6,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontSize: 13, fontWeight: 700, color: '#ffffff',
        userSelect: 'none',
      }}>
        {outputSide === 'LEFT' ? 'L' : 'R'}
      </div>
    );
  }

  return (
    <div style={{ position: 'relative', width: canvasW, height: canvasH }}>
      {BUTTON_KEYS.map(renderButton)}
      {renderJoystick('leftJoystick', true)}
      {renderJoystick('rightJoystick', false)}
      {renderSideToggle()}
    </div>
  );
}
