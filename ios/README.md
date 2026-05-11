# TabletHID iOS

This folder contains the preliminary iOS/iPadOS equivalent for TabletHID.

Open `TabletHID.xcodeproj` in Xcode, or regenerate it from `project.yml`:

```sh
xcodegen generate
```

The current iOS app includes first-run onboarding with a HoG server name prompt, the SwiftUI control surfaces, profile/config persistence, HID descriptor/report byte builders, keyboard macro reports, and an experimental BLE HID transport.

The transport uses `CBPeripheralManager` with the expanded HID service UUID `00001812-0000-1000-8000-00805F9B34FB`, a Device Information Service/PnP ID, and encrypted report access. This follows the workaround discussed in developer community testing and should be treated as experimental until it is validated on physical devices and target hosts. The setup screen's Development Mode is still useful for validating UI/report behavior without a host connection.

Useful commands:

```sh
xcodebuild build -project TabletHID.xcodeproj -scheme TabletHID -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5),OS=26.4'
xcodebuild build-for-testing -project TabletHID.xcodeproj -scheme TabletHID -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5),OS=26.4'
xcodebuild test -project TabletHID.xcodeproj -scheme TabletHID -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5),OS=26.4'
```
