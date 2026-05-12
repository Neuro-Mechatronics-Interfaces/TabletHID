#!/usr/bin/env node
import crypto from 'crypto';
import path from 'path';
import { fileURLToPath } from 'url';
import Database from '../web/node_modules/better-sqlite3/lib/index.js';
import { validateGamepadConfig, validateTouchMouseConfig } from '../web/api/validate.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_QS_DB = path.join(__dirname, 'output', 'quadstick.db');
const DEFAULT_TABLETHID_DB = path.join(__dirname, '..', 'web', 'data', 'tablethid.db');
const IMPORT_NAMESPACE = 'tablethid:quadstick:v2';
const DEFAULT_CLUSTER_THRESHOLD = 0.72;

const BUTTON_KEYS = [
  'a', 'b', 'x', 'y', 'lb', 'rb', 'lt', 'rt',
  'back', 'start', 'dpadUp', 'dpadDown', 'dpadLeft', 'dpadRight',
];

const OUTPUT_TO_BUTTON = new Map([
  ['x', 'a'],
  ['circle', 'b'],
  ['square', 'x'],
  ['triangle', 'y'],
  ['left_1', 'lb'],
  ['right_1', 'rb'],
  ['left_2', 'lt'],
  ['right_2', 'rt'],
  ['select', 'back'],
  ['start', 'start'],
  ['dpad_N', 'dpadUp'],
  ['dpad_S', 'dpadDown'],
  ['dpad_W', 'dpadLeft'],
  ['dpad_E', 'dpadRight'],
]);

const DIAGONAL_DPAD = new Map([
  ['dpad_NE', ['dpadUp', 'dpadRight']],
  ['dpad_SE', ['dpadDown', 'dpadRight']],
  ['dpad_SW', ['dpadDown', 'dpadLeft']],
  ['dpad_NW', ['dpadUp', 'dpadLeft']],
]);

const BUTTON_LABELS = {
  a: 'A',
  b: 'B',
  x: 'X',
  y: 'Y',
  lb: 'LB',
  rb: 'RB',
  lt: 'LT',
  rt: 'RT',
  back: 'Back',
  start: 'Start',
  dpadUp: 'Up',
  dpadDown: 'Down',
  dpadLeft: 'Left',
  dpadRight: 'Right',
};

const KEYBOARD_USAGE = new Map([
  ...'abcdefghijklmnopqrstuvwxyz'.split('').map((key, index) => [`kb_${key}`, 0x04 + index]),
  ...'123456789'.split('').map((key, index) => [`kb_${key}`, 0x1e + index]),
  ['kb_0', 0x27],
  ['kb_enter', 0x28],
  ['kb_escape', 0x29],
  ['kb_backspace', 0x2a],
  ['kb_tab', 0x2b],
  ['kb_space', 0x2c],
  ['kb_minus', 0x2d],
  ['kb_equal', 0x2e],
  ['kb_left_bracket', 0x2f],
  ['kb_right_bracket', 0x30],
  ['kb_backslash', 0x31],
  ['kb_semicolon', 0x33],
  ['kb_quote', 0x34],
  ['kb_grave', 0x35],
  ['kb_comma', 0x36],
  ['kb_period', 0x37],
  ['kb_slash', 0x38],
  ['kb_caps_lock', 0x39],
  ...Array.from({ length: 12 }, (_, index) => [`kb_f${index + 1}`, 0x3a + index]),
  ['kb_print_screen', 0x46],
  ['kb_scroll_lock', 0x47],
  ['kb_pause', 0x48],
  ['kb_insert', 0x49],
  ['kb_home', 0x4a],
  ['kb_page_up', 0x4b],
  ['kb_delete', 0x4c],
  ['kb_end', 0x4d],
  ['kb_page_down', 0x4e],
  ['kb_right_arrow', 0x4f],
  ['kb_left_arrow', 0x50],
  ['kb_down_arrow', 0x51],
  ['kb_up_arrow', 0x52],
  ['kb_keypad_plus', 0x57],
  ['kb_left_control', 0xe0],
  ['kb_left_shift', 0xe1],
  ['kb_left_alt', 0xe2],
  ['kb_left_gui', 0xe3],
  ['kb_right_control', 0xe4],
  ['kb_right_shift', 0xe5],
  ['kb_right_alt', 0xe6],
  ['kb_right_gui', 0xe7],
]);

