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
| Act as HID peripheral | Implemented with `BluetoothGattServer` BLE HID-over-GATT; DIS + HID services advertised via `BluetoothLeAdvertiser` | Experimental BLE HID transport added via expanded `00001812-...` service UUID; needs physical-device validation |
| Combined mouse + gamepad + keyboard descriptor | Implemented with Report ID 1, 2, and 3 in one registration; BLE path exposes all three Report characteristics | Ported with Report IDs 1, 2, and 3; exposed through experimental HID-over-GATT Report characteristics; physical-device validation pending |
| Discoverable pairing flow | Implemented with 120-second discoverable request and configurable Bluetooth name | Experimental `CBPeripheralManager` advertising with configurable local name from Home or Setup; new-pair action republishes DIS + HID services, requires encrypted report access, ignores known-host auto-reattach attempts, and requires on-iPad approval before accepting a new host |
| Reconnect bonded host | Implemented; BLE GATT server kept open across disconnect so Windows can reconnect via cached handles without re-pairing; `disconnect()` no longer tears down GATT server | Implemented for the experimental transport by remembering subscribed hosts and restarting advertising; known hosts reconnect only through explicit Reconnect/auto-reconnect paths, not through new-pair discovery |
| Disconnect / unbond | Implemented; `disconnect()` keeps GATT server alive for re-use; `disconnectAndUnbond()` does a full teardown | Disconnect resets local state only |
| Foreground service | Implemented; `connectedDevice` foreground service type; connection survives Home press and screen-off | TODO |
| Persistent notification | Implemented via foreground service; shows connection state text and Disconnect action when connected | TODO |
| Auto-reconnect on launch | Implemented; opt-in toggle in Settings; reconnects to last paired device using `HidForegroundService` on app open | Implemented for the experimental transport; opt-in toggle reconnects to the last remembered host after onboarding is complete |

Relevant Apple docs currently point app developers toward Core Bluetooth BLE central/peripheral APIs and the MFi program for Classic Bluetooth accessories, not a clearly documented public app API for making an iPhone/iPad advertise as a Bluetooth HID device. The iOS implementation therefore treats BLE HID as experimental:

- https://developer-mdn.apple.com/bluetooth/
- https://developer.apple.com/documentation/corebluetooth
- https://developer.apple.com/documentation/corehid
- https://developer.apple.com/forums/thread/725238

The iOS BLE HID report map now includes the keyboard Report ID 3 collection and a third Report characteristic. Hosts that cached an earlier iOS report map may need to forget and re-pair before keyboard macro reports are visible.

## Reports And Protocol

| Feature | Android status | iOS status |
| --- | --- | --- |
| Mouse descriptor | Implemented | Ported |
| Mouse report builder | Implemented, 7 bytes including horizontal AC Pan | Ported with unit tests |
| Gamepad descriptor | Implemented | Ported |
| Gamepad report builder | Implemented, 13 bytes | Ported with unit tests |
| Keyboard descriptor | Implemented as standard 8-byte input report on Report ID 3; Android Touch Mouse and Gamepad macro buttons use the existing Report ID 3 path | Ported as Report ID 3 in the experimental BLE HID report map; host validation pending |
| Keyboard report builder | Implemented for modifiers plus up to 6 key usages, with JVM unit tests | Ported with unit tests |
| Button bit constants | Implemented | Ported |
| Hat switch diagonal mapping | Implemented | Ported in UI model |
| Output reports / rumble | Not implemented | Not implemented |

## App Navigation

