# Bug Investigation

Use this role when working a bug entry from `BUGFIXES.md`. Read the entry, select the specialist sub-section below that matches the **Category** field, gather the listed context, then investigate and write findings back under **Investigation** and **Resolution** in the bug entry.

## General protocol

1. Read the full bug entry in `BUGFIXES.md`.
2. Match the **Category** field to a specialist section below.
3. Gather every file listed under "Read first" for that category before forming a hypothesis.
4. Do not guess at root cause before reading the relevant source — HID and BLE bugs especially have non-obvious byte-level causes.
5. Write a clear root-cause explanation under **Investigation**, citing file paths and line numbers.
6. Implement the fix, note which files changed and whether re-pairing is required, then fill in **Resolution**.
7. Update `spec/platform-feature-status.md` if the bug reveals a status that was wrong.

---

## HID Descriptor / Report Structure

**When to use**: axis direction wrong or inverted, sticks swapped, buttons don't register, triggers not analog, host assigns wrong device type (mouse treated as joystick, etc.), report bytes parsed incorrectly by host.

**Domain knowledge required**:
- USB HID 1.11 spec: Usage Pages, Usage IDs, Report Descriptor items (short-form tag/type/size encoding).
- Generic Desktop Usage Page (0x01): X=0x30, Y=0x31, Z=0x32, Rx=0x33, Ry=0x34, Rz=0x35, Hat=0x39.
- Xbox-standard axis layout: Left stick = X/Y, Right stick = Rx/Ry, Triggers = Z/Rz (left=Z, right=Rz).
- Logical Minimum / Logical Maximum define value range; swapping them inverts the axis direction.
- Axes must be declared in the same order in the descriptor as they are packed in the report byte array.
- `INPUT (Data, Variable, Relative)` for mouse; `INPUT (Data, Variable, Absolute)` for joystick/trigger axes.
- Report ID byte is prepended to every report when multiple collections share one interface; byte offsets shift by 1.
- Hosts cache the HID descriptor across sessions; descriptor changes require the host to forget and re-pair.
- Hat switch: 8-direction encoding (0=N, 1=NE, … 7=NW, 8=released); sent as 4-bit field in the report.

**Read first**:
- `app/src/main/java/com/tablet/hid/bluetooth/HidReportDescriptors.kt` — full descriptor byte arrays and report-build helpers.
- `app/src/test/java/com/tablet/hid/bluetooth/` — unit tests for report byte construction; run these first to check for regressions.
- `app/src/main/java/com/tablet/hid/bluetooth/BleHidManager.kt` — how reports are assembled and sent via `sendReport()`.

**Common root causes and fixes**:
- *Axis inverted*: check `LOGICAL_MINIMUM` / `LOGICAL_MAXIMUM` order in the descriptor; or check that the value sent is `axisValue` not `255 - axisValue`.
- *Sticks swapped*: verify axis Usage IDs match the byte order in the report. If Left stick uses X/Y and Right stick uses Rx/Ry, the bytes must be packed in that order.
- *Trigger not analog*: ensure `INPUT (Data, Variable, Absolute)` and a range of `0x00`–`0xFF` (not a 1-bit button field).
- *Button index off by one*: count bits in the descriptor button array; padding bits at the end of a byte must be `INPUT (Const)`.
- *Host re-paired needed*: always note in **Resolution** if descriptor bytes changed.

---

## BLE Transport

**When to use**: pairing fails, "incorrect PIN or passkey", connection drops immediately, GATT error codes in logcat, device not discovered by host, state machine stuck, reconnect fails.

**Domain knowledge required**:
- BLE advertising: `AdvertiseSettings`, `AdvertiseData`, `startAdvertising()`. Advertising stops after first connection.
- GATT server: service/characteristic/descriptor setup; `PERMISSION_READ_ENCRYPTED` on the report characteristic is required for HID-over-GATT.
- SMP bonding: `createBond()` from the peripheral side sends a Security Request, causing the central to initiate Pairing Request and show a PIN dialog on both sides. Without `createBond()`, the host may silently refuse to bond.
- Stale LTK: if Android already has a bond record for the host but the host has forgotten it, the host rejects `EncryptionRequest` with an LTK-not-found error, which surfaces as "incorrect PIN." Fix: call `removeBond()` via reflection before starting a fresh pair.
- `BluetoothGattServerCallback.onConnectionStateChange`: `STATE_CONNECTED` fires before bonding completes; do not send HID reports until `ACTION_BOND_STATE_CHANGED` reaches `BOND_BONDED`.
- GATT server must stay alive across reconnects; rebuilding it forces the host to re-read the service table.

**Read first**:
- `app/src/main/java/com/tablet/hid/bluetooth/BleHidManager.kt` — full transport state machine.
- `app/src/main/java/com/tablet/hid/bluetooth/HidHostStore.kt` — persisted host address list used for stale-bond detection.
- Logcat tags: `BleHidManager`, `BluetoothGatt`, `bt_btif`, `SMP`.

