# iOS TODO

Use this platform TODO for iOS-specific implementation, parity, validation, and release work. Keep user-visible feature intent in the root `TODO.md`, and update `spec/platform-feature-status.md` when an iOS TODO becomes implemented or intentionally deferred.

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
- [x] Run unit tests successfully in simulator.

## Phase 1 - Transport Strategy

- [x] Add `HIDTransport` abstraction so UI/report work is decoupled from the actual sender.
- [x] Add `NoopHIDTransport` that makes the iOS public API limitation visible in-app.
- [x] Add `ExperimentalBLEHIDTransport` using the expanded HID service UUID workaround (`00001812-0000-1000-8000-00805F9B34FB`) and HID-over-GATT characteristics.
- [x] Wire experimental BLE HID transport into `AppState`.
- [x] Add iOS quick reconnect flow that remembers the last subscribed host and restarts advertising for that host.
- [x] Align the iOS Home connection workflow with Android: status card, Make Discoverable, Reconnect, Cancel, Disconnect, editable HoG server name, and direct mode entry.
- [x] Add opt-in auto-reconnect on app launch after onboarding is complete.
- [x] Make iOS new-pair advertising drop stale subscribed centrals, ignore known-host auto-reattach attempts, require explicit on-iPad approval for new hosts, include Device Information Service/PnP ID, and require encrypted HID report access.
- [ ] Test `ExperimentalBLEHIDTransport` on a physical iPhone/iPad against macOS, Windows, Android, and iPadOS hosts.
- [ ] Confirm whether hosts subscribe to the Report characteristics and accept mouse/gamepad reports from the combined report map.
- [ ] If combined mouse+gamepad fails, split iOS advertisement into keyboard/mouse-only, mouse-only, and gamepad-only BLE HID variants.
- [ ] Add transport diagnostics UI/logging for `didAdd`, advertising, read/write requests, subscriptions, and `updateValue` backpressure.
- [ ] Evaluate pairing/reconnect behavior after app restart, force quit, host forget-device, and iOS Bluetooth toggle.
- [ ] Confirm whether Windows/macOS automatically reattach when iOS enters reconnect advertising mode, or whether the host still needs a manual Connect click.
- [x] Decide whether read/write permissions need encryption-required flags for stable host pairing; iOS report/protocol/control characteristics now require encrypted access.
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
- [x] Add three-finger scroll with vertical wheel and horizontal AC Pan, plus enable/invert settings.
- [ ] Add static zone edit flow matching Android's rubber-band editor.
- [ ] Add middle click gesture.
- [ ] Add haptic feedback.

## Phase 1 - Gamepad

- [x] Add initial landscape-friendly gamepad view.
- [x] Add two SwiftUI analog joysticks.
- [x] Add A/B/X/Y, LB/RB, LT/RT, Back/Start buttons.
- [x] Add D-pad hat mapping with diagonals.
- [x] Send 13-byte gamepad reports through `HIDTransport`.
- [x] Wire per-button enable/disable settings into visible controls.
- [x] Wire latching and turbo behavior into button controls.
- [ ] Implement analog trigger drag travel like Android.
- [ ] Add settings sheet for button behavior, turbo, trigger travel, joystick deadzone/gain.
- [x] Add drag-to-reposition layout edit mode.
- [x] Add pinch-to-resize layout edit mode.
- [x] Persist edited gamepad layout.

## Phase 2 - Parity And Polish

