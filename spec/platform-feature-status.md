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
| Combined mouse + gamepad + keyboard descriptor | Implemented with Report ID 1, 2, and 3 in one registration; BLE path exposes all three Report characteristics | Descriptor/report builders ported and exposed through experimental HID-over-GATT Report characteristics |
| Discoverable pairing flow | Implemented with 120-second discoverable request and configurable Bluetooth name | Experimental `CBPeripheralManager` advertising with configurable local name |
| Reconnect bonded host | Implemented by cached Bluetooth address and `BluetoothHidDevice.connect()` | Implemented for the experimental transport by remembering subscribed hosts and restarting advertising |
| Disconnect / unbond | Implemented | Disconnect resets local state only |
| Foreground service | Implemented; `connectedDevice` foreground service type; connection survives Home press and screen-off | TODO |
| Persistent notification | Implemented via foreground service; shows connection state text and Disconnect action when connected | TODO |
| Auto-reconnect on launch | Implemented; opt-in toggle in Settings; reconnects to last paired device using `HidForegroundService` on app open | TODO |

Relevant Apple docs currently point app developers toward Core Bluetooth BLE central/peripheral APIs and the MFi program for Classic Bluetooth accessories, not a clearly documented public app API for making an iPhone/iPad advertise as a Bluetooth HID device. The iOS implementation therefore treats BLE HID as experimental:

- https://developer-mdn.apple.com/bluetooth/
- https://developer.apple.com/documentation/corebluetooth
- https://developer.apple.com/documentation/corehid
- https://developer.apple.com/forums/thread/725238

## Reports And Protocol

| Feature | Android status | iOS status |
| --- | --- | --- |
| Mouse descriptor | Implemented | Ported |
| Mouse report builder | Implemented, 7 bytes including horizontal AC Pan | Ported with unit tests |
| Gamepad descriptor | Implemented | Ported |
| Gamepad report builder | Implemented, 13 bytes | Ported with unit tests |
| Keyboard descriptor | Implemented as standard 8-byte input report on Report ID 3; Android Touch Mouse and Gamepad macro buttons use the existing Report ID 3 path | TODO |
| Keyboard report builder | Implemented for modifiers plus up to 6 key usages, with JVM unit tests | TODO |
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
| Touch mode movement with button held | Implemented | Implemented |
| Double-tap double-click | Implemented | Implemented |
| Bottom right-click zone | Implemented | Implemented |
| Mouse/trackpad mode | Implemented | Implemented |
| Sensitivity | Implemented | Implemented |
| Static left/right zones | Implemented | Implemented |
| Dynamic left/right zones | Implemented | Implemented |
| Shared dynamic follower location | Implemented with config toggle for Android dynamic zones | TODO |
| Overlapping mouse zones combine buttons | Implemented for static and dynamic Android zones | TODO |
| Momentary/latching zones | Implemented | Implemented |
| First-touch zone hit-test (no spurious movement on zone tap) | Implemented | Implemented |
| Sub-pixel delta accumulation (smooth movement) | Implemented | Implemented (coalesced touches) |
| Dynamic zone auto-calibrate (3-finger placement flow) | Implemented | Implemented |
| Zone editing (rubber-band drag to set static zone) | Implemented | Not yet implemented |
| Persist config | Implemented | Implemented |
| Three-finger scroll | Implemented with vertical wheel, horizontal AC Pan, enable toggle, and invert toggle | Implemented with vertical wheel, horizontal AC Pan, enable toggle, and invert toggle |
| Multiple button sub-regions | Implemented for static Android sub-regions with add/clear UI under Touch Mouse settings | TODO |
| Sub-region modifiers / alternate mouse button | Implemented for Android static sub-regions: middle-click alternates and Ctrl modifier sub-regions send keyboard modifier reports | TODO |
| Middle click | Implemented through Android Touch Mouse middle-click sub-regions | TODO |
| Momentum / fling | TODO | TODO |