| Feature | Android status | iOS status |
| --- | --- | --- |
| Home mode selection | Implemented | Implemented |
| Built-in profiles | Default, Access Basic, Access Advanced | Ported |
| Custom profiles | Add custom profile, persist key/name | Initial add/select support |
| Setup / tutorial | Windows and macOS pairing instructions; Tutorial required only for first-time pairing — reconnect and direct-to-mode navigation available from Home screen | First-run onboarding added with pairing overview and HoG server name prompt; Setup screen provides first-pair and reconnect guidance with iOS transport limitation note |
| Home screen connection card | Implemented; LED status indicator, Make Discoverable and Reconnect buttons, editable device-name chip; mode cards navigate directly to control mode when already connected | Implemented; LED status indicator, editable HoG server-name chip, Make Discoverable, Reconnect, Cancel, Disconnect, and Setup actions |
| Enter control mode after connect | Implemented; direct nav actions bypass Tutorial when already connected | Implemented; direct mode cards use connected/reconnect/discoverable state and avoid forcing Setup once a host is known |

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
| Shared dynamic follower location | Implemented with config toggle for Android dynamic zones | Implemented |
| Overlapping mouse zones combine buttons | Implemented for static and dynamic Android zones | Implemented for left/right zones |
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
| Single-joystick layout mode | Implemented; one visible joystick can route to left-stick or right-stick report fields with the inactive stick neutral | Implemented; one visible joystick can route to left or right report fields with the inactive stick neutral |
| A/B/X/Y buttons | Implemented | Initial buttons |
| LB/RB buttons | Implemented | Initial buttons |
| LT/RT analog triggers | Implemented via drag travel | Initial press-only trigger values |
| Back/Start buttons | Implemented | Initial buttons |
| D-pad with diagonals | Implemented | Initial buttons and diagonal hat mapping |
| Momentary/latching buttons | Implemented | Implemented |
| Turbo | Implemented | Implemented |
| Drag-to-reposition controls | Implemented | Implemented for Gamepad controls via in-surface layout edit mode |
| Pinch-to-resize controls | Implemented; Android gamepad widgets have no maximum scale cap | Implemented for Gamepad controls via in-surface layout edit mode |
| Persist layout | Implemented | Implemented for Gamepad controls; offsets/scales persist per profile and are included in community config import/export |
| Multiple presets | Built-in plus custom profiles | Built-in plus custom profiles |
| Keyboard macro buttons | Implemented with Windows/Mac preset sets on Android Touch Mouse and Gamepad layouts; custom macro editor (`CustomMacroEditorDialog`) allows adding arbitrary modifier+key combinations; each button stores `layoutOffsetX/Y/scaleX/Y` persisted per profile | Initial implementation: Windows/Mac preset sets, custom modifier+key editor, Touch Mouse shortcut panel, and Gamepad macro overlay; layout repositioning TODO; BLE host validation pending |
| Macro button repositioning | Implemented; Touch Mouse: "Edit Macro Layout" button in config sheet enters drag+pinch edit mode with a banner and Done button; Gamepad: included in existing Edit Layout mode; offsets and scale saved per profile | TODO |
| Visual press feedback | Implemented | Initial press styling |
| Rumble | TODO | TODO |
| Vibrotactile feedback | Implemented; `HapticFeedback.vibrate()` fires on button/D-pad `ACTION_DOWN`; intensity (Off/Light/Medium/Strong) persisted per profile via `GamepadConfigStore`; toggle group in `GamepadConfigSheet` | Implemented with UIKit impact feedback on gamepad button/D-pad presses; intensity persisted per profile |
| Custom button labels | Implemented; `customButtonLabels: Map<String, String>` in `GamepadConfig`; persisted as `label_<key>` strings per profile; `GamepadConfigSheet` shows an inline EditText per selected button; `GamepadFragment.applyConfig()` applies labels with fallback to defaults | Implemented |
| Sensitivity adjuster (sniper zone) | Implemented in Touch Mouse mode; static drag-to-set zone labeled "S"; while held, divides the movement scale by 2×/4×/8× (configurable); persisted per profile; drawn in teal on overlay | TODO |
| Drag-lock gesture (double-tap-and-hold) | Implemented in Touch mode; second tap held ≥ 220 ms latches left button; first lift keeps latch for drag; tap with minimal movement releases; short second tap still fires double-click | TODO |
| Keyboard shortcut launcher panel | Implemented; keyboard icon button in Touch Mouse status bar (visible when macros are configured) toggles a right-side scrollable panel showing all macro buttons in a vertical list; reuses macro button config | Implemented |
| App home screen shortcuts | Implemented; static `shortcuts.xml` with Touch Mouse and Gamepad entries; `singleTop` MainActivity; `pendingStartMode` in ViewModel consumed by `HomeFragment` to bypass mode card selection | TODO |
| Home screen widget | Implemented; `HidWidgetProvider` shows connection status and Reconnect/Disconnect button; `HidWidgetState` SharedPrefs updated by ViewModel on each state change; Reconnect triggers foreground service reconnect and opens app; Disconnect sends service intent | TODO |

## Settings & App Controls

