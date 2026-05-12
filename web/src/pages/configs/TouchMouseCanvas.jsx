import { useMemo, useRef, useState } from 'react';
import { gridForCanvas, gridOverlayStyle, snap, snapPoint } from './gridSnap.js';

const DRAG_THRESHOLD = 5;

function zoneRect(zone, canvasW, canvasH) {
  return {
    left: zone.left * canvasW,
    top: zone.top * canvasH,
    width: Math.max(1, (zone.right - zone.left) * canvasW),
    height: Math.max(1, (zone.bottom - zone.top) * canvasH),
  };
}

function dynamicRect(zone, canvasW, canvasH) {
  const radius = zone.radius * Math.min(canvasW, canvasH);
  return {
    left: canvasW * (0.5 + zone.offsetX) - radius,
    top: canvasH * (0.5 + zone.offsetY) - radius,
    width: radius * 2,
    height: radius * 2,
  };
}

function setButton(config, key, patch) {
  return { ...config, [key]: { ...(config?.[key] ?? {}), ...patch } };
}

function setMacro(config, index, patch) {
  const macroButtons = [...(config?.macroButtons ?? [])];
  macroButtons[index] = { ...(macroButtons[index] ?? {}), ...patch };
  return { ...config, macroButtons };
}

export function getTouchMouseElementOffset(config, key) {
  if (key?.startsWith('macro:')) {
    const index = Number(key.split(':')[1]);
    const macro = config?.macroButtons?.[index] ?? {};
    return {
      ox: macro.layoutOffsetX ?? 0,
      oy: macro.layoutOffsetY ?? 0,
      sx: macro.layoutScaleX ?? 1,
      sy: macro.layoutScaleY ?? 1,
    };
  }
  return { ox: 0, oy: 0, sx: 1, sy: 1 };
}

export function applyTouchMouseOffset(config, key, ox, oy) {
  if (key?.startsWith('macro:')) {
    return setMacro(config, Number(key.split(':')[1]), { layoutOffsetX: ox, layoutOffsetY: oy });
  }
  return config;
}

export function applyTouchMouseScale(config, key, sx, sy) {
  if (key?.startsWith('macro:')) {
    return setMacro(config, Number(key.split(':')[1]), { layoutScaleX: sx, layoutScaleY: sy });
  }
  return config;
}

