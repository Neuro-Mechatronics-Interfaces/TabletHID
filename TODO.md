# TODO

## Phase 1 — Initial implementation ✅

- [x] HID descriptor byte arrays (mouse + gamepad, combined single-bond descriptor)
- [x] `HidManager` — `BluetoothHidDevice` proxy, StateFlow state machine
- [x] `HidViewModel` — activity-scoped, owns HidManager lifetime
- [x] `HomeFragment` — mode selection UI
- [x] `TutorialFragment` — Windows / macOS pairing instructions, discoverable button
- [x] Navigation graph with home → tutorial → control fragments
- [x] Bluetooth runtime permissions (API 31+)
- [x] Adaptive launcher icons
- [x] System navigation bar inset handling (Android 15 edge-to-edge)

## Phase 1b — Touch Mouse config ✅

- [x] Touch / Mouse mode toggle
- [x] Sensitivity slider (1–10)
- [x] Per-button enable toggles (Left, Right)
- [x] Static zone — drag to place fixed click rectangle
- [x] Dynamic zone — click pad follows pointer at configurable offset and radius
- [x] Momentary / Latching behavior per button
- [x] Multi-pointer routing
- [x] First-touch zone hit-testing — checks zones before assigning movement pointer on `ACTION_DOWN`
- [x] Sub-pixel accumulation (`accumDx`/`accumDy`) to prevent quantization noise in mouse movement
- [x] Zone-edit flow with rubber-band overlay and Cancel
- [x] Dynamic zone auto-calibrate — 3-step finger placement derives offset + radius for L/R buttons
- [x] `TouchZoneOverlayView` — touch surface + canvas renderer
- [x] Config sheet (BottomSheetDialogFragment); live-pushes changes to ViewModel
- [x] Persist `TouchMouseConfig` to SharedPreferences (survives app restart)

## Phase 2 — Profile system ✅

- [x] `Profile` data class with built-in profiles: Default, Access – Basic, Access – Advanced
- [x] `ProfileStore` — persists active profile key + custom profile list
- [x] Profile-namespaced config storage (`gamepad_config_<key>`, `touch_mouse_config_<key>`)
- [x] `__saved` sentinel — distinguishes "never configured" from "explicitly saved"
- [x] Raw resource default configs (`res/raw/gamepad_config_<key>.xml`) loaded on first use
- [x] Per-profile Kotlin code defaults (Access Basic disables triggers/D-pad; Access Advanced uses latching)
- [x] Custom profile creation via "+" dialog; persists across sessions
- [x] Profile chip selector on Home screen (HorizontalScrollView + ChipGroup)
- [x] Home screen redesign — icon-forward mode cards (mouse/gamepad icons)

## Phase 2 — Gamepad controls ✅

- [x] Drag to reposition controls (edit mode with banner overlay + Done button)
- [x] Pinch to resize — independent H/V scaling (buttons up to 4×, joysticks up to 3×)
- [x] Persist layout per profile to SharedPreferences
- [x] Multiple layout presets via profile system
- [x] Analog triggers — LT/RT as Z/Rz axes (0–255); drag to modulate; configurable travel distance (30–300 dp) and drag direction (▲▼◀▶)
- [x] Trigger visual glow — alpha interpolates 0x33→0xFF with trigger level
- [x] Button enable/disable per profile
- [x] Momentary / Latching click behavior per button
- [x] Turbo (auto-repeat) with configurable press duration and repeat interval
- [x] Joystick deadzone and gain sliders
- [x] D-pad diagonal input (NE/NW/SE/SW hat codes)
- [x] Back/Start button text auto-sizing (prevents clipping)

## Phase 2 — Settings & app controls ✅

- [x] Settings dialog — Appearance (system/light/dark) + session logging toggle + orientation lock
- [x] Session logging — `.config` snapshot + timestamped `.log` of all HID events written to app storage on each connection
- [x] Orientation lock — System/Portrait/Landscape preference; in-canvas cycle button on both Touch Mouse and Gamepad status bars; applies immediately via `requestedOrientation`

## Phase 2 — Connection & pairing ✅

- [x] Smart reconnect — detects bonded device on Tutorial screen; shows Reconnect button to skip full pairing flow
- [x] Sticky immersive mode (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) in Gamepad and Touch Mouse screens
- [x] Back-press guard with "Disconnect and exit?" dialog in control screens

## Phase 2 — HID descriptor ✅

- [x] Combined mouse + gamepad descriptor on single Bluetooth bond (Report ID 1 = mouse, Report ID 2 = gamepad)
- [x] Gamepad axis order corrected to Xbox-standard: X, Y → Z, Rz (triggers) → Rx, Ry (right stick) for correct host-side axis mapping

## Phase 2 — Developer tooling ✅

- [x] `DEV_MODE` build flag
- [x] Export Gamepad Config button — shares current profile's SharedPreferences XML
- [x] Export All Configs button — shares all config files concatenated
- [x] Profile-namespaced export filenames

---

## Remaining / Phase 3

### Connection & pairing

- [ ] **Foreground service** — BT connection survives Home button press / screen-off
- [ ] **Auto-reconnect** — optionally reconnect to last paired device on app open
- [ ] **Persistent notification** — quick-disconnect action while connected
- [ ] **"Forget device" option** — remove bond without full disconnect flow

### UX & polish

- [ ] **Screen pinning** (`startLockTask()`) — optional advanced lock-in; toggle in Settings
- [ ] **Settings fragment** — device name, global preferences (basic settings dialog exists; no dedicated fragment yet)
- [ ] **Onboarding screen** — shown once on first launch
- [x] **Orientation lock** — System / Portrait / Landscape, accessible from Settings dialog and in-canvas status bar
- [ ] **Connection status chip** — persistent across all fragments

### Quality / CI

- [ ] Unit tests for HID report byte construction
- [ ] Integration test stubs for `HidManager` state machine
- [ ] ProGuard/R8 rules for production build
- [ ] GitHub Actions CI workflow