| Feature | Android status | iOS status |
| --- | --- | --- |
| Appearance / dark mode | System / Light / Dark; applied via `AppCompatDelegate` | System / Light / Dark; gear icon on Home opens `AppSettingsView`; drives `preferredColorScheme` |
| Configurable peripheral name | Settings screen stores the name; `HidManager` uses it for adapter rename and SDP registration on new pair | First-run onboarding and `AppSettingsView` store the HoG server name; `ExperimentalBLEHIDTransport` uses it for BLE local-name advertising |
| Large Text | Settings screen stores preference; activity context applies enlarged font scale after recreate | `AppSettingsView` stores preference; SwiftUI root applies accessibility dynamic type |
| High Contrast | Settings screen stores preference; activity applies high-contrast Material theme after recreate | `AppSettingsView` stores preference; SwiftUI root applies stronger contrast and primary tint |
| Session logging | Toggle in Settings screen; `.config` + timestamped `.log` per connection via `SessionLogger` | Toggle in `AppSettingsView`; writes to `Documents/sessions/` via `SessionLogger` |
| Orientation lock | System / Portrait / Landscape; Settings screen + in-canvas cycle button on both status bars; `requestedOrientation` applied immediately | System / Portrait / Landscape; `AppDelegate.supportedInterfaceOrientationsFor` + `UIWindowScene.requestGeometryUpdate` (iOS 16+); `AppSettingsView` picker + in-canvas cycle button |
| Connection status chip | Implemented via ActionBar subtitle in `MainActivity`; visible on Home and Tutorial screens; shows empty (Idle), Starting, Waiting, Connecting, Connected, and Error states | Implemented on Home connection card and control surfaces; shows idle, starting, waiting, reconnecting, connected, unavailable, and error states |
| Screen pinning | Implemented; opt-in toggle in Settings screen; `startLockTask()` called in `GamepadFragment` and `TouchMouseFragment` `onResume()` | TODO |
| Settings screen | Implemented as `SettingsFragment` in nav graph; replaces the programmatic dialog; navigated to via ActionBar Settings menu item from any screen | Implemented as `AppSettingsView` from Home plus per-mode settings sheets |
| Known host management | Rename / forget host; last 10 hosts stored | Rename / forget host; list stored in `UserDefaults`; iOS exposes central identifiers rather than friendly host names, so unknown hosts are shown as unidentified until renamed |
| Multi-profile support | Built-in + custom profiles; profile-namespaced config storage | Built-in + custom profiles; profile-namespaced `UserDefaults` keys |

## Community Config Sharing

