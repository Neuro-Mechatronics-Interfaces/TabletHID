# iOS HID/Parity Implementation

Use this role for iOS work under `ios/` involving SwiftUI feature parity, report descriptors/builders, the experimental BLE HID transport, config/profile persistence, or App Store/TestFlight readiness.

## Start Here

1. Read `spec/platform-feature-status.md` and identify the Android behavior being matched or intentionally diverged from.
2. Check `ios/TODO.md` for iOS-specific blockers, validation status, and release notes.
3. Inspect the closest implementation:
   - Transport: `ios/TabletHID/HID/HIDTransport.swift`, `ExperimentalBLEHIDTransport.swift`
   - Reports/descriptors: `ios/TabletHID/HID/HIDReportDescriptors.swift`
   - App state/send-report boundary: `ios/TabletHID/App/AppState.swift`
   - Touch mouse: `ios/TabletHID/Views/TouchMouseView.swift`, `TouchMouseSettingsView.swift`
   - Gamepad: `ios/TabletHID/Views/GamepadView.swift`, `GamepadSettingsView.swift`
   - Stores/models: `ios/TabletHID/Store/`, `ios/TabletHID/Models/`

## Implementation Bias

- Treat iOS HID peripheral behavior as experimental unless physical-device validation proves otherwise.
- Keep report IDs and byte layouts aligned with Android: mouse Report ID 1, gamepad Report ID 2.
- Prefer feature parity in UI/model behavior while clearly documenting platform transport limitations.
- Preserve profile-scoped `UserDefaults` storage patterns.
- Do not present BLE HID as fully equivalent to Android Classic Bluetooth HID without validation against physical iPhone/iPad and target hosts.

## Verification

- Add or update Swift unit tests for report builders and descriptor-sensitive changes.
- Typical checks are `xcodebuild build`, `xcodebuild build-for-testing`, and `xcodebuild test` when simulator launch is stable locally.
- For BLE HID, record host/device coverage explicitly: iPhone/iPad hardware, macOS, Windows, Android, iPadOS, host forget/reconnect, app relaunch, Bluetooth toggle, background/screen lock.
- Update `spec/platform-feature-status.md`, `ios/TODO.md`, and user-facing docs when parity or limitations change.
