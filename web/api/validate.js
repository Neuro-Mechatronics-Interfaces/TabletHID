// Internal helpers — not exported

function isFiniteNumber(v) {
  return typeof v === 'number' && isFinite(v);
}

function isPositiveInteger(v) {
  return Number.isInteger(v) && v > 0;
}

function isNonNegativeInteger(v) {
  return Number.isInteger(v) && v >= 0;
}

function isBoolean(v) {
  return typeof v === 'boolean';
}

function isSniperZone(o) {
  if (o === null || typeof o !== 'object' || Array.isArray(o)) return false;
  for (const k of ['left', 'top', 'right', 'bottom']) {
    if (!isFiniteNumber(o[k])) return false;
  }
  return true;
}

function isStaticZone(o) {
  if (o === null || typeof o !== 'object' || Array.isArray(o)) return false;
  for (const k of ['left', 'top', 'right', 'bottom']) {
    if (!isFiniteNumber(o[k])) return false;
  }
  return true;
}

function isDynamicZone(o) {
  if (o === null || typeof o !== 'object' || Array.isArray(o)) return false;
  for (const k of ['offsetX', 'offsetY', 'radius']) {
    if (!isFiniteNumber(o[k])) return false;
  }
  return true;
}

function isKeyboardMacroButtonConfig(o) {
  if (o === null || typeof o !== 'object' || Array.isArray(o)) return false;
  if (typeof o.label !== 'string' || o.label.length === 0) return false;
  if (!isNonNegativeInteger(o.modifiers)) return false;
  if (!Array.isArray(o.keyUsages) || o.keyUsages.length === 0) return false;
  for (const usage of o.keyUsages) {
    if (!isNonNegativeInteger(usage)) return false;
  }
  for (const k of ['layoutOffsetX', 'layoutOffsetY', 'layoutScaleX', 'layoutScaleY']) {
    if (k in o && !isFiniteNumber(o[k])) return false;
  }
  return true;
}

function isTouchMouseSubRegionConfig(o) {
  if (o === null || typeof o !== 'object' || Array.isArray(o)) return false;
  if (!isBoolean(o.enabled)) return false;
  if (o.zoneType !== 'STATIC' && o.zoneType !== 'DYNAMIC') return false;
  if (!isStaticZone(o.staticZone)) return false;
  if (!isDynamicZone(o.dynamicZone)) return false;
  if (!isNonNegativeInteger(o.keyboardModifiers)) return false;
  if (o.alternateMouseButton !== null &&
      o.alternateMouseButton !== 'LEFT' &&
      o.alternateMouseButton !== 'RIGHT' &&
      o.alternateMouseButton !== 'MIDDLE') return false;
  return true;
}

function isButtonZoneConfig(o) {
  if (o === null || typeof o !== 'object' || Array.isArray(o)) return false;
  if (!isBoolean(o.enabled)) return false;
  if (o.zoneType !== 'STATIC' && o.zoneType !== 'DYNAMIC') return false;
  if (o.behavior !== 'MOMENTARY' && o.behavior !== 'LATCHING') return false;
  if (!isStaticZone(o.staticZone)) return false;
  if (!isDynamicZone(o.dynamicZone)) return false;
  if (!Array.isArray(o.subRegions)) return false;
  for (const sr of o.subRegions) {
    if (!isTouchMouseSubRegionConfig(sr)) return false;
  }
  return true;
}

function isButtonConfig(o) {
  if (o === null || typeof o !== 'object' || Array.isArray(o)) return false;
  if (!isBoolean(o.enabled)) return false;
  if (o.behavior !== 'MOMENTARY' && o.behavior !== 'LATCHING') return false;
  if (!isBoolean(o.turbo)) return false;
  if (!isPositiveInteger(o.turboDurationMs)) return false;
  if (!isPositiveInteger(o.turboIntervalMs)) return false;
  for (const k of ['offsetX', 'offsetY', 'scaleX', 'scaleY']) {
    if (!isFiniteNumber(o[k])) return false;
  }
  // trigger fields are optional
  if ('triggerTravelDp' in o && !isFiniteNumber(o.triggerTravelDp)) return false;
  if ('triggerAxis' in o) {
    if (o.triggerAxis !== 'UP' && o.triggerAxis !== 'DOWN' &&
        o.triggerAxis !== 'LEFT' && o.triggerAxis !== 'RIGHT') return false;
  }
  return true;
}

function isJoystickConfig(o) {
  if (o === null || typeof o !== 'object' || Array.isArray(o)) return false;
  if (!isBoolean(o.enabled)) return false;
  for (const k of ['deadzone', 'gain', 'offsetX', 'offsetY', 'scaleX', 'scaleY']) {
    if (!isFiniteNumber(o[k])) return false;
  }
  return true;
}

// Exported validators

