# TabletHID

An Android tablet app that turns the device into a Bluetooth HID peripheral. Supports two modes:

- **Touch Mouse** — full-screen touch surface that emulates a relative-movement mouse with left/right click and scroll
- **Gamepad** — configurable virtual Xbox-style controller with analog sticks, triggers, face/shoulder buttons, and D-pad

---

## Architecture

```
app/src/main/java/com/tablet/hid/
├── TabletHidApplication.kt          # Application class; owns HidViewModel factory
├── MainActivity.kt                  # Runtime permission handling; hosts NavHostFragment
├── HidViewModel.kt                  # Activity-scoped ViewModel; owns HidManager, profile + config state
├── bluetooth/
│   ├── HidManager.kt                # BluetoothHidDevice proxy; StateFlow state machine
│   └── HidReportDescriptors.kt      # Combined HID descriptor + report-building helpers
├── model/
│   ├── DeviceMode.kt                # Enum: TOUCH_MOUSE, GAMEPAD
│   ├── AccessProfile.kt             # Profile data class (name, key); built-in + custom profiles
│   ├── GamepadConfig.kt             # ButtonConfig, JoystickConfig, GamepadConfig data classes
│   ├── TouchMouseConfig.kt          # Touch mouse settings data class
│   └── ClickBehavior.kt             # Enum: MOMENTARY, LATCHING
├── util/
│   ├── ProfileStore.kt              # Active profile + custom profile list persistence
│   ├── GamepadConfigStore.kt        # Profile-namespaced gamepad config SharedPreferences
│   └── TouchMouseConfigStore.kt     # Profile-namespaced touch mouse config SharedPreferences
└── ui/
    ├── home/HomeFragment.kt          # Mode selection + profile selector
    ├── tutorial/TutorialFragment.kt  # Pairing guide; smart reconnect flow
    ├── touchmouse/
    │   ├── TouchMouseFragment.kt
    │   └── TouchMouseConfigSheet.kt
    └── gamepad/
        ├── GamepadFragment.kt
        ├── GamepadConfigSheet.kt
        └── JoystickView.kt           # Custom analog-stick View
```

### HID layer

`BluetoothHidDevice` (classic BR/EDR, API 28+) is used — not BLE HOGP. Both modes share a **single Bluetooth bond** using a combined descriptor with two application collections:

| Collection | Report ID | Report size | SDP subclass |
|------------|-----------|-------------|--------------|
| Mouse      | 1         | 6 bytes     | `SUBCLASS1_NONE` (0x00) |
| Gamepad    | 2         | 13 bytes    | `SUBCLASS1_NONE` (0x00) |

Sharing one bond means the tablet only needs to pair once, and switching between Touch Mouse and Gamepad modes is instant.

**Mouse report layout (6 bytes, Report ID 1):**
```
byte[0]   buttons  — bit 0 = left, bit 1 = right, bit 2 = middle; bits 3-7 padding
byte[1:2] X        — signed 16-bit LE, relative
byte[3:4] Y        — signed 16-bit LE, relative
byte[5]   wheel    — signed 8-bit, relative
```

**Gamepad report layout (13 bytes, Report ID 2):**
```
byte[0:1]   left stick X   — signed 16-bit LE, absolute (-32768 … 32767)   [HID usage X]
byte[2:3]   left stick Y   — signed 16-bit LE, absolute                    [HID usage Y]
byte[4]     left trigger   — unsigned 8-bit (0-255)                        [HID usage Z]
byte[5]     right trigger  — unsigned 8-bit (0-255)                        [HID usage Rz]
byte[6:7]   right stick X  — signed 16-bit LE, absolute                   [HID usage Rx]
byte[8:9]   right stick Y  — signed 16-bit LE, absolute                   [HID usage Ry]
byte[10:11] buttons        — 10 bits (A B X Y LB RB Back Start L3 R3) + 6 pad
byte[12]    hat switch     — lower 4 bits (0=N 1=NE 2=E 3=SE 4=S 5=SW 6=W 7=NW 8=none)
```

Axis order (X, Y → Z, Rz → Rx, Ry) matches the Xbox-standard HID layout for maximum host compatibility. Re-pairing is required after any descriptor change since the host caches the descriptor.

### Profile system

Three built-in profiles are pre-configured:

| Profile         | Key              | Default behaviour |
|-----------------|------------------|-------------------|
| Default         | `default`        | All controls enabled, momentary |
| Access – Basic  | `access_basic`   | Triggers + D-pad disabled |
| Access – Advanced | `access_advanced` | Face + shoulder buttons latching |

