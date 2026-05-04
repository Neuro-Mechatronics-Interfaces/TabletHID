# TODO

## Phase 1 — Initial implementation ✅

- [x] HID descriptor byte arrays (mouse + gamepad)
- [x] `HidManager` — `BluetoothHidDevice` proxy, StateFlow state machine
- [x] `HidViewModel` — activity-scoped, owns HidManager lifetime
- [x] `HomeFragment` — mode selection UI
- [x] `TutorialFragment` — Windows / macOS pairing instructions, discoverable button
- [x] Navigation graph with home → tutorial → control fragments
- [x] Bluetooth runtime permissions (API 31+ BLUETOOTH_CONNECT)
- [x] Auto-unpair on mode switch and on activity finish
- [x] Adaptive launcher icons (dark navy background, mouse + gamepad + BT badge foreground)
- [x] System navigation bar inset handling (Android 15 edge-to-edge)

## Phase 1b — Touch Mouse config drawer ✅

- [x] **Touch / Mouse mode toggle** — Touch mode preserves original tap-to-click behaviour; Mouse mode is trackpad-style (delta only on MOVE, no jump on lift)
- [x] **Sensitivity slider** (1–10, integer steps) — scales movement deltas in Mouse mode (`sensitivity × 0.3` multiplier)
- [x] **Per-button enable toggles** — Left Button (L) and Right Button (R) independently enabled/disabled in Mouse mode
- [x] **Static zone** — user drags a rectangle on-screen to define the click pad; fractional coordinates persist in `TouchMouseConfig`; zone is visually drawn and lights up (blue = L, orange = R)
- [x] **Dynamic zone** — click pad follows primary pointer at a configurable relative offset (X, Y) and radius; appears as a labelled circle
- [x] **Momentary / Latching behavior** per button — momentary releases on finger lift; latching toggles on each tap and stays active
- [x] **Multi-pointer routing** — first pointer = movement; subsequent pointers hit-tested against zones; multiple momentary pointers tracked independently
- [x] **Zone-edit flow** — "Set Zone" dismisses the sheet, shows a rubber-band drag overlay with hint text and Cancel button; confirms on finger lift (min 5% size guard)
- [x] **`TouchZoneOverlayView`** — single custom view serving as touch surface + canvas renderer; draws Touch-mode right-click zone, Mouse-mode static/dynamic zones, and edit rubber band
- [x] **Config sheet** (`BottomSheetDialogFragment`) — gear icon button in status bar; live-pushes changes to ViewModel StateFlow; re-reads config on reopen (no stale state)
- [x] **`TouchMouseConfig` in `HidViewModel`** — session-persistent `MutableStateFlow<TouchMouseConfig>` survives fragment navigation

## Phase 2 — Gamepad controls

- [ ] **Drag-to-reposition controls** — long-press enters "edit mode"; controls can be dragged to any position
- [ ] **Pinch-to-resize controls** — each control independently resizable in edit mode
- [ ] **Persist layout** — save/load custom layout to SharedPreferences or DataStore
- [ ] **Multiple layout presets** — user can save named layouts and switch between them
- [ ] **Analog triggers** — expose LT/RT as Z / Rz analog axes (0–255) rather than binary; add slider controls for partial press
- [ ] **Visual press feedback** — animation/ripple on virtual button press
- [ ] **D-pad diagonal input** ✅ (already implemented — simultaneous adjacent directions emit hat codes NE/NW/SE/SW)
- [ ] **Rumble / force-feedback** — handle output reports to vibrate the tablet on host-initiated rumble

## Phase 2 — Touch Mouse further polish

- [ ] **Two-finger scroll** — pinch/spread gesture on the Mouse-mode surface → scroll wheel events
- [ ] **Three-finger tap** → middle click
- [ ] **Momentum / fling** — inertia after fast swipe decays and sends additional delta reports
- [ ] **On-screen scroll wheel strip** — configurable edge strip that sends scroll wheel axis
- [x] **Persist `TouchMouseConfig`** to SharedPreferences so settings survive app restart

## Phase 2 — Connection & pairing

- [ ] **Smart reconnect for already-bonded devices** — on entering the Setup screen, check `BluetoothAdapter.bondedDevices` for a device whose name matches the current mode's `deviceName`; if found, show a "Reconnect" button that skips the full discoverable → pair flow and directly calls `connect()` on the bonded device. This avoids the current frustrating Remove → Make Discoverable → re-pair loop every session.
- [ ] **Foreground service** — BT connection survives Home button press / screen-off
- [ ] **Auto-reconnect** — optionally reconnect to last paired device on app open
- [ ] **"Forget device" option** — remove bond without full disconnect flow
- [ ] **Persistent notification** — quick-disconnect action while connected

## Phase 2 — HID descriptor variants

- [ ] **Touchpad absolute-coordinate mode** — for hosts that support HID absolute touchpad (e.g. iPadOS pointer)
- [ ] **Combined keyboard + mouse descriptor** — simultaneous keyboard input alongside mouse
- [ ] **`SUBCLASS1_KEYBOARD` enumeration option** — for hosts requiring keyboard-style PIN pairing

## Phase 2 — UX & polish

- [x] **Fix Setup wizard instructions formatting** — replaced `\n\n` with `<br/><br/>` in all four instruction strings so `Html.fromHtml` renders each numbered step on its own paragraph; also bolded the step numbers for scannability.
- [x] **Fullscreen / accidental-exit prevention in control screens** — sticky immersive mode (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) hides system bars on `onResume`, restores on `onPause`; back press intercepted with `OnBackPressedCallback` showing a "Disconnect and exit?" `MaterialAlertDialog`; "Switch Mode" button calls `disconnectAndUnbond()` (unbonds) while back-press dialog calls `disconnect()` (keeps bond for easy reconnect).
- [ ] **Screen Pinning (`startLockTask()`) option** — optional advanced lock-in; expose a toggle in Settings; requires one-time user consent in Android Security settings; after pinning, only holding Back + Recents simultaneously exits.
- [ ] **Haptic feedback** on virtual button press (Gamepad buttons, click zones)
- [ ] **Settings fragment** — device name, layout presets, global preferences
- [ ] **Onboarding screen** — shown once on first launch
- [ ] **Landscape lock toggle** — make portrait/landscape preference configurable per mode
- [ ] **Connection status chip in app bar** — persistent across all fragments

## Phase 3 — Quality / CI

- [ ] Unit tests for HID report byte construction
- [ ] Integration test stubs for `HidManager` state machine
- [ ] ProGuard/R8 rules for production build
- [ ] GitHub Actions CI workflow
