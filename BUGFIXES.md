# BUGFIXES

Active bug tracking for TabletHID. Each entry uses the template below. When an agent investigates a bug, it reads the entry, picks the matching specialist category from `.skills/bug-investigation.md`, and writes its findings back under **Investigation** and **Resolution**.

---

## Template

```
## BUG-NNN: [One-line title]

- **Category**: [HID Descriptor | BLE Transport | Touch Input | Gamepad UI | Config Persistence | Host Compatibility | Android Platform | iOS Platform]
- **Platform**: [Android | iOS | Both | Host:Windows | Host:macOS | Host:Linux]
- **Severity**: [Blocking | High | Medium | Low]
- **Status**: [Open | Investigating | Fix in progress | Fixed | Won't fix]

### Symptom
What the user sees or experiences.

### Expected behavior
What should happen instead.

### Reproduction steps
1. ...

### Diagnostic data
Paste logcat output, HID report bytes, descriptor hex dumps, host-side device manager screenshots, etc.
Leave blank if not yet gathered.

### Investigation
<!-- Agent fills this in: root cause hypothesis, files read, relevant byte offsets, spec citations. -->

### Resolution
<!-- Agent fills this in when fixed: what changed, which files, whether re-pair is required. -->
```

---

## Category quick-reference

| Category | Typical symptoms | Key files |
| --- | --- | --- |
| **HID Descriptor** | Wrong axis direction, swapped sticks, buttons not registering, triggers not analog, host mis-identifies device type | `HidReportDescriptors.kt`, unit tests |
| **BLE Transport** | Pairing fails, "incorrect PIN", connection drops, GATT errors, device not discovered | `BleHidManager.kt`, `HidHostStore.kt` |
| **Touch Input** | Zone not triggered, wrong pointer routed, scroll/drag mis-fires, accumulation drift | `TouchZoneOverlayView.kt`, `TouchMouseFragment.kt` |
| **Gamepad UI** | Widget off-screen after resize, edit mode broken, trigger travel wrong, turbo mis-fires | `GamepadFragment.kt`, `GamepadView.kt`, `TriggerView.kt` |
| **Config Persistence** | Profile not saved/loaded, defaults wrong, reset doesn't clear, migration crash | `*Store.kt` files under `util/` |
| **Host Compatibility** | Works on Windows, broken on macOS; axis mapping differs per host; Steam/game-specific | Descriptor + host-side driver behavior |
| **Android Platform** | Permission crash, foreground service killed, orientation lock ignored, API-level regression | `MainActivity.kt`, `HidForegroundService.kt`, `AndroidManifest.xml` |
| **iOS Platform** | CBPeripheralManager error, background disconnect, host can't discover, report not received | `ios/` source tree |

---

## Active bugs

<!-- Add new bugs above the resolved section. -->

---

## Resolved bugs

## BUG-001: Reconnect stalls forever when Windows host has forgotten the device

- **Category**: BLE Transport
- **Platform**: Android / Host:Windows
- **Severity**: High
- **Status**: Fixed

### Symptom
Tapping "Reconnect" when the Android device is not listed in Windows 11 Bluetooth (Input or Other Devices) causes the app to stay in "Reconnecting…" indefinitely. Windows never shows a PIN pairing dialog even if the user tries to add the device manually via Windows Bluetooth settings.

### Expected behavior
After a short timeout, the app should clear the stale bond and fall back to "Waiting for connection" mode, allowing a fresh PIN pairing flow when the user adds the device in Windows Bluetooth settings.

### Reproduction steps
1. Pair Android tablet to Windows 11 PC at least once.
2. On Windows, remove the device from Bluetooth settings (or forget it).
3. On the tablet, tap "Reconnect" on the Home screen.
4. Observe the app stalls in "Reconnecting to <host>" indefinitely.
5. On Windows, open Bluetooth → Add device — no PIN dialog appears.

### Diagnostic data
N/A — behaviour is deterministic.

### Investigation
`BleHidManager.initialize()` (line 101) takes a fast path when `reconnectTarget != null && gattServer != null`: it calls only `startAdvertising()`, skipping `removeStaleBonds()`. The manager stays in `State.Reconnecting` with no timeout.

If Windows has forgotten the device it will not initiate a connection on its own, so the stall is permanent. If the user goes to Windows Bluetooth → Add device, Android presents the cached LTK; Windows no longer has the matching bond record, so SMP key validation fails silently — no PIN dialog on either side.

The same fast path exists in `reconnect()` (line 137). There is no timeout anywhere in the `Reconnecting` state machine.

Key files: `BleHidManager.kt:82-138`, `HidForegroundService.kt:87`, `HomeFragment.kt:95-98`.

### Resolution
Added `scheduleReconnectTimeout(address)` / `cancelReconnectTimeout()` helpers to `BleHidManager.kt`. The timeout (30 s) is scheduled whenever a `Reconnecting` state is entered via `initialize()` (fast path) or `reconnect()`. On expiry, if still `Reconnecting`, the specific host's Android-side bond is removed via the private `removeBond` API and state transitions to `WaitingForConnection`. `cancelReconnectTimeout()` is called on successful `STATE_CONNECTED`, `disconnect()`, `disconnectAndUnbond()`, `cleanup()`, and `forgetDevice()`.

Result: after 30 s with no response from the host, the stale LTK is cleared so Windows can perform a fresh SMP exchange and show the PIN dialog if the user adds the device in Windows Bluetooth settings. Re-pairing required after the timeout fires.
