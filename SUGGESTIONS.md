# Suggestions

This file is a staging area for feature ideas that have not yet been accepted into the backlog. Review each item and move the ones you want to pursue into `TODO.md` (and the matching platform `TODO.md` if platform-specific work is needed). Delete or archive items that are out of scope.

Items in this file do **not** follow the full TODO workflow; they are intentionally informal. Once an item moves to `TODO.md` it becomes subject to the feature workflow in `AGENTS.md` and `CLAUDE.md`.

---

## Input & Control

### Last-used mode memory for reconnect and widget
Remember whether the user last used Touch Mouse or Gamepad mode (persist in `HidPrefs`) and use that mode when auto-reconnecting on app open, when the widget's Reconnect button is tapped, and when launching via a home-screen shortcut that doesn't specify a mode. Currently all reconnect paths hardcode `DeviceMode.TOUCH_MOUSE`.

### Scroll sensitivity independent of movement sensitivity
Add a separate "Scroll speed" slider in the Touch Mouse config sheet that scales the three-finger scroll delta independently of the movement sensitivity. Currently scroll output is gated through the same `baseScale` as pointer movement, so sensitivity changes affect both. The new multiplier would apply only in the three-finger scroll path in `TouchMouseFragment`.

### Macro sequence with inter-step delays
Extend `KeyboardMacroButtonConfig` (or add a new `MacroSequenceButtonConfig`) to support multi-step sequences where each step is a modifier+key combo followed by a configurable delay (default 0 ms, range 0–2000 ms). Useful for game combos or application macros that require brief pauses between keys. The single-step macro format is preserved as the common case.

### Configurable mouse report rate
Allow users to select the HID report frequency: 20 Hz (battery saver), 50 Hz (default), or 100 Hz (low-latency gaming). Currently fixed at 50 Hz in `HidViewModel`. The rate drives the EMA smoothing timer, so the EMA alpha would also need tuning per rate. Requires a setting field and a SharedPreferences key.

### Gesture zones for media / system key shortcuts
Configurable swipe-from-edge zones in Touch Mouse mode that send HID Consumer Control report codes (play/pause, next track, volume up/down, mute). Implemented as additional static zones that trigger keyboard/consumer reports rather than mouse reports.

### Inactivity auto-disconnect
Optionally disconnect after N minutes of no HID input (configurable 1–60 min, or off). Could live in the Settings dialog alongside other connection options. Prevents draining Bluetooth when the app is left open accidentally.

---

## Productivity & Shortcuts

### Android Quick Settings tile
A `TileService` that shows connection status (idle / connected / error) in the notification shade. Tapping the tile toggles discoverable mode or triggers a reconnect attempt to the last paired device. Requires `android.permission.BIND_QUICK_SETTINGS_TILE` and a tile icon.

### Profile import/export via file
Export the current profile's SharedPreferences XML to a named `.json` or `.xml` file that the user can share, back up, or copy to another device. Import reads the file and creates a new custom profile. Extends the existing "Export Config" developer feature into a first-class user action.

---

## Customization

### Gamepad button color customization
Allow per-button color overrides stored in `GamepadConfig` alongside the new `customButtonLabels` map. A color picker or a small preset palette (per button or "all buttons one color") would live in `GamepadConfigSheet`. A/B/X/Y currently have hard-coded Xbox palette in `GamepadFragment`; the override would replace those values when present.

### Drag-lock active state indicator in Touch Mouse canvas
When the double-tap-and-hold drag-lock is latched, show a subtle on-screen badge or icon overlay (e.g., a small padlock icon near the pointer origin or in the status bar area) so the user can clearly tell the latch is active. Currently there is no visual distinction between a normal touch drag and a latched drag-lock drag.

### Mouse acceleration curve editor
Replace the linear 1–10 sensitivity slider with a simple curve editor offering three presets — Linear, Fast-start (high initial speed, plateaus), Precision (slow initial speed, ramps up). Implemented as a piecewise multiplier applied before the EMA step.

### Per-host configuration profiles
Associate a default profile with each paired host address stored in `HidHostStore`. When reconnecting to a known host, automatically switch to its saved profile instead of keeping the last-active profile.



### Gamepad layout portrait mode
A compact gamepad layout variant optimised for portrait orientation — controls rearranged for one-handed or thumb-reachable use. Added as a layout preset option alongside the existing landscape layout.

---

## Feedback & Diagnostics

### Turbo active pulse animation on gamepad buttons
When a button is in turbo mode and currently firing, briefly highlight it on each press cycle (e.g., flash to white or cycle the existing active color). Currently turbo fires at the configured rate but the button has no visual indication that turbo is active beyond the config sheet toggle. Could be implemented in the turbo `Job` coroutine in `GamepadFragment` by toggling a tint between press and release.

### In-app session log viewer
Add a read-only session log list screen accessible from Settings. Lists the timestamped `.log` files in app storage, lets the user tap one to view its contents, and provides share/delete actions. The existing share-file intent from the DEV export feature can be reused for the share action. Avoids requiring file manager access to read session logs.

### Simulated rumble via device vibrator
When the connected host sends an HID output report (rumble command), translate it into a `VibrationEffect` on the Android device. This closes the rumble loop: the tablet confirms rumble commands are being sent. Requires implementing the output report handler in `BleHidManager.onCharacteristicWriteRequest`.

### Connection quality / latency indicator
Display a small colour-coded dot (green/yellow/red) on the status bar estimating BLE report delivery quality. Could be derived from consecutive `updateValue` return values (`false` indicates back-pressure / congestion) rather than true round-trip latency.

### Notification sound or LED for connection events
Play a short tone or flash the notification LED (where available) when the HID connection is established or lost. Configurable in Settings. Helps users know connection state without looking at the screen.

---

## Platform & Infrastructure

### Widget profile selector
Extend the home-screen widget to show the active profile name and expose a tap-to-cycle or tap-to-open action that switches between saved profiles. Requires writing the active profile key to `HidWidgetState` SharedPreferences and reflecting it in `RemoteViews`. Most useful when a user has profiles set up for different apps or games and wants to switch without opening the full app.



### iOS parity entry in web documentation
The Support page (`web/src/pages/Support.jsx`) could include a section explicitly listing which features are iOS-only experimental, which are parity with Android, and which are pending. Currently the site is Android-centric. Useful once the iOS transport strategy is decided.

### Battery saver mode
Automatically drop the mouse report rate to 20 Hz and reduce BLE advertise power when battery is below a configurable threshold (default 20%). Implemented in `HidViewModel` by observing `BatteryManager` broadcast or `BatteryManager.EXTRA_LEVEL`.