function parseArgs(argv) {
  const args = {
    source: DEFAULT_QS_DB,
    target: DEFAULT_TABLETHID_DB,
    limit: null,
    dryRun: false,
    replace: false,
    macrosOnly: false,
    since: '2026-05-01T00:00:00.000Z',
    clusterThreshold: DEFAULT_CLUSTER_THRESHOLD,
  };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--source') args.source = argv[++i];
    else if (arg === '--target') args.target = argv[++i];
    else if (arg === '--limit') args.limit = Number.parseInt(argv[++i], 10);
    else if (arg === '--dry-run') args.dryRun = true;
    else if (arg === '--replace') args.replace = true;
    else if (arg === '--macros-only') args.macrosOnly = true;
    else if (arg === '--since') args.since = argv[++i];
    else if (arg === '--cluster-threshold') args.clusterThreshold = Number.parseFloat(argv[++i]);
    else if (arg === '--help' || arg === '-h') {
      printHelp();
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  if (args.limit !== null && (!Number.isInteger(args.limit) || args.limit < 1)) {
    throw new Error('--limit must be a positive integer');
  }
  if (!Number.isFinite(args.clusterThreshold) || args.clusterThreshold <= 0 || args.clusterThreshold > 1) {
    throw new Error('--cluster-threshold must be a number in the range (0, 1]');
  }

  return args;
}

function printHelp() {
  console.log(`Usage: node scripts/import_quadstick_community_configs.mjs [options]

Converts scraped QuadStick sheet metadata from scripts/output/quadstick.db into
TabletHID Community Config rows in web/data/tablethid.db.

Options:
  --source PATH   Scraped QuadStick SQLite DB (default: scripts/output/quadstick.db)
  --target PATH   TabletHID Community SQLite DB (default: web/data/tablethid.db)
  --limit N       Convert at most N source configs
  --dry-run       Print conversion summary without writing target DB
  --replace       Replace deterministic QuadStick import IDs if they already exist
  --macros-only   Re-derive and patch only macroButtons on existing rows; preserves all other metadata
  --since ISO     Base uploaded_at timestamp for deterministic ordering
  --cluster-threshold N
                 Minimum Jaccard overlap for shared layouts (default: ${DEFAULT_CLUSTER_THRESHOLD})
`);
}

function stableUuid(input) {
  const bytes = crypto.createHash('sha1').update(`${IMPORT_NAMESPACE}:${input}`).digest().subarray(0, 16);
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function makeButton(overrides = {}) {
  return {
    enabled: false,
    behavior: 'MOMENTARY',
    turbo: false,
    turboDurationMs: 100,
    turboIntervalMs: 100,
    offsetX: 0,
    offsetY: 0,
    scaleX: 1,
    scaleY: 1,
    ...overrides,
  };
}

function makeJoystick(overrides = {}) {
  return {
    enabled: false,
    deadzone: 0.05,
    gain: 1,
    offsetX: 0,
    offsetY: 0,
    scaleX: 1,
    scaleY: 1,
    ...overrides,
  };
}

function emptyGamepadConfig() {
  const buttons = {};
  for (const key of BUTTON_KEYS) {
    const triggerDefaults = key === 'lt' || key === 'rt'
      ? { triggerTravelDp: 80, triggerAxis: 'UP' }
      : {};
    buttons[key] = makeButton(triggerDefaults);
  }

  return {
    buttons,
    leftJoystick: makeJoystick(),
    rightJoystick: makeJoystick(),
    singleJoystickMode: false,
    singleJoystickSideToggleEnabled: false,
    singleJoystickOutputSide: 'LEFT',
    macroHostDefaults: 'WINDOWS',
    macroButtons: [],
    vibrationIntensity: 'OFF',
    customButtonLabels: {},
  };
}

function emptyTouchMouseConfig() {
  return {
    mode: 'MOUSE',
    sensitivity: 5,
    scrollEnabled: true,
    invertScroll: false,
    sharedDynamicZone: false,
    sharedDynamic: { offsetX: 0.5, offsetY: 0.5, radius: 0.15 },
    leftButton: {
      enabled: false,
      zoneType: 'STATIC',
      behavior: 'MOMENTARY',
      staticZone: { left: 0, top: 0.75, right: 0.5, bottom: 1 },
      dynamicZone: { offsetX: 0.25, offsetY: 0.87, radius: 0.08 },
      subRegions: [],
    },
    rightButton: {
      enabled: false,
      zoneType: 'STATIC',
      behavior: 'MOMENTARY',
      staticZone: { left: 0.5, top: 0.75, right: 1, bottom: 1 },
      dynamicZone: { offsetX: 0.75, offsetY: 0.87, radius: 0.08 },
      subRegions: [],
    },
    sniper: {
      enabled: false,
      zone: { left: 0, top: 0, right: 0.15, bottom: 0.25 },
      divisor: 3,
    },
    macroHostDefaults: 'WINDOWS',
    macroButtons: [],
  };
}

function rowInputs(row) {
  const values = [];
  for (let i = 1; i <= 8; i++) {
    const value = row[`input_${i}`];
    if (value !== null && value !== undefined && String(value).trim() !== '') {
      values.push(String(value).trim());
    }
  }
  return values;
}

function displayInput(input) {
  return input
    .replace(/^mp_/, '')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, c => c.toUpperCase())
    .replace(/\bUsb\b/g, 'USB');
}

function compactLabel(buttonKey, inputs) {
  if (!inputs.length) return BUTTON_LABELS[buttonKey] ?? buttonKey.toUpperCase();
  const rendered = inputs.map(displayInput).join(' + ');
  return rendered.length <= 18 ? rendered : `${rendered.slice(0, 15)}...`;
}

function inputKind(input) {
  const lower = input.toLowerCase();
  if (/^(left|right|up|down|center)$/.test(lower)) return 'joystick';
  if (/^(n|ne|e|se|s|sw|w|nw)$/.test(lower)) return 'hat';
  if (/^usb_\d+_(left|right|up|down|n|ne|e|se|s|sw|w|nw)$/i.test(input)) return 'external_analog';
  if (/^usb_\d+_button_\d+$/i.test(input)) return 'external_button';
  if (lower.includes('sip') || lower.includes('puff') || lower.includes('lip') || lower === 'push') return 'sip_puff';
  return 'named_button';
}

function bindingInputsByOutput(bindings) {
  const byOutput = new Map();
  for (const binding of bindings) {
    const inputs = rowInputs(binding);
    if (!inputs.length) continue;
    const current = byOutput.get(binding.output_name) ?? new Set();
    for (const input of inputs) current.add(input);
    byOutput.set(binding.output_name, current);
  }
  return byOutput;
}

function controlsForBindings(bindings) {
  const controls = new Set();
  const unsupportedOutputs = new Set();
  const inputsByButton = new Map();
  const macroInputsByOutput = new Map();

  for (const [output, inputSet] of bindingInputsByOutput(bindings).entries()) {
    const directButton = OUTPUT_TO_BUTTON.get(output);
    const diagonalButtons = DIAGONAL_DPAD.get(output) ?? [];
    const buttons = directButton ? [directButton] : diagonalButtons;

    if (output.startsWith('left_joy_')) {
      controls.add('leftJoystick');
      continue;
    }
    if (output.startsWith('right_joy_')) {
      controls.add('rightJoystick');
      continue;
    }
    if (output.startsWith('kb_') && KEYBOARD_USAGE.has(output)) {
      controls.add(`macro:${output}`);
      macroInputsByOutput.set(output, inputSet);
      continue;
    }
    if (output === 'mouse_left_button') {
      controls.add('touchLeftButton');
      continue;
    }
    if (output === 'mouse_right_button') {
      controls.add('touchRightButton');
      continue;
    }
    if (output === 'mouse_middle_button') {
      controls.add('touchMiddleButton');
      continue;
    }
    if (output.startsWith('mouse_')) {
      controls.add('touchMovement');
      continue;
    }

    if (!buttons.length) {
      unsupportedOutputs.add(output);
      continue;
    }

    for (const button of buttons) {
      controls.add(button);
      const current = inputsByButton.get(button) ?? new Set();
      for (const input of inputSet) current.add(input);
      inputsByButton.set(button, current);
    }
  }

  return { controls, unsupportedOutputs, inputsByButton, macroInputsByOutput };
}

function inputKindsForBindings(bindings) {
  return new Set(bindings.flatMap(rowInputs).map(inputKind));
}

function inferLayoutFromControls(controls, kinds) {
  const hasLeftJoy = controls.has('leftJoystick');
  const hasRightJoy = controls.has('rightJoystick');
  const hasDpad = ['dpadUp', 'dpadDown', 'dpadLeft', 'dpadRight'].some(k => controls.has(k));
  const hasTriggers = controls.has('lt') || controls.has('rt');
  const faceCount = ['a', 'b', 'x', 'y'].filter(k => controls.has(k)).length;
  const shoulderCount = ['lb', 'rb', 'lt', 'rt'].filter(k => controls.has(k)).length;
  const hasPhysicalAnalog = kinds.has('joystick') || kinds.has('hat') || kinds.has('external_analog');
  const hasPhysicalButtons = kinds.has('sip_puff') || kinds.has('external_button') || kinds.has('named_button');

  let family = 'buttons-only';
  if (hasLeftJoy && hasRightJoy) family = 'dual-stick';
  else if (hasLeftJoy) family = 'single-stick-left';
  else if (hasRightJoy) family = 'single-stick-right';
  else if (hasDpad) family = 'dpad';
  else if (controls.has('touchMovement')) family = 'mouse-keyboard';
  else if ([...controls].some(c => c.startsWith('macro:'))) family = 'keyboard-macros';
  else if (hasTriggers) family = 'trigger-buttons';

  const traits = [];
  if (hasPhysicalAnalog) traits.push('analog-inputs');
  if (hasPhysicalButtons) traits.push('pushbutton-inputs');
  if (hasDpad) traits.push('dpad');
  if (hasTriggers) traits.push('triggers');
  if (faceCount > 0) traits.push('face-buttons');
  if (shoulderCount > 0) traits.push('shoulders');
  if (controls.has('touchMovement')) traits.push('mouse');
  if ([...controls].some(c => c.startsWith('macro:'))) traits.push('keyboard');

  return { family, traits, hasLeftJoy, hasRightJoy };
}

function communityModeForControls(controls) {
  const hasGamepadControl = [...BUTTON_KEYS, 'leftJoystick', 'rightJoystick'].some(control => controls.has(control));
  return hasGamepadControl ? 'gamepad' : 'touch_mouse';
}

function applyLayoutHints(config, layout, controls) {
  for (const key of BUTTON_KEYS) {
    config.buttons[key].enabled = controls.has(key);
  }

  if (layout.family === 'single-stick-left') {
    config.singleJoystickMode = true;
    config.singleJoystickOutputSide = 'LEFT';
    config.leftJoystick.enabled = true;
    config.rightJoystick.enabled = false;
  } else if (layout.family === 'single-stick-right') {
    config.singleJoystickMode = true;
    config.singleJoystickOutputSide = 'RIGHT';
    config.leftJoystick.enabled = true;
    config.rightJoystick.enabled = false;
  } else {
    config.leftJoystick.enabled = layout.hasLeftJoy;
    config.rightJoystick.enabled = layout.hasRightJoy;
  }

  if (layout.family === 'buttons-only' || layout.family === 'trigger-buttons') {
    for (const key of ['a', 'b', 'x', 'y']) {
      if (config.buttons[key].enabled) {
        config.buttons[key].scaleX = 1.15;
        config.buttons[key].scaleY = 1.15;
      }
    }
  }
}

function macroLabel(output, inputs) {
  const key = output.replace(/^kb_/, '').replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  if (!inputs.length) return key;
  const rendered = `${key}: ${inputs.map(displayInput).join(' + ')}`;
  return rendered.length <= 18 ? rendered : `${rendered.slice(0, 15)}...`;
}

function macroButtonsForControls(controls, macroInputsByOutput) {
  const outputs = [...controls]
    .filter(control => control.startsWith('macro:'))
    .map(control => control.slice('macro:'.length))
    .sort();

  return outputs.map((output, index) => ({
    label: macroLabel(output, [...(macroInputsByOutput.get(output) ?? [])]),
    modifiers: 0,
    keyUsages: [KEYBOARD_USAGE.get(output)],
    // 2-column grid; 90dp between columns, 56dp between rows (~44dp button height + 12dp gap)
    layoutOffsetX: (index % 2) * 90,
    layoutOffsetY: Math.floor(index / 2) * 56,
    layoutScaleX: 1,
    layoutScaleY: 1,
  }));
}

function jaccard(a, b) {
  let intersection = 0;
  for (const value of a) {
    if (b.has(value)) intersection++;
  }
  const union = new Set([...a, ...b]).size;
  return union === 0 ? 0 : intersection / union;
}

function clusterSources(items, threshold) {
  const clusters = [];
  const sorted = [...items].sort((a, b) => {
    const sizeDiff = b.controls.size - a.controls.size;
    if (sizeDiff !== 0) return sizeDiff;
    return a.sourceConfig.config_id - b.sourceConfig.config_id;
  });

  for (const item of sorted) {
    let bestCluster = null;
    let bestScore = 0;
    for (const cluster of clusters) {
      if (cluster.mode !== item.mode) continue;
      if (cluster.layout.family !== item.layout.family) continue;
      const score = jaccard(item.controls, cluster.unionControls);
      if (score > bestScore) {
        bestScore = score;
        bestCluster = cluster;
      }
    }

    if (bestCluster && bestScore >= threshold) {
      bestCluster.items.push(item);
      for (const control of item.controls) bestCluster.unionControls.add(control);
      for (const kind of item.inputKinds) bestCluster.inputKinds.add(kind);
      bestCluster.layout = inferLayoutFromControls(bestCluster.unionControls, bestCluster.inputKinds);
      continue;
    }

    clusters.push({
      mode: item.mode,
      items: [item],
      unionControls: new Set(item.controls),
      inputKinds: new Set(item.inputKinds),
      layout: item.layout,
    });
  }

  return clusters.map((cluster, index) => {
    const signature = [...cluster.unionControls].sort().join(',');
    const clusterId = `qs-${crypto.createHash('sha1').update(`${cluster.layout.family}:${signature}`).digest('hex').slice(0, 8)}`;
    return { ...cluster, clusterId, index };
  });
}

function convertGamepadConfig(sourceConfig, bindings, cluster) {
  const config = emptyGamepadConfig();
  const { controls, inputsByButton, macroInputsByOutput, unsupportedOutputs } = controlsForBindings(bindings);

  for (const button of BUTTON_KEYS) {
    if (!cluster.unionControls.has(button)) continue;
    const inputs = [...(inputsByButton.get(button) ?? [])];
    config.customButtonLabels[button] = compactLabel(button, inputs);
  }

  applyLayoutHints(config, cluster.layout, cluster.unionControls);
  config.macroButtons = macroButtonsForControls(controls, macroInputsByOutput);

  const error = validateGamepadConfig(config);
  if (error !== null) {
    throw new Error(`generated config did not validate for source config ${sourceConfig.config_id}: ${error}`);
  }

  return { config, layout: cluster.layout, unsupportedOutputs: [...unsupportedOutputs].sort() };
}

function convertTouchMouseConfig(sourceConfig, bindings, cluster) {
  const config = emptyTouchMouseConfig();
  const { controls, macroInputsByOutput, unsupportedOutputs } = controlsForBindings(bindings);
  config.mode = cluster.unionControls.has('touchMovement') ? 'MOUSE' : 'TOUCH';
  config.leftButton.enabled = cluster.unionControls.has('touchLeftButton');
  config.rightButton.enabled = cluster.unionControls.has('touchRightButton');

  if (cluster.unionControls.has('touchMiddleButton')) {
    config.leftButton.subRegions.push({
      enabled: true,
      zoneType: 'STATIC',
      staticZone: { left: 0.25, top: 0.75, right: 0.5, bottom: 1 },
      dynamicZone: { offsetX: 0.4, offsetY: 0.87, radius: 0.08 },
      keyboardModifiers: 0,
      alternateMouseButton: 'MIDDLE',
    });
  }

  config.macroButtons = macroButtonsForControls(controls, macroInputsByOutput);

  const error = validateTouchMouseConfig(config);
  if (error !== null) {
    throw new Error(`generated touch config did not validate for source config ${sourceConfig.config_id}: ${error}`);
  }

  return { config, layout: cluster.layout, unsupportedOutputs: [...unsupportedOutputs].sort() };
}

function convertConfig(sourceConfig, bindings, cluster) {
  return cluster.mode === 'touch_mouse'
    ? convertTouchMouseConfig(sourceConfig, bindings, cluster)
    : convertGamepadConfig(sourceConfig, bindings, cluster);
}

function categoryFor(sourceConfig, layout, bindings) {
  const haystack = `${sourceConfig.folder} ${sourceConfig.xlsx_name} ${sourceConfig.sheet_name}`.toLowerCase();
  if (haystack.includes('mouse')) return 'productivity';
  if (haystack.includes('keyboard') || bindings.some(b => b.output_name.startsWith('kb_'))) return 'productivity';
  if (haystack.includes('tv') || bindings.some(b => b.output_name.startsWith('ir_'))) return 'media';
  if (layout.traits.includes('analog-inputs') || layout.traits.includes('face-buttons')) return 'gaming';
  return 'accessibility';
}

function profileNameFor(sourceConfig) {
  const folder = humanizeSourceName(sourceConfig.folder || sourceConfig.xlsx_name.replace(/\.xlsx$/i, ''));
  const sheet = humanizeSourceName(sourceConfig.sheet_name);
  const mode = humanizeSourceName(sourceConfig.mode_name || sourceConfig.sheet_name);
  const name = `${folder} - ${sheet} - ${mode}`.replace(/\s+/g, ' ').trim();
  return name.length > 80 ? name.slice(0, 77).trimEnd() + '...' : name;
}

function humanizeSourceName(value) {
  return String(value ?? '')
    .replace(/_s\b/g, "'s")
    .replace(/_/g, ' ')
    .trim();
}

function truncateText(value, maxLength) {
  if (value.length <= maxLength) return value;
  const sliced = value.slice(0, maxLength - 3);
  const lastSpace = sliced.lastIndexOf(' ');
  const base = lastSpace >= Math.floor(maxLength * 0.75) ? sliced.slice(0, lastSpace) : sliced;
  return `${base.trimEnd()}...`;
}

function descriptionFor(sourceConfig, layout, unsupportedOutputs, cluster) {
  const friendlyName = humanizeSourceName(sourceConfig.folder || sourceConfig.xlsx_name.replace(/\.xlsx$/i, ''));
  const pieces = [
    `QuadStick-derived ${friendlyName} layout from ${sourceConfig.rel_path}, sheet "${sourceConfig.sheet_name}".`,
    `Graph cluster ${cluster.clusterId}: shared ${layout.family} layout across ${cluster.items.length} similar sheet config${cluster.items.length === 1 ? '' : 's'}; traits: ${layout.traits.join(', ') || 'none'}.`,
  ];
  if (sourceConfig.transport) pieces.push(`Original transport: ${sourceConfig.transport}.`);
  if (unsupportedOutputs.length) {
    const rendered = unsupportedOutputs.slice(0, 12).join(', ');
    const suffix = unsupportedOutputs.length > 12 ? `, +${unsupportedOutputs.length - 12} more` : '';
    pieces.push(`Unsupported QuadStick outputs preserved only as metadata: ${rendered}${suffix}.`);
  }
  return truncateText(pieces.join(' '), 400);
}

function tagsFor(sourceConfig, layout, cluster) {
  const tags = ['quadstick', cluster.clusterId, layout.family, ...layout.traits];
  if (sourceConfig.transport) tags.push(String(sourceConfig.transport).toLowerCase().replace(/[^a-z0-9-]+/g, '-'));
  return [...new Set(tags)]
    .map(t => t.toLowerCase().replace(/[^a-z0-9-]+/g, '-').replace(/^-|-$/g, ''))
    .filter(Boolean)
    .slice(0, 8);
}

function loadSourceConfigs(qsDb, limit) {
  const sql = `
    SELECT c.*
    FROM configs c
    WHERE EXISTS (
      SELECT 1 FROM bindings b
      WHERE b.config_id = c.config_id
        AND (
          b.output_name LIKE 'left_joy_%'
          OR b.output_name LIKE 'right_joy_%'
          OR b.output_name LIKE 'dpad_%'
          OR b.output_name LIKE 'kb_%'
          OR b.output_name LIKE 'mouse_%'
          OR b.output_name IN ('x','circle','square','triangle','left_1','right_1','left_2','right_2','select','start')
        )
    )
    ORDER BY c.config_id
    ${limit === null ? '' : 'LIMIT ?'}
  `;
  return limit === null ? qsDb.prepare(sql).all() : qsDb.prepare(sql).all(limit);
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const qsDb = new Database(args.source, { readonly: true });
  const sourceConfigs = loadSourceConfigs(qsDb, args.limit);
  const bindingsStmt = qsDb.prepare('SELECT * FROM bindings WHERE config_id = ? ORDER BY binding_id');

  const sourceItems = sourceConfigs.map(sourceConfig => {
    const bindings = bindingsStmt.all(sourceConfig.config_id);
    const { controls } = controlsForBindings(bindings);
    const inputKinds = inputKindsForBindings(bindings);
    return {
      sourceConfig,
      bindings,
      controls,
      inputKinds,
      mode: communityModeForControls(controls),
      layout: inferLayoutFromControls(controls, inputKinds),
    };
  });
  const clusters = clusterSources(sourceItems, args.clusterThreshold);
  const clusterByConfigId = new Map();
  for (const cluster of clusters) {
    for (const item of cluster.items) {
      clusterByConfigId.set(item.sourceConfig.config_id, cluster);
    }
  }

  const rows = [];
  const layoutCounts = new Map();
  const skipped = [];
  const baseTime = Date.parse(args.since);
  if (!Number.isFinite(baseTime)) throw new Error(`Invalid --since timestamp: ${args.since}`);

  for (let index = 0; index < sourceItems.length; index++) {
    const { sourceConfig, bindings } = sourceItems[index];
    const cluster = clusterByConfigId.get(sourceConfig.config_id);
    try {
      const { config, layout, unsupportedOutputs } = convertConfig(sourceConfig, bindings, cluster);
      layoutCounts.set(layout.family, (layoutCounts.get(layout.family) ?? 0) + 1);
      rows.push({
        id: stableUuid(`${sourceConfig.rel_path}:${sourceConfig.sheet_name}:${sourceConfig.block_index}:${sourceConfig.config_id}`),
        schema_version: 1,
        platform: 'android',
        mode: cluster.mode,
        profile_name: profileNameFor(sourceConfig),
        description: descriptionFor(sourceConfig, layout, unsupportedOutputs, cluster),
        tags: JSON.stringify(tagsFor(sourceConfig, layout, cluster)),
        category: categoryFor(sourceConfig, layout, bindings),
        app_version: 'quadstick-import',
        config_json: JSON.stringify(config),
        uploaded_at: new Date(baseTime + index * 1000).toISOString(),
        download_count: 0,
        device_name: 'QuadStick sheet import',
        device_hw_id: null,
        device_os_version: null,
        device_os_api_level: null,
        device_screen_width_px: null,
        device_screen_height_px: null,
        device_screen_density_dpi: null,
        device_screen_diagonal_in: null,
      });
    } catch (error) {
      skipped.push({ config_id: sourceConfig.config_id, error: error.message });
    }
  }

  console.log(`Source configs with mappable controls: ${sourceConfigs.length}`);
  console.log(`Shared layout clusters: ${clusters.length} (threshold ${args.clusterThreshold})`);
  console.log(`Converted rows: ${rows.length}`);
  console.log(`Skipped rows: ${skipped.length}`);
  console.log('Layout families:');
  for (const [family, count] of [...layoutCounts.entries()].sort((a, b) => b[1] - a[1])) {
    console.log(`  ${family}: ${count}`);
  }

  if (args.macrosOnly) {
    // Preview mode: show which rows would change and what their new macros would be
    if (args.dryRun) {
      const targetDb = new Database(args.target, { readonly: true });
      const getStmt = targetDb.prepare('SELECT config_json FROM configs WHERE id = ?');
      let wouldPatch = 0, wouldUnchange = 0, absent = 0;
      for (const row of rows) {
        const existing = getStmt.get(row.id);
        if (!existing) { absent++; continue; }
        const existingConfig = JSON.parse(existing.config_json);
        const newConfig = JSON.parse(row.config_json);
        if (JSON.stringify(existingConfig.macroButtons ?? []) === JSON.stringify(newConfig.macroButtons)) {
          wouldUnchange++;
        } else {
          if (wouldPatch < 10) {
            const old = (existingConfig.macroButtons ?? []).map(m => m.label);
            const next = newConfig.macroButtons.map(m => m.label);
            console.log(`  ${row.profile_name}`);
            console.log(`    before: [${old.join(', ') || '—'}]`);
            console.log(`    after:  [${next.join(', ') || '—'}]`);
          }
          wouldPatch++;
        }
      }
      console.log(`\nDry run: would patch ${wouldPatch}, leave unchanged ${wouldUnchange}, absent ${absent}`);
      targetDb.close();
      qsDb.close();
      return;
    }

    const targetDb = new Database(args.target);
    const getStmt = targetDb.prepare('SELECT config_json FROM configs WHERE id = ?');
    const updateStmt = targetDb.prepare('UPDATE configs SET config_json = ? WHERE id = ?');

    let patched = 0, unchanged = 0, absent = 0;
    const patchAll = targetDb.transaction(items => {
      for (const row of items) {
        const existing = getStmt.get(row.id);
        if (!existing) { absent++; continue; }
        const existingConfig = JSON.parse(existing.config_json);
        const newConfig = JSON.parse(row.config_json);
        const oldSig = JSON.stringify(existingConfig.macroButtons ?? []);
        const newSig = JSON.stringify(newConfig.macroButtons);
        if (oldSig === newSig) { unchanged++; continue; }
        existingConfig.macroButtons = newConfig.macroButtons;
        updateStmt.run(JSON.stringify(existingConfig), row.id);
        patched++;
      }
    });
    patchAll(rows);

    console.log(`Patched macroButtons: ${patched} updated, ${unchanged} unchanged, ${absent} not in target DB`);
    if (patched > 0) {
      console.log('\nSample of patched configs (first 5 with macros):');
      let shown = 0;
      for (const row of rows) {
        if (shown >= 5) break;
        const newConfig = JSON.parse(row.config_json);
        if (!newConfig.macroButtons.length) continue;
        console.log(`  ${row.profile_name}  →  [${newConfig.macroButtons.map(m => m.label).join(', ')}]`);
        shown++;
      }
    }
    targetDb.close();
    qsDb.close();
    return;
  }

  if (args.dryRun) {
    if (rows[0]) {
      console.log('\nFirst converted row preview:');
      console.log(JSON.stringify({
        id: rows[0].id,
        profile_name: rows[0].profile_name,
        tags: JSON.parse(rows[0].tags),
        category: rows[0].category,
        description: rows[0].description,
        config_json: JSON.parse(rows[0].config_json),
      }, null, 2));
    }
    qsDb.close();
    return;
  }

  const targetDb = new Database(args.target);
  const verb = args.replace ? 'INSERT OR REPLACE' : 'INSERT OR IGNORE';
  const insert = targetDb.prepare(`
    ${verb} INTO configs (
      id, schema_version, platform, mode, profile_name, description, tags, category,
      app_version, config_json, uploaded_at, download_count,
      device_name, device_hw_id, device_os_version, device_os_api_level,
      device_screen_width_px, device_screen_height_px, device_screen_density_dpi,
      device_screen_diagonal_in
    ) VALUES (
      @id, @schema_version, @platform, @mode, @profile_name, @description, @tags, @category,
      @app_version, @config_json, @uploaded_at, @download_count,
      @device_name, @device_hw_id, @device_os_version, @device_os_api_level,
      @device_screen_width_px, @device_screen_height_px, @device_screen_density_dpi,
      @device_screen_diagonal_in
    )
  `);

  const write = targetDb.transaction(items => {
    let changed = 0;
    for (const row of items) changed += insert.run(row).changes;
    return changed;
  });
  const changed = write(rows);

  console.log(`Inserted/replaced rows: ${changed}`);
  console.log(`Target DB: ${args.target}`);
  qsDb.close();
  targetDb.close();
}

try {
  main();
} catch (error) {
  console.error(error.message);
  process.exit(1);
}