Each profile stores its own config in `gamepad_config_<key>` / `touch_mouse_config_<key>` SharedPreferences. On first use (no `__saved` sentinel), the app falls back to a bundled raw resource default (`res/raw/gamepad_config_<key>.xml`) then to a Kotlin code default. Custom profiles can be created at runtime and persist across sessions.

### State machine

```
Idle ──initialize(mode)──► Registering ──onAppStatusChanged(true)──► WaitingForConnection
                                                                            │
                                                              host pairs & connects
                                                                            │
                                                                     Connected(device, mode)
                                                                            │
                                               disconnect() / cleanup() ◄──┘
```

---

## Pairing flow (user-facing)

### Windows 11
1. Open app, select a profile and mode → Tutorial screen appears
2. If a previously paired device is found, tap **Reconnect** to skip the full pairing flow
3. Otherwise tap **Make Discoverable** — tablet becomes visible for 120 s
4. On Windows: Settings → Bluetooth → "Add device" → find **TabletHID**
5. Accept pairing on both sides (no PIN required for HID devices)
6. Tap **Enter [Mode]** once status shows *Connected*

### macOS
1. Tap **Make Discoverable**
2. On Mac: System Settings → Bluetooth → find **TabletHID** → Connect

### Switching modes
Both Touch Mouse and Gamepad use the same Bluetooth bond. Switching modes only changes which Report ID is sent; no re-pairing is needed.

---

## Touch Mouse

### Gesture reference

| Gesture                                  | Action               |
|------------------------------------------|----------------------|
| Touch & drag (main area)                 | Move + left-button held |
| Lift finger                              | Release left button  |
| Double-tap (main area)                   | Double-click         |
| Hold **right-click zone** (bottom strip) | Activates right-click modifier |
| Drag while right-click zone held         | Move + right-button held |

### Config sheet options
- **Touch / Mouse mode** — Touch mode taps-to-click; Mouse mode is trackpad-style (delta only on MOVE)
- **Sensitivity** slider (1–10)
- **Left / Right button enable toggles**
- **Static zone** — drag to place a fixed click rectangle anywhere on screen
- **Dynamic zone** — click pad follows the primary pointer at a configurable offset and radius
- **Momentary / Latching** behavior per button

---

## Gamepad

### Controls
- **Left / Right analog sticks** — `JoystickView` custom view; configurable deadzone and gain
- **Analog triggers (LT/RT)** — tap = full press; drag in the configured direction to modulate 0–255; visual glow indicates level
- **Face buttons (A/B/X/Y)**, **Shoulder buttons (LB/RB)**, **Back/Start** — momentary or latching; optional turbo with configurable press duration and repeat interval
- **D-pad** — 8-directional (diagonal combinations emit NE/NW/SE/SW hat codes)

### Layout editing
Tap **Settings → Edit Layout** to enter edit mode. A banner appears at the top.
- **Drag** any control to reposition it
- **Pinch** a control to resize it — H and V axes scale independently (buttons up to 4×, joysticks up to 3×)
- Tap **Done** or press Back to exit; positions auto-save per profile

### Gamepad settings (per button)
- **Enable / Disable** — hide controls not needed for a given profile
- **Click behavior** — Momentary (held) or Latching (toggle)
- **Turbo** — auto-repeating press with configurable duration and interval
- **Trigger travel** (LT/RT only) — drag distance in dp for full deflection (30–300 dp)
- **Drag direction** (LT/RT only) — which direction triggers the analog ramp (▲▼◀▶)

---

## Building

Standard Android project — open in Android Studio, sync Gradle, run on a device with Bluetooth (emulators do not expose `BluetoothHidDevice`).

**Minimum SDK:** API 29 (Android 10)

**Required permissions (runtime on API 31+):** `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`

### DEV mode

Set `DEV_MODE = true` in `app/build.gradle.kts` to enable:
- **Export Gamepad Config** — shares the current profile's SharedPreferences XML via Android share sheet
- **Export All Configs** — shares all config files concatenated

To pull config files via ADB:
```powershell
adb shell "run-as com.tablet.hid cat shared_prefs/gamepad_config_default.xml" |
    Out-File gamepad_config_default.xml -Encoding UTF8
```

Place exported XML files in `app/src/main/res/raw/` as `gamepad_config_<key>.xml` to use them as profile defaults for fresh installs.

---

## Known limitations

See [TODO.md](TODO.md).