export default function TouchMouseCanvas({
  canvasW, canvasH, config,
  editMode = false, canvasScale = 1, onConfigChange,
  selectedKey = null, onSelect,
  snapToGrid = false,
}) {
  const grid = useMemo(() => gridForCanvas(canvasW, canvasH), [canvasW, canvasH]);
  const dragRef = useRef(null);
  const [activeKey, setActiveKey] = useState(null);

  function updateStaticZone(key, dx, dy) {
    const button = config?.[key];
    if (!button?.staticZone) return;
    const deltaX = dx / canvasW;
    const deltaY = dy / canvasH;
    const zone = button.staticZone;
    const width = zone.right - zone.left;
    const height = zone.bottom - zone.top;
    let left = zone.left + deltaX;
    let top = zone.top + deltaY;
    if (snapToGrid) {
      left = snap(left * canvasW, grid.stepX) / canvasW;
      top = snap(top * canvasH, grid.stepY) / canvasH;
    }
    left = Math.max(0, Math.min(1 - width, left));
    top = Math.max(0, Math.min(1 - height, top));
    onConfigChange?.(setButton(config, key, {
      staticZone: { left, top, right: left + width, bottom: top + height },
    }));
  }

  function updateDynamicZone(key, dx, dy) {
    const button = config?.[key];
    if (!button?.dynamicZone) return;
    let offsetX = button.dynamicZone.offsetX + dx / canvasW;
    let offsetY = button.dynamicZone.offsetY + dy / canvasH;
    if (snapToGrid) {
      const snapped = snapPoint(canvasW * (0.5 + offsetX), canvasH * (0.5 + offsetY), grid);
      offsetX = snapped.x / canvasW - 0.5;
      offsetY = snapped.y / canvasH - 0.5;
    }
    onConfigChange?.(setButton(config, key, {
      dynamicZone: {
        ...button.dynamicZone,
        offsetX: Math.max(-1, Math.min(1, offsetX)),
        offsetY: Math.max(-1, Math.min(1, offsetY)),
      },
    }));
  }

  function updateSniper(dx, dy) {
    const sniper = config?.sniper;
    if (!sniper?.zone) return;
    const deltaX = dx / canvasW;
    const deltaY = dy / canvasH;
    const zone = sniper.zone;
    const width = zone.right - zone.left;
    const height = zone.bottom - zone.top;
    let left = zone.left + deltaX;
    let top = zone.top + deltaY;
    if (snapToGrid) {
      left = snap(left * canvasW, grid.stepX) / canvasW;
      top = snap(top * canvasH, grid.stepY) / canvasH;
    }
    left = Math.max(0, Math.min(1 - width, left));
    top = Math.max(0, Math.min(1 - height, top));
    onConfigChange?.({ ...config, sniper: { ...sniper, zone: { left, top, right: left + width, bottom: top + height } } });
  }

  function updateMacro(index, dx, dy, scaleDelta) {
    const macro = config?.macroButtons?.[index];
    if (!macro) return;
    if (scaleDelta !== 0) {
      const factor = Math.pow(1.5, -scaleDelta / 120);
      const nextScale = Math.max(0.2, Math.min(4, (macro.layoutScaleX ?? 1) * factor));
      onConfigChange?.(setMacro(config, index, { layoutScaleX: nextScale, layoutScaleY: nextScale }));
    } else {
      const nextX = (macro.layoutOffsetX ?? 0) + dx;
      const nextY = (macro.layoutOffsetY ?? 0) + dy;
      const paletteLeft = Math.max(12, canvasW - 190);
      const snapped = snapToGrid
        ? snapPoint(paletteLeft + nextX, 18 + nextY, grid)
        : { x: paletteLeft + nextX, y: 18 + nextY };
      onConfigChange?.(setMacro(config, index, {
        layoutOffsetX: snapped.x - paletteLeft,
        layoutOffsetY: snapped.y - 18,
      }));
    }
  }

  function startDrag(e, key) {
    if (!editMode) return;
    e.stopPropagation();
    dragRef.current = { key, hasMoved: false, startX: e.clientX, startY: e.clientY };
    setActiveKey(key);
  }

  function handleMove(e) {
    if (!dragRef.current) return;
    const screenDX = e.clientX - dragRef.current.startX;
    const screenDY = e.clientY - dragRef.current.startY;
    if (!dragRef.current.hasMoved && Math.sqrt(screenDX ** 2 + screenDY ** 2) > DRAG_THRESHOLD) {
      dragRef.current.hasMoved = true;
    }
    if (!dragRef.current.hasMoved) return;

    dragRef.current.startX = e.clientX;
    dragRef.current.startY = e.clientY;
    const dx = screenDX / canvasScale;
    const dy = screenDY / canvasScale;
    const key = dragRef.current.key;

    if (key === 'leftButton' || key === 'rightButton') {
      const button = config?.[key];
      if (button?.zoneType === 'DYNAMIC') updateDynamicZone(key, dx, dy);
      else updateStaticZone(key, dx, dy);
    } else if (key === 'sniper') {
      updateSniper(dx, dy);
    } else if (key.startsWith('macro:')) {
      updateMacro(Number(key.split(':')[1]), dx, dy, e.shiftKey ? screenDY : 0);
    }
  }

  function handleUp() {
    if (!dragRef.current) return;
    const { key, hasMoved } = dragRef.current;
    dragRef.current = null;
    setActiveKey(null);
    if (!hasMoved) onSelect?.(selectedKey === key ? null : key);
  }

  function selectableStyle(key, base) {
    const selected = editMode && selectedKey === key;
    const active = editMode && activeKey === key;
    return {
      ...base,
      cursor: editMode ? (active ? 'grabbing' : 'grab') : 'default',
      boxShadow: active
        ? '0 0 0 2px rgba(255,255,255,.9), 0 0 14px rgba(255,255,255,.45)'
        : selected
          ? '0 0 0 2px rgba(255,255,255,.65), 0 0 0 4px rgba(255,255,255,.16)'
          : base.boxShadow,
    };
  }

  function renderButtonZone(key, label, color) {
    const button = config?.[key];
    if (!button?.enabled) return null;
    const rect = button.zoneType === 'DYNAMIC'
      ? dynamicRect(button.dynamicZone, canvasW, canvasH)
      : zoneRect(button.staticZone, canvasW, canvasH);
    return (
      <div
        key={key}
        onPointerDown={editMode ? e => startDrag(e, key) : undefined}
        style={selectableStyle(key, {
          position: 'absolute',
          ...rect,
          borderRadius: button.zoneType === 'DYNAMIC' ? '50%' : 10,
          border: `2px solid ${color}`,
          background: `${color}26`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#fff',
          fontWeight: 800,
          fontSize: 14,
          userSelect: 'none',
        })}
      >
        {label}
      </div>
    );
  }

  function renderSubRegion(region, index) {
    if (!region?.enabled) return null;
    const rect = region.zoneType === 'DYNAMIC'
      ? dynamicRect(region.dynamicZone, canvasW, canvasH)
      : zoneRect(region.staticZone, canvasW, canvasH);
    return (
      <div
        key={`sub:${index}`}
        style={{
          position: 'absolute',
          ...rect,
          borderRadius: region.zoneType === 'DYNAMIC' ? '50%' : 8,
          border: '2px dashed rgba(251,191,36,.9)',
          background: 'rgba(251,191,36,.18)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#fff',
          fontWeight: 800,
          fontSize: 11,
          pointerEvents: 'none',
        }}
      >
        {region.alternateMouseButton?.slice(0, 1) || 'MOD'}
      </div>
    );
  }

  function renderMacro(macro, index) {
    const key = `macro:${index}`;
    const sx = macro.layoutScaleX ?? 1;
    const sy = macro.layoutScaleY ?? 1;
    const paletteLeft = Math.max(12, canvasW - 190);
    return (
      <div
        key={key}
        onPointerDown={editMode ? e => startDrag(e, key) : undefined}
        style={selectableStyle(key, {
          position: 'absolute',
          left: paletteLeft,
          top: 18,
          minWidth: 72,
          height: 38,
          padding: '0 10px',
          transform: `translate(${macro.layoutOffsetX ?? 0}px, ${macro.layoutOffsetY ?? 0}px) scale(${sx}, ${sy})`,
          transformOrigin: 'center center',
          borderRadius: 8,
          border: '2px solid rgba(168,85,247,.78)',
          background: 'rgba(168,85,247,.24)',
          color: '#fff',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 11,
          fontWeight: 800,
          userSelect: 'none',
        })}
      >
        {macro.label}
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
      <div className="touchmouse-surface-label">
        {config?.mode === 'MOUSE' ? 'Mouse Movement Surface' : 'Touch Surface'}
      </div>
      {renderButtonZone('leftButton', 'L', '#60a5fa')}
      {renderButtonZone('rightButton', 'R', '#fb923c')}
      {(config?.leftButton?.subRegions ?? []).map(renderSubRegion)}
      {(config?.rightButton?.subRegions ?? []).map((region, index) => renderSubRegion(region, `r${index}`))}
      {config?.sniper?.enabled && (
        <div
          onPointerDown={editMode ? e => startDrag(e, 'sniper') : undefined}
          style={selectableStyle('sniper', {
            position: 'absolute',
            ...zoneRect(config.sniper.zone, canvasW, canvasH),
            borderRadius: 8,
            border: '2px solid rgba(45,212,191,.9)',
            background: 'rgba(45,212,191,.18)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontWeight: 800,
            userSelect: 'none',
          })}
        >
          S/{config.sniper.divisor}
        </div>
      )}
      {(config?.macroButtons ?? []).map(renderMacro)}
    </div>
  );
}
