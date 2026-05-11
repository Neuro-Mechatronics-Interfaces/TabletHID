# TabletHID Reusable Skills

Use these notes to assign focused sub-agents without re-reading the whole repo. The canonical project workflow is still `AGENTS.md`; these files summarize repeatable roles.

## Available Skills

- [Android HID/Input Implementation](android-hid-input.md): Android Classic Bluetooth HID, report builders, touch mouse, gamepad, config persistence.
- [iOS HID/Parity Implementation](ios-hid-parity.md): SwiftUI parity work, experimental BLE HID transport, report descriptor parity, iOS validation limits.
- [Website/Spec/TODO Documentation Upkeep](docs-spec-todo.md): Status matrix, TODO hygiene, website copy review, user-facing docs.
- [Verification/Release Validation](verification-release.md): Build/test/manual validation passes for Android, iOS, website, HID pairing, and release readiness.
- [Bug Investigation](bug-investigation.md): Specialist sub-agent roles for each bug category in `BUGFIXES.md` — HID descriptor, BLE transport, touch input, gamepad UI, config persistence, host compatibility, Android platform, iOS platform.

## Shared Ground Rules

- Read `spec/platform-feature-status.md` before changing or reviewing a feature.
- Check `TODO.md` and the platform TODO, currently `ios/TODO.md`, before marking work complete.
- Keep platform status explicit when behavior differs.
- Do not mark HID, pairing, persistence, or release behavior complete without a matching verification note.
