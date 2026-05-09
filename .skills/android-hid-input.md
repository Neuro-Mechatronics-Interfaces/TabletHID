# Android HID/Input Implementation

Use this role for Android work under `app/` involving Bluetooth HID, input reports, touch mouse behavior, gamepad behavior, profile/config storage, or Android-facing setup flows.

## Start Here

1. Read `spec/platform-feature-status.md` for the feature row and Android/iOS parity status.
2. Check `TODO.md` for the product item and any Android implementation notes.
3. Inspect the closest existing implementation before designing new structure:
   - HID transport/report bytes: `app/src/main/java/com/tablet/hid/bluetooth/HidReportDescriptors.kt`, `HidManager.kt`, `BleHidManager.kt`
   - App state boundary: `app/src/main/java/com/tablet/hid/HidViewModel.kt`
   - Touch mouse UI: `app/src/main/java/com/tablet/hid/ui/touchmouse/`
   - Gamepad UI: `app/src/main/java/com/tablet/hid/ui/gamepad/`
   - Config/profile/session stores: `app/src/main/java/com/tablet/hid/util/`

## Implementation Bias

- Preserve HID descriptor/report stability unless the task explicitly requires a descriptor change.
- If controls are hidden, disabled, resized, or remapped, prefer neutral report values over changing report shape.
- Treat descriptor changes as pairing-impacting because hosts cache HID descriptors.
- Keep persistence profile-scoped where existing stores use profile-specific keys.
- Follow the existing Fragment/XML/custom View architecture and ViewModel send-report boundary.

## Verification

- Add or update focused tests when report byte construction, config persistence, transport state, or user-visible workflows change.
- Run the smallest relevant Gradle checks first, commonly `./gradlew test` or `./gradlew connectedAndroidTest` when device coverage is needed.
- For Bluetooth HID behavior, emulator checks are not enough; note whether physical Android-device validation was completed or remains pending.
- Update `spec/platform-feature-status.md` and docs/TODOs in the same change when feature truth changes.
