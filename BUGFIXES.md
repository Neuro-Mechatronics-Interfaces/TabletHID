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

<!-- Move entries here once Status = Fixed, with Resolution filled in. -->
