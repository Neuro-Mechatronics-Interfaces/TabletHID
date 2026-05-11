# TabletHID App Store Release Checklist

This checklist tracks the iOS project work needed before sending TabletHID to TestFlight or App Review.

## Current Status

- BLE HID-over-GATT has been proven locally from the Mac build to a Windows host.
- The iOS project builds for simulator and native macOS.
- The app includes a privacy manifest declaring local `UserDefaults` use and optional Community Config data collection for app functionality.
- App Store assets, screenshots, review notes, and a physical iPhone/iPad TestFlight validation pass are still required.

## Project Settings

- Bundle ID: `com.tablet.hid.ios`
- Team ID: `S6F754UF9T`
- Version: `0.1.0`
- Build: `1`
- Minimum iOS: `17.0`

Before the first App Store Connect app record is created, decide whether to keep the current bundle ID or change it to a permanent reverse-DNS ID under your own domain/name. The App Store Connect bundle ID must match `PRODUCT_BUNDLE_IDENTIFIER`.

## Required Before Upload

- [ ] Create a production App Store Connect app record.
- [ ] Confirm the bundle ID in App Store Connect matches `PRODUCT_BUNDLE_IDENTIFIER`.
- [ ] Add a real app icon asset catalog (`AppIcon`) for iPhone/iPad.
- [ ] Confirm Bluetooth permission copy is accurate in `NSBluetoothAlwaysUsageDescription`.
- [ ] Generate a privacy report in Xcode and confirm it matches the App Store privacy answers.
- [ ] Confirm App Store privacy answers disclose Other User Content, Product Interaction, and Other Data Types for optional Community Config uploads/imports; mark them not linked to the user and not used for tracking.
- [ ] Archive with a generic iOS device destination or a physical iPhone/iPad selected.
- [ ] Upload the archive to App Store Connect using Xcode Organizer.
- [ ] Wait for build processing, then distribute through TestFlight first.

## App Store Connect Metadata

- [ ] App name.
- [ ] Subtitle.
- [ ] Category.
- [ ] Privacy Policy URL.
- [ ] Support URL.
- [ ] Marketing URL, optional.
- [ ] Screenshots for iPhone and iPad.
- [ ] Description that clearly says the app uses Bluetooth to act as a control surface.
- [ ] Review notes explaining:
  - Pair the iPhone/iPad with a Windows/macOS host over Bluetooth.
  - Open TabletHID, choose Touch Mouse or Gamepad, and tap Prepare Transport.
  - The host should subscribe to the HID report characteristic and receive mouse/gamepad reports.

## Review Risk To Test Explicitly

- [ ] Validate on a physical iPhone or iPad, not only native macOS.
- [ ] Validate after forgetting/re-pairing the host.
- [ ] Validate after app relaunch.
- [ ] Validate after Bluetooth toggle.
- [ ] Validate host behavior when the app is backgrounded or screen locks.
- [ ] Decide whether the App Store build should expose a diagnostic log screen for review/debugging.

## Release Commands

Open the project:

```sh
open ios/TabletHID.xcodeproj
```

Regenerate the project after editing `project.yml`:

```sh
cd ios
xcodegen generate
```

Build locally:

```sh
cd ios
xcodebuild build -project TabletHID.xcodeproj -scheme TabletHID -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.4'
```

Native macOS smoke build:

```sh
cd ios
xcodebuild build -project TabletHID.xcodeproj -scheme TabletHID -sdk macosx CODE_SIGNING_ALLOWED=NO
```
