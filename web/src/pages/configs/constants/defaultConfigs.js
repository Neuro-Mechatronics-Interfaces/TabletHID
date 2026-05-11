function makeBtn(overrides = {}) {
  return { enabled: true, behavior: 'MOMENTARY', turbo: false, turboDurationMs: 100, turboIntervalMs: 100, offsetX: 0, offsetY: 0, scaleX: 1.0, scaleY: 1.0, ...overrides };
}

function makeJoystick(overrides = {}) {
  return { enabled: true, deadzone: 0.05, gain: 1.0, offsetX: 0, offsetY: 0, scaleX: 1.0, scaleY: 1.0, ...overrides };
}

export const DEFAULT_GAMEPAD_CONFIG = {
  buttons: {
    a: makeBtn(),
    b: makeBtn(),
    x: makeBtn(),
    y: makeBtn(),
    lb: makeBtn(),
    rb: makeBtn(),
    lt: makeBtn({ triggerTravelDp: 80, triggerAxis: 'UP' }),
    rt: makeBtn({ triggerTravelDp: 80, triggerAxis: 'UP' }),
    back: makeBtn(),
    start: makeBtn(),
    dpadUp: makeBtn(),
    dpadDown: makeBtn(),
    dpadLeft: makeBtn(),
    dpadRight: makeBtn(),
  },
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

export const DEFAULT_TOUCH_MOUSE_CONFIG = {
  mode: 'MOUSE',
  sensitivity: 5,
  scrollEnabled: true,
  invertScroll: false,
  sharedDynamicZone: false,
  sharedDynamic: { offsetX: 0.5, offsetY: 0.5, radius: 0.15 },
  leftButton: {
    enabled: true,
    zoneType: 'STATIC',
    behavior: 'MOMENTARY',
    staticZone: { left: 0, top: 0.75, right: 0.5, bottom: 1.0 },
    dynamicZone: { offsetX: 0.25, offsetY: 0.87, radius: 0.08 },
    subRegions: [],
  },
  rightButton: {
    enabled: true,
    zoneType: 'STATIC',
    behavior: 'MOMENTARY',
    staticZone: { left: 0.5, top: 0.75, right: 1.0, bottom: 1.0 },
    dynamicZone: { offsetX: 0.75, offsetY: 0.87, radius: 0.08 },
    subRegions: [],
  },
  sniper: {
    enabled: false,
    zone: { left: 0.0, top: 0.0, right: 0.15, bottom: 0.25 },
    divisor: 3.0,
  },
  macroHostDefaults: 'WINDOWS',
  macroButtons: [],
};
