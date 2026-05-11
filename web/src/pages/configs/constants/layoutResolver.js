import LAYOUT from './gamepadLayout.json';

export function resolveLayout(canvasW, canvasH) {
  const topOffset = LAYOUT.topReservedDp;
  const els = LAYOUT.elements;
  const chainGroups = LAYOUT.chainGroups ?? {};

  const xPos = {};
  const yPos = {};
  const w = {};
  const h = {};

  for (const [id, el] of Object.entries(els)) {
    w[id] = el.w;
    h[id] = el.h;
  }

  // Pre-pass: resolve chain group x positions
  for (const group of Object.values(chainGroups)) {
    const totalW = group.elements.reduce((sum, id) => sum + w[id], 0) +
                   group.gap * (group.elements.length - 1);
    let x = group.horizontalAlign === 'center' ? (canvasW - totalW) / 2 : 0;
    for (const id of group.elements) {
      xPos[id] = x;
      x += w[id] + group.gap;
    }
  }

  function getXDeps(id) {
    if (xPos[id] !== undefined) return [];
    const el = els[id];
    const deps = [];
    if (el.startToStart && el.startToStart !== 'parent') deps.push(el.startToStart);
    if (el.startToEnd   && el.startToEnd   !== 'parent') deps.push(el.startToEnd);
    if (el.endToEnd     && el.endToEnd     !== 'parent') deps.push(el.endToEnd);
    if (el.endToStart   && el.endToStart   !== 'parent') deps.push(el.endToStart);
    return deps;
  }

  function getYDeps(id) {
    const el = els[id];
    const deps = [];
    if (el.topToTop      && el.topToTop      !== 'parent') deps.push(el.topToTop);
    if (el.topToBottom   && el.topToBottom   !== 'parent') deps.push(el.topToBottom);
    if (el.bottomToBottom && el.bottomToBottom !== 'parent') deps.push(el.bottomToBottom);
    if (el.bottomToTop   && el.bottomToTop   !== 'parent') deps.push(el.bottomToTop);
    return deps;
  }

  function topoSort(ids, getDeps) {
    const visited = new Set();
    const order = [];
    function visit(id) {
      if (visited.has(id)) return;
      visited.add(id);
      for (const dep of getDeps(id)) visit(dep);
      order.push(id);
    }
    for (const id of ids) visit(id);
    return order;
  }

  const allIds = Object.keys(els);
  const xOrder = topoSort(allIds, getXDeps);
  const yOrder = topoSort(allIds, getYDeps);

  for (const id of xOrder) {
    if (xPos[id] !== undefined) continue;
    const el = els[id];
    const sm = el.startMargin ?? 0;
    const em = el.endMargin ?? 0;
    if      (el.startToStart === 'parent')         xPos[id] = sm;
    else if (el.startToStart)                      xPos[id] = xPos[el.startToStart] + sm;
    else if (el.startToEnd   === 'parent')         xPos[id] = canvasW + sm;
    else if (el.startToEnd)                        xPos[id] = xPos[el.startToEnd] + w[el.startToEnd] + sm;
    else if (el.endToEnd     === 'parent')         xPos[id] = canvasW - w[id] - em;
    else if (el.endToEnd)                          xPos[id] = xPos[el.endToEnd] + w[el.endToEnd] - w[id] - em;
    else if (el.endToStart   === 'parent')         xPos[id] = -w[id] - em;
    else if (el.endToStart)                        xPos[id] = xPos[el.endToStart] - w[id] - em;
    else                                           xPos[id] = 0;
  }

  for (const id of yOrder) {
    const el = els[id];
    const tm = el.topMargin ?? 0;
    const bm = el.bottomMargin ?? 0;
    if      (el.topToTop      === 'parent')        yPos[id] = topOffset + tm;
    else if (el.topToTop)                          yPos[id] = yPos[el.topToTop] + tm;
    else if (el.topToBottom   === 'parent')        yPos[id] = canvasH + tm;
    else if (el.topToBottom)                       yPos[id] = yPos[el.topToBottom] + h[el.topToBottom] + tm;
    else if (el.bottomToBottom === 'parent')       yPos[id] = canvasH - h[id] - bm;
    else if (el.bottomToBottom)                    yPos[id] = yPos[el.bottomToBottom] + h[el.bottomToBottom] - h[id] - bm;
    else if (el.bottomToTop   === 'parent')        yPos[id] = -h[id] - bm;
    else if (el.bottomToTop)                       yPos[id] = yPos[el.bottomToTop] - h[id] - bm;
    else                                           yPos[id] = 0;
  }

  const result = {};
  for (const id of allIds) {
    result[id] = { left: xPos[id] ?? 0, top: yPos[id] ?? 0, w: w[id], h: h[id] };
  }
  return result;
}