## Gamepad

| Feature | Android status | iOS status |
| --- | --- | --- |
| Two analog sticks | Implemented | Initial SwiftUI joysticks |
| Single-joystick layout mode | Implemented; one visible joystick can route to left-stick or right-stick report fields with the inactive stick neutral | TODO |
| A/B/X/Y buttons | Implemented | Initial buttons |
| LB/RB buttons | Implemented | Initial buttons |
| LT/RT analog triggers | Implemented via drag travel | Initial press-only trigger values |
| Back/Start buttons | Implemented | Initial buttons |
| D-pad with diagonals | Implemented | Initial buttons and diagonal hat mapping |
| Momentary/latching buttons | Implemented | Data model ported; UI uses momentary for first pass |
| Turbo | Implemented | Data model ported; UI not yet wired |
| Drag-to-reposition controls | Implemented | TODO |
| Pinch-to-resize controls | Implemented; Android gamepad widgets have no maximum scale cap | TODO |
| Persist layout | Implemented | Config persistence ported; layout editing TODO |
| Multiple presets | Built-in plus custom profiles | Built-in plus custom profiles |
| Keyboard macro buttons | Implemented with Windows/Mac preset sets on Android Touch Mouse and Gamepad layouts; custom macro editor (`CustomMacroEditorDialog`) allows adding arbitrary modifier+key combinations | TODO |
| Visual press feedback | Implemented | Initial press styling |
| Rumble | TODO | TODO |

## Settings & App Controls

| Feature | Android status | iOS status |
| --- | --- | --- |
| Appearance / dark mode | System / Light / Dark; applied via `AppCompatDelegate` | System / Light / Dark; gear icon on Home opens `AppSettingsView`; drives `preferredColorScheme` |
| Configurable peripheral name | Settings dialog stores the name; `HidManager` uses it for adapter rename and SDP registration on new pair | `AppSettingsView` stores the name; `ExperimentalBLEHIDTransport` uses it for BLE local-name advertising |
| Large Text | Settings dialog stores preference; activity context applies enlarged font scale after recreate | `AppSettingsView` stores preference; SwiftUI root applies accessibility dynamic type |
| High Contrast | Settings dialog stores preference; activity applies high-contrast Material theme after recreate | `AppSettingsView` stores preference; SwiftUI root applies stronger contrast and primary tint |
| Session logging | Toggle in Settings dialog; `.config` + timestamped `.log` per connection via `SessionLogger` | Toggle in `AppSettingsView`; writes to `Documents/sessions/` via `SessionLogger` |
| Orientation lock | System / Portrait / Landscape; Settings dialog + in-canvas cycle button on both status bars; `requestedOrientation` applied immediately | System / Portrait / Landscape; `AppDelegate.supportedInterfaceOrientationsFor` + `UIWindowScene.requestGeometryUpdate` (iOS 16+); `AppSettingsView` picker + in-canvas cycle button |
| Connection status chip | Implemented via ActionBar subtitle in `MainActivity`; visible on Home and Tutorial screens; shows empty (Idle), Starting, Waiting, Connecting, Connected, and Error states | TODO |
| Screen pinning | Implemented; opt-in toggle in Settings; `startLockTask()` called in `GamepadFragment` and `TouchMouseFragment` `onResume()` | TODO |
| Known host management | Rename / forget host; last 10 hosts stored | Rename / forget host; list stored in `UserDefaults` |
| Multi-profile support | Built-in + custom profiles; profile-namespaced config storage | Built-in + custom profiles; profile-namespaced `UserDefaults` keys |

## Quality

| Feature | Android status | iOS status |
| --- | --- | --- |
| Unit tests for report bytes | Added for Android mouse, gamepad, keyboard builders and combined descriptor presence | Added for iOS report builders |
| CI | Implemented; debug build and unit tests run on push/PR to main | TODO |
| App Store readiness | Not applicable yet | Blocked until transport strategy is chosen |
