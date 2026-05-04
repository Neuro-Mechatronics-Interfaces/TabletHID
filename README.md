# TabletHID

An Android tablet app that turns the device into a Bluetooth HID peripheral. Supports two modes:

- **Touch Mouse** — full-screen touch surface that emulates a relative-movement mouse with left/right click and scroll
- **Gamepad** — configurable virtual Xbox-style controller

---

## Architecture

```
app/src/main/java/com/tablet/hid/
├── TabletHidApplication.kt        # Application class; owns HidViewModel factory
├── MainActivity.kt                # Runtime permission handling; hosts NavHostFragment
├── HidViewModel.kt                # Activity-scoped ViewModel; owns HidManager lifetime
├── bluetooth/
│   ├── HidManager.kt              # BluetoothHidDevice proxy wrapper; StateFlow state
│   └── HidReportDescriptors.kt    # HID descriptor byte arrays + report-building helpers
├── model/
│   └── DeviceMode.kt              # Enum: TOUCH_MOUSE, GAMEPAD
└── ui/
    ├── home/HomeFragment.kt        # Mode selection screen
    ├── tutorial/TutorialFragment.kt# Step-by-step pairing guide (Windows / macOS tabs)
    ├── touchmouse/TouchMouseFragment.kt
    └── gamepad/
        ├── GamepadFragment.kt
        └── JoystickView.kt        # Custom analog-stick View
```

### HID layer

`BluetoothHidDevice` (classic BR/EDR Bluetooth, API 28+) is used — not BLE HOGP. This
gives the best compatibility with Windows and macOS without requiring custom drivers.

| Mode       | SDP subclass                      | Report ID | Report size |
|------------|-----------------------------------|-----------|-------------|
| Touch Mouse| `SUBCLASS1_MOUSE` (0x80)          | 1         | 6 bytes     |
| Gamepad    | `SUBCLASS1_NONE` (0x00)           | 2         | 13 bytes    |

**Mouse report layout (6 bytes):**
```
byte[0]   buttons  — bit 0 = left, bit 1 = right, bit 2 = middle; bits 3-7 padding
byte[1:2] X        — signed 16-bit little-endian, relative
byte[3:4] Y        — signed 16-bit little-endian, relative
byte[5]   wheel    — signed 8-bit, relative
```

**Gamepad report layout (13 bytes):**
```
byte[0:1]   left stick X   — signed 16-bit LE, absolute (-32768 … 32767)
byte[2:3]   left stick Y   — signed 16-bit LE, absolute
byte[4:5]   right stick X  — signed 16-bit LE, absolute
byte[6:7]   right stick Y  — signed 16-bit LE, absolute
byte[8]     left trigger   — unsigned 8-bit (0-255)
byte[9]     right trigger  — unsigned 8-bit (0-255)
byte[10:11] buttons        — 10 button bits (A B X Y LB RB Back Start L3 R3) + 6 padding
byte[12]    hat switch     — lower 4 bits (0=N 1=NE 2=E 3=SE 4=S 5=SW 6=W 7=NW 8=none)
                            upper 4 bits padding
```

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
1. Open app, select mode → Tutorial screen appears
2. Tap **Make Discoverable** — tablet becomes visible for 120 s
3. On Windows: Settings → Bluetooth → "Add device" → find **TabletHID Mouse** or **TabletHID Gamepad**
4. Accept pairing on both sides (no PIN required for HID devices)
5. Tap **Enter [Mode]** once the connection status shows *Connected*

### macOS
1. Tap **Make Discoverable**
2. On Mac: System Settings → Bluetooth → find **TabletHID …** → Connect
3. The tablet appears as a paired device with no PIN

### Switching modes / closing the app
When you switch modes or leave the app the bond is automatically removed so the host is
ready for a fresh pair with the new profile. This is intentional — the two modes present
different HID descriptors and cannot share a bond.

---

## Touch Mouse gesture reference

| Gesture                             | Mouse action         |
|-------------------------------------|----------------------|
| Touch & drag (main area)            | Move + left-button held |
| Lift finger                         | Release left button  |
| Double-tap (main area)              | Double-click         |
| Hold **Right-click zone** (bottom strip) | Activates right-click modifier |
| Drag while right-click zone held    | Move + right-button held |

---

## Building

Standard Android project — open in Android Studio, sync Gradle, run on a device with
Bluetooth (emulators do not expose `BluetoothHidDevice`).

**Minimum SDK:** API 29 (Android 10) — `BluetoothHidDevice` available since API 28.

**Required permissions (runtime on API 31+):** `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`.

---

## Known limitations / Phase 2 work

See [TODO.md](TODO.md).
