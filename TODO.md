# TODO

Use this root TODO as the product-wide backlog and Android implementation tracker. Keep platform-specific details in that platform's TODO file, currently `ios/TODO.md`, and update `spec/platform-feature-status.md` when a TODO becomes implemented or intentionally deferred.

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

- [x] Settings dialog — configurable Bluetooth name, Appearance (system/light/dark), Large Text, High Contrast, session logging toggle, and orientation lock
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

- [x] **Foreground service** — BT connection survives Home button press / screen-off
- [x] **Auto-reconnect** — optionally reconnect to last paired device on app open
- [x] **Persistent notification** — quick-disconnect action while connected
- [x] **"Forget device" option** — remove bond without full disconnect flow

### UX & polish

- [x] **Screen pinning** (`startLockTask()`) — optional advanced lock-in; toggle in Settings
- [ ] **Settings fragment** — optional dedicated settings screen if the dialog becomes too dense
- [x] **Onboarding screen** — shown once on first launch
- [x] **Orientation lock** — System / Portrait / Landscape, accessible from Settings dialog and in-canvas status bar
- [x] **Connection status chip** — persistent across all fragments

### Input customization and HID expansion

- [x] **Single dynamic touch follower** - add a Touch Mouse setting that uses one shared dynamic follower location for whichever mouse button zones are configured as dynamic, instead of separate follower locations per button.
- [x] **Unlimited gamepad widget sizing** - remove maximum size constraints from all gamepad widgets so users can resize any widget dimension as large as they want.
- [x] **Single-joystick gamepad layout mode** - add a gamepad configuration toggle that shows only one joystick on the layout.
- [x] **Runtime joystick output side toggle** - when single-joystick layout mode is enabled, add an optional in-layout toggle button next to the joystick that switches whether the current joystick values map to left-stick or right-stick fields in outgoing HID gamepad reports.
- [x] **Stable gamepad HID reports under layout toggles** - ensure enabling/disabling buttons or joysticks never changes the HID report descriptor or report byte structure; inactive controls should emit neutral/unpressed values.
- [x] **Keyboard HID report support** - add a third standard keyboard report collection/descriptor to the combined HID report map so TabletHID can send keyboard key combinations without changing the mouse/gamepad reports.
- [x] **Keyboard macro buttons on existing layouts** - allow users to add preset macro buttons to Touch Mouse and Gamepad layouts; pressing one sends the configured keyboard combination through the keyboard report.
- [x] **Keyboard macro defaults by host OS** - pre-populate macro choices with common Windows and Mac defaults, such as Alt+Tab or Cmd+Tab and Ctrl+S or Cmd+S, with an option to choose the target defaults.
- [x] **Custom keyboard macro editor** - allow users to create arbitrary keyboard combinations beyond the built-in Windows/Mac preset macro buttons.
- [x] **Overlapping static mouse zones combine buttons** - when static or dynamic Touch Mouse button regions overlap, touching the overlap sends a mouse report with every overlapping mouse button pressed.
- [x] **Multiple mouse button sub-regions** - add/edit/clear static Touch Mouse sub-regions from the Android settings sheet.
- [x] **Mouse sub-region modifiers** - support Android middle-click alternate sub-regions and Ctrl modifier sub-regions through mouse plus keyboard reports.

### Quality / CI

- [x] Unit tests for HID report byte construction
- [x] Integration test stubs for `HidManager` state machine
- [x] ProGuard/R8 rules for production build
- [x] GitHub Actions CI workflow

---

## Future Tech / Phase 4

### Improved Gestures
- [ ] **A "hold" gesture (e.g., double-tap and hold)** - latches a mouse button pressed until the user taps again. Useful for long drags without maintaining continuous finger contact. Could be a toggle within the dynamic zone config or a dedicated gesture zone.

- [ ] **Sensitivity adjuster** - A zone or button that, while held, applies a temporary sensitivity divisor (e.g., 0.25×) for fine cursor placement — analogous to a "sniper button" on gaming mice. Could be a static zone in Touch Mouse mode or a macro button on the Gamepad layout.

### Haptics
- [ ] **Vibrotactile feedback** - Short vibration pulses via `Vibrator`/`VibrationEffect` when pressing gamepad buttons (A, B, X, Y, LB, RB, triggers). Configurable intensity (off / light / medium / strong) per profile. Care needed to keep vibration latency low and not disturb button state.

### Customization 
- [ ] **Keyboard shortcut launcher panel** - A scrollable grid of user-defined or preset keyboard shortcuts (Ctrl+Z, Ctrl+C, Win+D, Alt+F4, etc.) accessible as a side-panel or overlay within Touch Mouse mode. Each button sends one `sendKeyboardReport` call. Could reuse the existing macro button infrastructure.

- [ ] **Custom button icons or labels** - Allow users to rename any gamepad button (e.g., relabel A/B/X/Y for a specific game) or pick from a small icon library. Stored per profile. Purely cosmetic; no HID report changes.

- [ ] **App home screen shortcuts** - Android app shortcuts (`shortcuts.xml`) that launch directly into Touch Mouse or Gamepad mode with a specific profile, bypassing the Home fragment. Users long-press the launcher icon to pick a shortcut. Shortcut list could be auto-generated from the saved profile list.

- [ ] **Android home screen widget** - A resizable `AppWidgetProvider` widget that shows connection status (device name + connected/idle) and provides a single Reconnect or Disconnect button, without opening the full app. Complements the Quick Settings tile for users who prefer widgets.