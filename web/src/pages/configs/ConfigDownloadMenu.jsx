import { useState } from 'react';

const GAMEPAD_BUTTON_PREFS = {
  a: 'a',
  b: 'b',
  x: 'x',
  y: 'y',
  lb: 'lb',
  rb: 'rb',
  lt: 'lt',
  rt: 'rt',
  back: 'back',
  start: 'start',
  dpadUp: 'dup',
  dpadDown: 'ddown',
  dpadLeft: 'dleft',
  dpadRight: 'dright',
};

const GAMEPAD_BUTTON_KEYS = Object.keys(GAMEPAD_BUTTON_PREFS);

function safeFilePart(value) {
  return String(value || 'config')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 48) || 'config';
}

function xmlEscape(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function prefString(name, value) {
  return `    <string name="${xmlEscape(name)}">${xmlEscape(value)}</string>`;
}

function prefBool(name, value) {
  return `    <boolean name="${xmlEscape(name)}" value="${value ? 'true' : 'false'}" />`;
}

function prefInt(name, value) {
  return `    <int name="${xmlEscape(name)}" value="${Math.trunc(Number(value) || 0)}" />`;
}

function prefFloat(name, value) {
  return `    <float name="${xmlEscape(name)}" value="${Number.isFinite(Number(value)) ? Number(value) : 0}" />`;
}

function buttonPrefs(prefix, button = {}) {
  return [
    prefBool(`${prefix}_en`, button.enabled !== false),
    prefString(`${prefix}_beh`, button.behavior || 'MOMENTARY'),
    prefBool(`${prefix}_trb`, Boolean(button.turbo)),
    prefInt(`${prefix}_trd`, button.turboDurationMs ?? 50),
    prefInt(`${prefix}_tri`, button.turboIntervalMs ?? 100),
    prefFloat(`${prefix}_ox`, button.offsetX ?? 0),
    prefFloat(`${prefix}_oy`, button.offsetY ?? 0),
    prefFloat(`${prefix}_scx`, button.scaleX ?? 1),
    prefFloat(`${prefix}_scy`, button.scaleY ?? 1),
    prefFloat(`${prefix}_ttd`, button.triggerTravelDp ?? 150),
    prefString(`${prefix}_tax`, button.triggerAxis || 'UP'),
  ];
}

function joystickPrefs(prefix, joystick = {}) {
  return [
    prefBool(`${prefix}_en`, joystick.enabled !== false),
    prefFloat(`${prefix}_dz`, joystick.deadzone ?? 0.08),
    prefFloat(`${prefix}_gn`, joystick.gain ?? 1),
    prefFloat(`${prefix}_ox`, joystick.offsetX ?? 0),
    prefFloat(`${prefix}_oy`, joystick.offsetY ?? 0),
    prefFloat(`${prefix}_scx`, joystick.scaleX ?? 1),
    prefFloat(`${prefix}_scy`, joystick.scaleY ?? 1),
  ];
}

function macroPrefs(macros = []) {
  const lines = [prefInt('macro_count', macros.length)];
  macros.forEach((macro, index) => {
    const prefix = `macro_${index}`;
    lines.push(
      prefString(`${prefix}_label`, macro.label || ''),
      prefInt(`${prefix}_modifiers`, macro.modifiers ?? 0),
      prefString(`${prefix}_keys`, (macro.keyUsages ?? []).join(',')),
      prefFloat(`${prefix}_lox`, macro.layoutOffsetX ?? 0),
      prefFloat(`${prefix}_loy`, macro.layoutOffsetY ?? 0),
      prefFloat(`${prefix}_lsx`, macro.layoutScaleX ?? 1),
      prefFloat(`${prefix}_lsy`, macro.layoutScaleY ?? 1),
    );
  });
  return lines;
}

function zonePrefs(prefix, button = {}) {
  const staticZone = button.staticZone ?? {};
  const dynamicZone = button.dynamicZone ?? {};
  const subRegions = button.subRegions ?? [];
  const lines = [
    prefBool(`${prefix}_enabled`, Boolean(button.enabled)),
    prefString(`${prefix}_zone_type`, button.zoneType || 'STATIC'),
    prefString(`${prefix}_behavior`, button.behavior || 'MOMENTARY'),
    prefFloat(`${prefix}_s_left`, staticZone.left ?? 0),
    prefFloat(`${prefix}_s_top`, staticZone.top ?? 0.75),
    prefFloat(`${prefix}_s_right`, staticZone.right ?? 0.25),
    prefFloat(`${prefix}_s_bottom`, staticZone.bottom ?? 1),
    prefFloat(`${prefix}_d_ox`, dynamicZone.offsetX ?? 0),
    prefFloat(`${prefix}_d_oy`, dynamicZone.offsetY ?? 0),
    prefFloat(`${prefix}_d_radius`, dynamicZone.radius ?? 0.07),
    prefInt(`${prefix}_subregion_count`, subRegions.length),
  ];
  subRegions.forEach((region, index) => {
    lines.push(...subRegionPrefs(`${prefix}_sub_${index}`, region));
  });
  return lines;
}

function subRegionPrefs(prefix, region = {}) {
  const staticZone = region.staticZone ?? {};
  const dynamicZone = region.dynamicZone ?? {};
  return [
    prefBool(`${prefix}_enabled`, region.enabled !== false),
    prefString(`${prefix}_zone_type`, region.zoneType || 'STATIC'),
    prefFloat(`${prefix}_s_left`, staticZone.left ?? 0),
    prefFloat(`${prefix}_s_top`, staticZone.top ?? 0),
    prefFloat(`${prefix}_s_right`, staticZone.right ?? 0),
    prefFloat(`${prefix}_s_bottom`, staticZone.bottom ?? 0),
    prefFloat(`${prefix}_d_ox`, dynamicZone.offsetX ?? 0),
    prefFloat(`${prefix}_d_oy`, dynamicZone.offsetY ?? 0),
    prefFloat(`${prefix}_d_radius`, dynamicZone.radius ?? 0.07),
    prefInt(`${prefix}_keyboard_modifiers`, region.keyboardModifiers ?? 0),
    region.alternateMouseButton == null
      ? prefString(`${prefix}_alternate_mouse_button`, '')
      : prefString(`${prefix}_alternate_mouse_button`, region.alternateMouseButton),
  ];
}

function wrapPrefsXml(lines) {
  return [
    "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>",
    '<map>',
    ...lines,
    '</map>',
    '',
  ].join('\n');
}

function gamepadXml(config = {}) {
  const lines = [];
  for (const key of GAMEPAD_BUTTON_KEYS) {
    lines.push(...buttonPrefs(GAMEPAD_BUTTON_PREFS[key], config.buttons?.[key]));
  }
  lines.push(
    ...joystickPrefs('left', config.leftJoystick),
    ...joystickPrefs('right', config.rightJoystick),
    prefBool('single_joystick_mode', Boolean(config.singleJoystickMode)),
    prefBool('single_joystick_side_toggle_enabled', Boolean(config.singleJoystickSideToggleEnabled)),
    prefString('single_joystick_output_side', config.singleJoystickOutputSide || 'LEFT'),
    prefString('macro_host_defaults', config.macroHostDefaults || 'WINDOWS'),
    ...macroPrefs(config.macroButtons ?? []),
    prefString('vibration_intensity', config.vibrationIntensity || 'OFF'),
  );
  for (const [key, label] of Object.entries(config.customButtonLabels ?? {})) {
    lines.push(prefString(`label_${GAMEPAD_BUTTON_PREFS[key] ?? key}`, label));
  }
  return wrapPrefsXml(lines);
}

function touchMouseXml(config = {}) {
  const shared = config.sharedDynamic ?? {};
  const sniper = config.sniper ?? {};
  return wrapPrefsXml([
    prefString('mode', config.mode || 'TOUCH'),
    prefInt('sensitivity', config.sensitivity ?? 5),
    prefBool('scroll_enabled', config.scrollEnabled !== false),
    prefBool('invert_scroll', Boolean(config.invertScroll)),
    prefBool('shared_dynamic_zone', Boolean(config.sharedDynamicZone)),
    prefFloat('shared_dynamic_ox', shared.offsetX ?? 0),
    prefFloat('shared_dynamic_oy', shared.offsetY ?? 0.18),
    prefFloat('shared_dynamic_radius', shared.radius ?? 0.08),
    prefBool('sniper_enabled', Boolean(sniper.enabled)),
    prefFloat('sniper_left', sniper.zone?.left ?? 0.35),
    prefFloat('sniper_top', sniper.zone?.top ?? 0.88),
    prefFloat('sniper_right', sniper.zone?.right ?? 0.65),
    prefFloat('sniper_bottom', sniper.zone?.bottom ?? 1),
    prefFloat('sniper_divisor', sniper.divisor ?? 4),
    prefString('macro_host_defaults', config.macroHostDefaults || 'WINDOWS'),
    ...macroPrefs(config.macroButtons ?? []),
    ...zonePrefs('l', config.leftButton),
    ...zonePrefs('r', config.rightButton),
  ]);
}

function downloadFile(filename, content, type) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function canonicalJson(record) {
  return JSON.stringify({
    schema_version: record.schema_version ?? 1,
    platform: record.platform,
    mode: record.mode,
    profile_name: record.profile_name,
    description: record.description ?? '',
    tags: record.tags ?? [],
    category: record.category ?? '',
    app_version: record.app_version ?? '',
    config_json: record.config_json,
  }, null, 2);
}

export default function ConfigDownloadMenu({ configRecord }) {
  const [open, setOpen] = useState(false);
  if (!configRecord?.config_json) return null;

  const baseName = safeFilePart(configRecord.profile_name);
  const rawPrefix = configRecord.mode === 'gamepad' ? 'gamepad_config' : 'touch_mouse_config';

  function downloadJson() {
    downloadFile(`${baseName}.json`, canonicalJson(configRecord), 'application/json');
    setOpen(false);
  }

  function downloadAndroidXml() {
    const xml = configRecord.mode === 'gamepad'
      ? gamepadXml(configRecord.config_json)
      : touchMouseXml(configRecord.config_json);
    downloadFile(`${rawPrefix}_${baseName}.xml`, xml, 'application/xml');
    setOpen(false);
  }

  return (
    <div className="download-menu">
      <button
        className="btn btn-outline download-menu-toggle"
        type="button"
        onClick={() => setOpen(v => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
      >
        Download As...
      </button>
      {open && (
        <div className="download-menu-popover" role="menu">
          <button type="button" role="menuitem" onClick={downloadJson}>
            Canonical JSON
          </button>
          <button type="button" role="menuitem" onClick={downloadAndroidXml}>
            Android raw XML
          </button>
        </div>
      )}
    </div>
  );
}