export function validateTouchMouseConfig(obj) {
  if (obj === null || typeof obj !== 'object' || Array.isArray(obj)) {
    return 'config_json must be a plain object';
  }

  if (obj.mode !== 'TOUCH' && obj.mode !== 'MOUSE') {
    return 'mode must be "TOUCH" or "MOUSE"';
  }

  if (!Number.isInteger(obj.sensitivity) || obj.sensitivity < 1 || obj.sensitivity > 10) {
    return 'sensitivity must be an integer between 1 and 10';
  }

  if (!isBoolean(obj.scrollEnabled)) {
    return 'scrollEnabled must be a boolean';
  }

  if (!isBoolean(obj.invertScroll)) {
    return 'invertScroll must be a boolean';
  }

  if (!isBoolean(obj.sharedDynamicZone)) {
    return 'sharedDynamicZone must be a boolean';
  }

  if (!isDynamicZone(obj.sharedDynamic)) {
    return 'sharedDynamic must be an object with finite number fields offsetX, offsetY, radius';
  }

  if (!isButtonZoneConfig(obj.leftButton)) {
    return 'leftButton must be a valid ButtonZoneConfig (check enabled, zoneType, behavior, staticZone, dynamicZone, subRegions)';
  }

  if (!isButtonZoneConfig(obj.rightButton)) {
    return 'rightButton must be a valid ButtonZoneConfig (check enabled, zoneType, behavior, staticZone, dynamicZone, subRegions)';
  }

  if (obj.sniper === null || typeof obj.sniper !== 'object' || Array.isArray(obj.sniper)) {
    return 'sniper must be an object';
  }
  if (!isBoolean(obj.sniper.enabled)) {
    return 'sniper.enabled must be a boolean';
  }
  if (!isSniperZone(obj.sniper.zone)) {
    return 'sniper.zone must be an object with finite number fields left, top, right, bottom';
  }
  if (!isFiniteNumber(obj.sniper.divisor) || obj.sniper.divisor <= 0) {
    return 'sniper.divisor must be a positive finite number';
  }

  if (obj.macroHostDefaults !== 'WINDOWS' && obj.macroHostDefaults !== 'MAC') {
    return 'macroHostDefaults must be "WINDOWS" or "MAC"';
  }

  if (!Array.isArray(obj.macroButtons)) {
    return 'macroButtons must be an array';
  }
  for (let i = 0; i < obj.macroButtons.length; i++) {
    if (!isKeyboardMacroButtonConfig(obj.macroButtons[i])) {
      return `macroButtons[${i}] is not a valid KeyboardMacroButtonConfig (check label, modifiers, keyUsages)`;
    }
  }

  return null;
}

const GAMEPAD_BUTTON_KEYS = [
  'a', 'b', 'x', 'y', 'lb', 'rb', 'lt', 'rt',
  'back', 'start', 'dpadUp', 'dpadDown', 'dpadLeft', 'dpadRight',
];

export function validateGamepadConfig(obj) {
  if (obj === null || typeof obj !== 'object' || Array.isArray(obj)) {
    return 'config_json must be a plain object';
  }

  if (obj.buttons === null || typeof obj.buttons !== 'object' || Array.isArray(obj.buttons)) {
    return 'buttons must be an object';
  }
  for (const key of GAMEPAD_BUTTON_KEYS) {
    if (key in obj.buttons && !isButtonConfig(obj.buttons[key])) {
      return `buttons.${key} is not a valid ButtonConfig (check enabled, behavior, turbo, turboDurationMs, turboIntervalMs, offsetX, offsetY, scaleX, scaleY)`;
    }
  }

  if (!isJoystickConfig(obj.leftJoystick)) {
    return 'leftJoystick must be a valid JoystickConfig (check enabled, deadzone, gain, offsetX, offsetY, scaleX, scaleY)';
  }

  if (!isJoystickConfig(obj.rightJoystick)) {
    return 'rightJoystick must be a valid JoystickConfig (check enabled, deadzone, gain, offsetX, offsetY, scaleX, scaleY)';
  }

  if (!isBoolean(obj.singleJoystickMode)) {
    return 'singleJoystickMode must be a boolean';
  }

  if (!isBoolean(obj.singleJoystickSideToggleEnabled)) {
    return 'singleJoystickSideToggleEnabled must be a boolean';
  }

  if (obj.singleJoystickOutputSide !== 'LEFT' && obj.singleJoystickOutputSide !== 'RIGHT') {
    return 'singleJoystickOutputSide must be "LEFT" or "RIGHT"';
  }

  if (obj.macroHostDefaults !== 'WINDOWS' && obj.macroHostDefaults !== 'MAC') {
    return 'macroHostDefaults must be "WINDOWS" or "MAC"';
  }

  if (!Array.isArray(obj.macroButtons)) {
    return 'macroButtons must be an array';
  }
  for (let i = 0; i < obj.macroButtons.length; i++) {
    if (!isKeyboardMacroButtonConfig(obj.macroButtons[i])) {
      return `macroButtons[${i}] is not a valid KeyboardMacroButtonConfig (check label, modifiers, keyUsages)`;
    }
  }

  if (obj.vibrationIntensity !== 'OFF' &&
      obj.vibrationIntensity !== 'LIGHT' &&
      obj.vibrationIntensity !== 'MEDIUM' &&
      obj.vibrationIntensity !== 'STRONG') {
    return 'vibrationIntensity must be "OFF", "LIGHT", "MEDIUM", or "STRONG"';
  }

  if (obj.customButtonLabels === null ||
      typeof obj.customButtonLabels !== 'object' ||
      Array.isArray(obj.customButtonLabels)) {
    return 'customButtonLabels must be an object';
  }
  for (const [k, v] of Object.entries(obj.customButtonLabels)) {
    if (typeof k !== 'string' || typeof v !== 'string') {
      return 'customButtonLabels must have string keys and string values';
    }
  }

  if ('orientationPreference' in obj &&
      obj.orientationPreference !== 'SYSTEM' &&
      obj.orientationPreference !== 'LANDSCAPE' &&
      obj.orientationPreference !== 'PORTRAIT') {
    return 'orientationPreference must be "SYSTEM", "LANDSCAPE", or "PORTRAIT"';
  }

  return null;
}
