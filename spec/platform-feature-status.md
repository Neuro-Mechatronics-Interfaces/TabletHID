# TabletHID Platform Feature Status

This spec tracks the Android implementation in `app/` and the iOS equivalent in `ios/`.

## Platform Baseline

| Area | Android status | iOS status |
| --- | --- | --- |
| Native project | Complete Gradle Android app | Initial SwiftUI Xcode project scaffolded |
| Minimum OS | Android 10 / API 29 | iOS/iPadOS 17.0 target |
| Primary UI | Fragment + XML + custom Views | SwiftUI views plus UIKit touch surface |
| Local persistence | SharedPreferences XML, profile-scoped | UserDefaults, profile-scoped Codable keys |
| Bluetooth permissions | Runtime `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` | `NSBluetoothAlwaysUsageDescription` included for future transport experiments |

## HID Transport

| Feature | Android status | iOS status |
| --- | --- | --- |
| Act as HID peripheral | Implemented with `BluetoothHidDevice` | Experimental BLE HID transport added via expanded `00001812-...` service UUID; needs physical-device validation |
| Combined mouse + gamepad descriptor | Implemented with Report ID 1 and 2 in one registration | Descriptor/report builders ported and exposed through experimental HID-over-GATT Report characteristics |
| Discoverable pairing flow | Implemented with 120-second discoverable request | Experimental `CBPeripheralManager` advertising with mode device name |
| Reconnect bonded host | Implemented by cached Bluetooth address and `BluetoothHidDevice.connect()` | Not available until a supported iOS transport exists |
| Disconnect / unbond | Implemented | Disconnect resets local state only |

Relevant Apple docs currently point app developers toward Core Bluetooth BLE central/peripheral APIs and the MFi program for Classic Bluetooth accessories, not a clearly documented public app API for making an iPhone/iPad advertise as a Bluetooth HID device. The iOS implementation therefore treats BLE HID as experimental:

- https://developer-mdn.apple.com/bluetooth/
- https://developer.apple.com/documentation/corebluetooth
- https://developer.apple.com/documentation/corehid
- https://developer.apple.com/forums/thread/725238

## Reports And Protocol

| Feature | Android status | iOS status |
| --- | --- | --- |
| Mouse descriptor | Implemented | Ported |
| Mouse report builder | Implemented, 6 bytes | Ported with unit tests |
| Gamepad descriptor | Implemented | Ported |
| Gamepad report builder | Implemented, 13 bytes | Ported with unit tests |
| Button bit constants | Implemented | Ported |
| Hat switch diagonal mapping | Implemented | Ported in UI model |
| Output reports / rumble | Not implemented | Not implemented |

## App Navigation

| Feature | Android status | iOS status |
| --- | --- | --- |
| Home mode selection | Implemented | Implemented |
| Built-in profiles | Default, Access Basic, Access Advanced | Ported |
| Custom profiles | Add custom profile, persist key/name | Initial add/select support |
| Setup / tutorial | Windows and macOS pairing instructions | Placeholder setup screen with platform limitation note |
| Enter control mode after connect | Implemented | Allowed in simulator/development even when transport is unavailable |

## Touch Mouse

| Feature | Android status | iOS status |
| --- | --- | --- |
| Touch mode movement with button held | Implemented | Initial UIKit touch surface |
| Double-tap double-click | Implemented | Initial implementation |
| Bottom right-click zone | Implemented | Initial implementation |
| Mouse/trackpad mode | Implemented | Initial implementation |
| Sensitivity | Implemented | Initial settings sheet |
| Static left/right zones | Implemented | Initial rendering and hit testing |
| Dynamic left/right zones | Implemented | Initial rendering and hit testing |
| Momentary/latching zones | Implemented | Initial implementation |
| Zone editing | Implemented | Not yet implemented |
| Persist config | Implemented | Implemented |
| Two-finger scroll | TODO | TODO |
| Middle click | TODO | TODO |
| Momentum / fling | TODO | TODO |

## Gamepad

| Feature | Android status | iOS status |
| --- | --- | --- |
| Two analog sticks | Implemented | Initial SwiftUI joysticks |
| A/B/X/Y buttons | Implemented | Initial buttons |
| LB/RB buttons | Implemented | Initial buttons |
| LT/RT analog triggers | Implemented via drag travel | Initial press-only trigger values |
| Back/Start buttons | Implemented | Initial buttons |
| D-pad with diagonals | Implemented | Initial buttons and diagonal hat mapping |
| Momentary/latching buttons | Implemented | Data model ported; UI uses momentary for first pass |
| Turbo | Implemented | Data model ported; UI not yet wired |
| Drag-to-reposition controls | Implemented | TODO |
| Pinch-to-resize controls | Implemented | TODO |
| Persist layout | Implemented | Config persistence ported; layout editing TODO |
| Multiple presets | Built-in plus custom profiles | Built-in plus custom profiles |
| Visual press feedback | Implemented | Initial press styling |
| Rumble | TODO | TODO |

## Quality

| Feature | Android status | iOS status |
| --- | --- | --- |
| Unit tests for report bytes | TODO | Added for iOS report builders |
| CI | TODO | TODO |
| App Store readiness | Not applicable yet | Blocked until transport strategy is chosen |