**Common root causes and fixes**:
- *Pairing dialog never appears on tablet*: `createBond()` not called on `STATE_CONNECTED`.
- *"Incorrect PIN"*: stale Android bond for host address; `removeBond()` via reflection in `removeStaleBonds()` before fresh pair.
- *GATT error 133 / 8*: connection parameter or MTU negotiation timing; add a short retry with backoff.
- *Reports silently ignored after reconnect*: GATT server was rebuilt, so host needs to re-discover services; keep server alive and only restart advertising.

---

## Touch Input / Gesture

**When to use**: zones not triggered, wrong pointer assigned to movement, scroll misfires, click accumulates drift, dynamic follower jumps, multi-touch routing wrong.

**Domain knowledge required**:
- Android `MotionEvent`: `ACTION_DOWN` carries pointer index 0; `ACTION_POINTER_DOWN` adds subsequent pointers; `getPointerId()` maps index to stable ID.
- Zone hit-testing must run on `ACTION_DOWN` / `ACTION_POINTER_DOWN` before assigning the movement pointer so the first touch doesn't steal a click zone.
- Sub-pixel accumulation (`accumDx` / `accumDy`): accumulate fractional deltas and only send integer HID delta to prevent quantization noise.
- Dynamic follower: position is derived from the movement pointer's current location plus a configured offset vector; must update on every `ACTION_MOVE`.
- Latching vs momentary: latching click state is stored in ViewModel and toggled on `ACTION_DOWN`; momentary clears on `ACTION_UP`.

**Read first**:
- `app/src/main/java/com/tablet/hid/ui/touchmouse/TouchZoneOverlayView.kt`
- `app/src/main/java/com/tablet/hid/ui/touchmouse/TouchMouseFragment.kt`
- `app/src/main/java/com/tablet/hid/model/TouchMouseConfig.kt`

---

## Gamepad UI

**When to use**: widget off-screen after resize, edit mode broken, trigger travel incorrect, turbo mis-fires, D-pad diagonal missing, button label clipped, layout not persisted.

**Read first**:
- `app/src/main/java/com/tablet/hid/ui/gamepad/GamepadFragment.kt`
- `app/src/main/java/com/tablet/hid/util/GamepadConfigStore.kt`
- `app/src/main/res/layout/fragment_gamepad.xml`

**Key constraints**:
- Layout positions are stored as fractions of screen size, not absolute pixels, so they survive rotation and device changes.
- Trigger level is a float 0–1 mapped to axis byte 0–255 before sending; verify the mapping does not clamp incorrectly.
- Edit mode uses a `FLAG_DIM_BEHIND`-cleared overlay; make sure the overlay is removed on `Done` or back-press.

---

## Config Persistence

**When to use**: profile not saved, wrong defaults loaded on first launch, reset doesn't clear, config survives uninstall unexpectedly, migration crash after update.

**Read first**:
- `app/src/main/java/com/tablet/hid/util/GamepadConfigStore.kt`
- `app/src/main/java/com/tablet/hid/util/TouchMouseConfigStore.kt`
- `app/src/main/java/com/tablet/hid/util/ProfileStore.kt`
- `app/src/main/res/raw/` — default config XML files loaded on first use.

**Key constraints**:
- All config keys are profile-namespaced: `gamepad_config_<profileKey>`, `touch_mouse_config_<profileKey>`.
- The `__saved` sentinel distinguishes "never configured" from "explicitly saved empty"; do not break this invariant.
- Raw resource defaults are loaded once when the key is absent; after that the SharedPreferences value is canonical.

---

## Host Compatibility

**When to use**: works on one host OS but not another, axis mapping differs in Steam vs bare OS, gamepad not recognized as gamepad, mouse cursor jumps on one host.

**Domain knowledge required**:
- Windows: `HID.dll` + `hidclass.sys`; gamepad devices are surfaced via `XInput` only if the descriptor matches the Xbox HID-compatible descriptor class. Otherwise they appear as a generic joystick.
- macOS: `IOHIDDevice`; gamepad requires the app to open the device (`IOHIDDeviceOpen`); mouse events are consumed by the OS automatically.
- Linux: `hid-generic` driver; axis mapping via `udev` rules; evdev axis codes assigned by Usage ID order in descriptor.
- Host caches descriptors: changing descriptor bytes requires the host to forget and re-pair.

**Read first**:
- `app/src/main/java/com/tablet/hid/bluetooth/HidReportDescriptors.kt`
- Any test files that validate report byte construction.
- The bug entry's **Diagnostic data** for host-side device manager screenshots or `hid-recorder` output.

---

## Android Platform

**When to use**: permission crash, foreground service killed by OS, orientation lock ignored, `requestedOrientation` has no effect, API-level regression on specific Android version.

**Read first**:
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/tablet/hid/HidForegroundService.kt`
- `app/src/main/java/com/tablet/hid/MainActivity.kt`

---

## iOS Platform

**When to use**: CBPeripheralManager fails to start, background disconnect, host can't discover iPad, report not received by host, HoG service not enumerated.

**Read first**:
- `ios/` source tree — start with the BLE peripheral manager and HID service setup.
- `spec/platform-feature-status.md` — confirm current iOS experimental status before assuming a feature should work.

**Key constraints**:
- iOS BLE HID transport is experimental; physical-device validation (not Simulator) is required before marking anything fixed.
- Background advertising requires the `bluetooth-peripheral` UIBackgroundModes entitlement.