| Feature | Android status | iOS status |
| --- | --- | --- |
| Community screen navigation (Home → Browse/Share tabs) | Implemented; `CommunityFragment` hosts two tabs via `ViewPager2` + `TabLayout`; `cardCommunity` added to `fragment_home.xml`; `HomeFragment` wires navigation action | Implemented; `CommunityView` (`TabView` with Browse/Share tabs) presented as a sheet from `HomeView` via `communityCard` button |
| Browse tab (list, filter chips, sort toggle, pull-to-refresh) | Implemented; `BrowseFragment` observes `CommunityViewModel.uiState`; mode/platform filter chips call `setFilterMode`/`setFilterPlatform`; sort toggle calls `setSortOrder`; `SwipeRefreshLayout` triggers `refresh()`; `syncIfStale()` called on `onResume` | Implemented; `BrowseView` shows mode/platform `FilterChip` controls, sort toggle, `List` backed by `CommunityViewModel.configs`; `.refreshable` triggers `refresh()`; `.task` triggers `syncIfStale()` |
| Config list item (mode icon, profile name, device/OS, download count) | Implemented via `ConfigListAdapter` + `item_config.xml` | Implemented via `ConfigRowView`; mode icon, profile name, device name, platform/OS/diagonal badges, download count |
| Import sheet (subset checkboxes, quick presets, profile picker, apply) | Implemented; `ImportSheet` fetches the selected record by id to increment `download_count`, deserializes `CommunityConfigRecord`, shows gamepad or touch-mouse checkboxes, preset chips update checkboxes, manual checkbox changes sync chip to Custom; `ConfigMerger.mergeGamepad`/`mergeTouchMouse` applied to target profile and saved; snackbar on success | Implemented; `ImportSheet` fetches the selected record by id to increment `download_count`, uses matching preset chips (Everything/Layout/Macros/Behaviors/Custom), per-mode toggle lists, profile picker; `ConfigMerger` merge is applied to the selected target profile and saved via `AppState` profile-specific persistence |
| Gamepad layout preview in import sheet | Implemented; `GamepadThumbnailView` (custom `View`) uses `GamepadLayoutResolver` (Kotlin port of web `layoutResolver.js`) to resolve positions from `gamepad_layout.json` (bundled in `assets/`); draws buttons and joysticks scaled to view width at a 600×340dp reference canvas; enabled/disabled state and offsets from config JSON applied; shown above profile picker in `ImportSheet` for gamepad configs; landscape/portrait toggle initialised from config's `orientationPreference` | TODO |
| Config orientation preference | Implemented; `OrientationPreference` enum (`SYSTEM`/`LANDSCAPE`/`PORTRAIT`) added to `GamepadConfig`; serialised as `orientationPreference` in canonical JSON; merged under `GAMEPAD_BUTTON_BEHAVIOR` subset; `GamepadFragment` applies it on enter via `applyConfigOrientation()`; `SYSTEM` defers to `OrientationStore`; backfill script at `scripts/backfill_orientation.py` | TODO |
| Share tab (config summary cards per mode, profile picker) | Implemented; `ShareFragment` loads `GamepadConfigStore` + `TouchMouseConfigStore` for active profile and shows enabled button count, macro count, mode/sensitivity, zone types; profile chip opens `AlertDialog` picker | Implemented; `ShareView` shows Gamepad and Touch Mouse summary cards with enabled count, macro count, joystick mode, vibration, sensitivity, zone types; profile picker sheet |
| Upload sheet (profile name, description, tags, category, device info, privacy notice, upload flow) | Implemented; `UploadSheet` serializes config to canonical JSON, builds `CommunityUploadBody` with display metrics and `Build.*` device fields, calls `viewModel.uploadConfig(body)`, shows progress indicator, snackbar on success | Implemented; `UploadSheet` serialises via `GamepadConfigSerializer`/`TouchMouseConfigSerializer`, populates `CommunityUploadBody` with `UIDevice`/`UIScreen`/`sysctlbyname("hw.machine")` device info, privacy notice, upload with progress overlay |
| Community API client | Implemented (`ConfigApiClient`); `HttpURLConnection`-based; reads `BuildConfig.COMMUNITY_API_BASE_URL`; feature is disabled when URL is empty | Implemented (`ConfigApiClient`); `URLSession`-based `actor`; reads `Bundle.main.infoDictionary["CommunityApiBaseUrl"]` from the `COMMUNITY_API_BASE_URL` build setting/`Secrets.xcconfig`; feature disabled when URL is empty or unresolved |
| Community config cache | Implemented (`CommunityConfigCache`); SharedPreferences-backed; `latest_at` delta cursor; merge-insert on incremental sync | Implemented (`CommunityConfigCache`); `UserDefaults`-backed; `community_latest_at` cursor; `community_cache_v1` JSON array; 500-record cap with uploadedAt trim |
| Config merge subsets | Implemented (`ConfigMerger`); six gamepad subsets and four touch-mouse subsets; returns new config without mutating inputs | Implemented (`ConfigMerger`); same six gamepad and four touch-mouse subsets; same field-level mappings as Android |
| Canonical JSON serialisers | Implemented (`GamepadConfigSerializer`, `TouchMouseConfigSerializer`); canonical field names matching `spec/server-schema.md` | Implemented (`GamepadConfigSerializer`, `TouchMouseConfigSerializer`); identical field names and nesting structure |
| QuadStick sheet import tooling | Implemented in `scripts/import_quadstick_community_configs.mjs`; converts scraped sheet metadata into clustered gamepad/touch-mouse Community Config rows tagged for future Graph grouping; groups keyboard outputs sharing one QuadStick input into single macro chord buttons and maps mouse buttons to Touch Mouse zones/sub-regions | Implemented as shared backend data; imported rows are normal v1 community records and can be browsed/imported by iOS clients |
| Community config graph | Server/web implemented with sparse token-similarity edge table, rebuild script, incremental upload/admin-update hooks, and `/configs` Graph tab; Android app does not query graph yet | Server/web implemented; iOS app does not query graph yet |
| Web preview device presets | Server/web implemented with `device_presets` table, `/api/v1/devices`, side-bound width/height inputs, and custom preset save action | Server/web implemented; iOS app uses uploaded device metadata but does not manage web preview presets |
| Web config preview/editor | Server/web implemented for Gamepad and Touch Mouse configs; Browser preview renders gamepad controls or Touch Mouse zones/macros, and Clone/Edit exposes metadata, device dimensions, Gamepad layout editing, Touch Mouse mode/sensitivity/scroll/button/sniper/macro controls, plus Touch Mouse zone/macro drag editing | Server/web implemented; iOS app consumes uploaded configs but does not use the web editor |

## Quality

| Feature | Android status | iOS status |
| --- | --- | --- |
| Unit tests for report bytes | Added for Android mouse, gamepad, keyboard builders and combined descriptor presence | Added for iOS report builders |
| CI | Implemented; debug build and unit tests run on push/PR to main | TODO |
| App Store readiness | Not applicable yet | Blocked until transport strategy is chosen |