- [x] Session logging — `SessionLogger` writes `.config` snapshot + timestamped `.log` of all HID events to `Documents/sessions/` on each connection; toggle in Settings.
- [x] Appearance / dark mode — gear icon on Home opens `AppSettingsView`; segmented picker (System / Light / Dark) drives `preferredColorScheme`.
- [x] User-configurable Bluetooth peripheral name — Settings stores the advertised local name and `ExperimentalBLEHIDTransport` uses it for new pairing/reconnect advertising.
- [x] First-run onboarding — mirrors the Android intro/tutorial flow, explains pairing/modes, prompts for the HoG server name, and persists completion locally.
- [x] Accessibility display toggles — Settings adds Large Text and High Contrast options; the SwiftUI root applies dynamic type and stronger rendered contrast.
- [x] Orientation lock — `OrientationLock` enum (System / Portrait / Landscape); `AppDelegate` + `UIApplicationDelegateAdaptor`; `UIWindowScene.requestGeometryUpdate` (iOS 16+); picker in Settings; cycle button in Touch Mouse and Gamepad canvas bars.
- [ ] Bring built-in Android XML defaults over to iOS as bundled JSON or plist defaults.
- [ ] Add profile import/export to aid parity testing.
- [x] Add onboarding copy explaining pairing, modes, and iOS HoG server naming.
- [x] Add app icon asset catalog.
- [ ] Add device signing notes after a development team is selected in Xcode.
- [ ] Add CI for iOS build/test once simulator test launch is stable.

## Phase 2 - Input Customization And HID Expansion

- [x] Add the Touch Mouse setting for one shared dynamic follower location when one or more button zones are configured as dynamic.
- [ ] Remove maximum size constraints from iOS gamepad widgets once layout editing exists, so any widget dimension can be resized as large as the user wants.
- [x] Add single-joystick gamepad layout mode.
- [x] In single-joystick mode, add an optional in-layout toggle beside the joystick that maps the live joystick values to either left-stick or right-stick fields in outgoing reports.
- [x] Keep the iOS gamepad HID report descriptor and report byte structure stable regardless of which buttons or joysticks are enabled, hidden, or toggled.
- [x] Add a standard keyboard report collection/descriptor to the iOS HID-over-GATT report map alongside mouse and gamepad.
- [x] Add configurable keyboard macro buttons to Touch Mouse and Gamepad layouts instead of adding a separate keyboard tab.
- [x] Pre-populate keyboard macro choices with Windows and Mac target defaults, including Alt+Tab or Cmd+Tab and Ctrl+S or Cmd+S.
- [x] Add a custom keyboard macro editor for one modifier combination plus one key usage.
- [x] Make overlapping static/dynamic Touch Mouse left/right zones send combined button bits.
- [x] Add configurable gamepad haptic feedback intensity.
- [x] Add custom gamepad button labels.
- [ ] Make overlapping static Touch Mouse button zones send all overlapping mouse button presses in the same report.
- [ ] Allow each Touch Mouse button to define multiple sub-regions.
- [ ] Allow each Touch Mouse sub-region to add a key modifier or alternate mouse button while pressed.

## Phase 2 - Community Config Sharing

- [x] Add Community entry point on Home and a SwiftUI Browse/Share tab host.
- [x] Add UserDefaults-backed community config cache with `latest_at` cursor and merge-insert behavior.
- [x] Add URLSession API client for `/api/v1/configs`, disabled when `COMMUNITY_API_BASE_URL` is unset.
- [x] Add canonical JSON serializers for iOS gamepad and touch-mouse configs.
- [x] Add import sheet with Android-matching subset presets, target profile picker, and profile-specific save path.
- [x] Port `GamepadLayoutResolver` (Swift) and `LayoutRescaler` (Swift) from Android; bundle `gamepad_layout.json`.
- [x] Add `GamepadThumbnailView` (SwiftUI Canvas) to import sheet with landscape/portrait toggle; rescale offsets when source device canvas differs.
- [x] Add `OrientationPreference` to `GamepadConfig`; serialise, merge under `buttonBehavior` subset; `GamepadView` applies on enter and restores global lock on exit.
- [x] Add share/upload sheet with profile name, description, tags, category, device metadata, and public-upload disclosure.
- [x] Wire community sources into the Xcode project and verify simulator build/test.
- [ ] Validate gamepad layout preview rendering on a physical device and confirm thumbnail rescaling math against an Android-uploaded config with differing screen dimensions.
- [ ] Validate `OrientationPreference` override in `GamepadView` on a physical device (landscape/portrait lock apply and release correctly).

## Phase 3 - TestFlight And App Store

- [x] Confirm experimental BLE HID-over-GATT can pair with and control a Windows host from the Mac build.
- [x] Add `PrivacyInfo.xcprivacy` for app-local `UserDefaults` usage and optional Community Config collection disclosures.
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
