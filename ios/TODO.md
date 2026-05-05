# iOS TODO

## Phase 0 - Project Bring-Up

- [x] Create an iOS project under `ios/` that opens in Xcode.
- [x] Add XcodeGen config (`project.yml`) so the project can be regenerated predictably.
- [x] Add SwiftUI app entry point and navigation shell.
- [x] Add iOS model equivalents for device modes, profiles, touch mouse config, and gamepad config.
- [x] Add profile-scoped `UserDefaults` persistence.
- [x] Port mouse/gamepad HID descriptors and report builders.
- [x] Add report-builder unit tests.
- [x] Verify simulator app build with `xcodebuild build`.
- [x] Verify test bundle compilation with `xcodebuild build-for-testing`.
- [ ] Run unit tests successfully in simulator. Current local blocker: `xcodebuild test` builds, then simulator launch fails with `NSMachErrorDomain Code=-308`.

## Phase 1 - Transport Strategy

- [x] Add `HIDTransport` abstraction so UI/report work is decoupled from the actual sender.
- [x] Add `NoopHIDTransport` that makes the iOS public API limitation visible in-app.
- [x] Add `ExperimentalBLEHIDTransport` using the expanded HID service UUID workaround (`00001812-0000-1000-8000-00805F9B34FB`) and HID-over-GATT characteristics.
- [x] Wire experimental BLE HID transport into `AppState`.
- [x] Add iOS quick reconnect flow that remembers the last subscribed host and restarts advertising for that host.
- [ ] Test `ExperimentalBLEHIDTransport` on a physical iPhone/iPad against macOS, Windows, Android, and iPadOS hosts.
- [ ] Confirm whether hosts subscribe to the Report characteristics and accept mouse/gamepad reports from the combined report map.
- [ ] If combined mouse+gamepad fails, split iOS advertisement into keyboard/mouse-only, mouse-only, and gamepad-only BLE HID variants.
- [ ] Add transport diagnostics UI/logging for `didAdd`, advertising, read/write requests, subscriptions, and `updateValue` backpressure.
- [ ] Evaluate pairing/reconnect behavior after app restart, force quit, host forget-device, and iOS Bluetooth toggle.
- [ ] Confirm whether Windows/macOS automatically reattach when iOS enters reconnect advertising mode, or whether the host still needs a manual Connect click.
- [ ] Decide whether read/write permissions need encryption-required flags for stable host pairing.
- [ ] Decide the real iOS transport path:
  - Public iOS Bluetooth HID peripheral mode is not clearly documented as supported; the expanded UUID path may be an undocumented workaround.
  - Core Bluetooth can be explored for custom BLE/GATT transport, but that would require a companion host app/driver and would not pair as a normal mouse/gamepad.
  - MFi/accessory or external hardware bridge could preserve native host HID behavior.
  - macOS companion app with network transport could be a practical development path for Mac hosts.
- [ ] Update setup screen once the transport path is chosen.

## Phase 1 - Touch Mouse

- [x] Add initial full-screen touch surface.
- [x] Add Touch mode: primary touch sends movement with a mouse button held.
- [x] Add Touch mode bottom right-click zone.
- [x] Add initial double-tap double-click behavior.
- [x] Add Mouse mode: trackpad-style delta movement.
- [x] Add static/dynamic left and right click zones.
- [x] Add momentary/latching zone state.
- [x] Fix first-touch zone assignment — check zones before assigning movement pointer in `beginMouseMode`.
- [x] Process coalesced touches (`event?.coalescedTouches`) and accumulate sub-pixel deltas for smooth movement.
- [x] Add settings sheet for mode, sensitivity, dynamic zone offset/radius, and button behavior.
- [x] Add dynamic zone auto-calibrate — 3-step finger placement flow (`CalibrationSurface` UIViewRepresentable + Coordinator) derives offset + radius for L/R buttons.
- [ ] Add static zone edit flow matching Android's rubber-band editor.
- [ ] Add two-finger scroll.
- [ ] Add middle click gesture.
- [ ] Add haptic feedback.

## Phase 1 - Gamepad

- [x] Add initial landscape-friendly gamepad view.
- [x] Add two SwiftUI analog joysticks.
- [x] Add A/B/X/Y, LB/RB, LT/RT, Back/Start buttons.
- [x] Add D-pad hat mapping with diagonals.
- [x] Send 13-byte gamepad reports through `HIDTransport`.
- [ ] Wire per-button enable/disable settings into visible controls.
- [ ] Wire latching and turbo behavior into button controls.
- [ ] Implement analog trigger drag travel like Android.
- [ ] Add settings sheet for button behavior, turbo, trigger travel, joystick deadzone/gain.
- [ ] Add drag-to-reposition layout edit mode.
- [ ] Add pinch-to-resize layout edit mode.
- [ ] Persist edited gamepad layout.

## Phase 2 - Parity And Polish

- [x] Session logging — `SessionLogger` writes `.config` snapshot + timestamped `.log` of all HID events to `Documents/sessions/` on each connection; toggle in Settings.
- [x] Appearance / dark mode — gear icon on Home opens `AppSettingsView`; segmented picker (System / Light / Dark) drives `preferredColorScheme`.
- [x] Orientation lock — `OrientationLock` enum (System / Portrait / Landscape); `AppDelegate` + `UIApplicationDelegateAdaptor`; `UIWindowScene.requestGeometryUpdate` (iOS 16+); picker in Settings; cycle button in Touch Mouse and Gamepad canvas bars.
- [ ] Bring built-in Android XML defaults over to iOS as bundled JSON or plist defaults.
- [ ] Add profile import/export to aid parity testing.
- [ ] Add onboarding copy explaining iOS transport limits and development mode.
- [x] Add app icon asset catalog.
- [ ] Add device signing notes after a development team is selected in Xcode.
- [ ] Add CI for iOS build/test once simulator test launch is stable.

## Phase 3 - TestFlight And App Store

- [x] Confirm experimental BLE HID-over-GATT can pair with and control a Windows host from the Mac build.
- [x] Add `PrivacyInfo.xcprivacy` for app-local `UserDefaults` usage and no declared data collection.
- [x] Add App Store/TestFlight release checklist in `ios/APP_STORE.md`.
- [ ] Confirm the permanent bundle ID before creating the App Store Connect app record.
- [x] Add complete AppIcon asset catalog.
- [ ] Validate BLE HID pairing/control on a physical iPhone or iPad.
- [ ] Validate behavior after host forget-device, app relaunch, Bluetooth toggle, backgrounding, and screen lock.
- [ ] Create App Store Connect app record.
- [ ] Add privacy policy and support URLs.
- [ ] Capture iPhone and iPad screenshots.
- [ ] Archive and upload first TestFlight build.
- [ ] Write App Review notes explaining host pairing and testing steps.
