# Suggestions

This file is a staging area for feature ideas that have not yet been accepted into the backlog. Review each item and move the ones you want to pursue into `TODO.md` (and the matching platform `TODO.md` if platform-specific work is needed). Delete or archive items that are out of scope.

Items in this file do **not** follow the full TODO workflow; they are intentionally informal. Once an item moves to `TODO.md` it becomes subject to the feature workflow in `AGENTS.md` and `CLAUDE.md`.

---

## Input & Control

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

### App home screen shortcuts
Android app shortcuts (`shortcuts.xml`) that launch directly into Touch Mouse or Gamepad mode with a specific profile, bypassing the Home fragment. Users long-press the launcher icon to pick a shortcut. Shortcut list could be auto-generated from the saved profile list.

### Keyboard shortcut launcher panel
A scrollable grid of user-defined or preset keyboard shortcuts (Ctrl+Z, Ctrl+C, Win+D, Alt+F4, etc.) accessible as a side-panel or overlay within Touch Mouse mode. Each button sends one `sendKeyboardReport` call. Could reuse the existing macro button infrastructure.

### Profile import/export via file
Export the current profile's SharedPreferences XML to a named `.json` or `.xml` file that the user can share, back up, or copy to another device. Import reads the file and creates a new custom profile. Extends the existing "Export Config" developer feature into a first-class user action.

---

## Customization

### Mouse acceleration curve editor
Replace the linear 1–10 sensitivity slider with a simple curve editor offering three presets — Linear, Fast-start (high initial speed, plateaus), Precision (slow initial speed, ramps up). Implemented as a piecewise multiplier applied before the EMA step.

### Per-host configuration profiles
Associate a default profile with each paired host address stored in `HidHostStore`. When reconnecting to a known host, automatically switch to its saved profile instead of keeping the last-active profile.

### Custom button icons or labels
Allow users to rename any gamepad button (e.g., relabel A/B/X/Y for a specific game) or pick from a small icon library. Stored per profile. Purely cosmetic; no HID report changes.

### Gamepad layout portrait mode
A compact gamepad layout variant optimised for portrait orientation — controls rearranged for one-handed or thumb-reachable use. Added as a layout preset option alongside the existing landscape layout.

---

## Feedback & Diagnostics

### Simulated rumble via device vibrator
When the connected host sends an HID output report (rumble command), translate it into a `VibrationEffect` on the Android device. This closes the rumble loop: the tablet confirms rumble commands are being sent. Requires implementing the output report handler in `BleHidManager.onCharacteristicWriteRequest`.

### Connection quality / latency indicator
Display a small colour-coded dot (green/yellow/red) on the status bar estimating BLE report delivery quality. Could be derived from consecutive `updateValue` return values (`false` indicates back-pressure / congestion) rather than true round-trip latency.

### Notification sound or LED for connection events
Play a short tone or flash the notification LED (where available) when the HID connection is established or lost. Configurable in Settings. Helps users know connection state without looking at the screen.

---

## Platform & Infrastructure

### iOS parity entry in web documentation
The Support page (`web/src/pages/Support.jsx`) could include a section explicitly listing which features are iOS-only experimental, which are parity with Android, and which are pending. Currently the site is Android-centric. Useful once the iOS transport strategy is decided.

### Battery saver mode
Automatically drop the mouse report rate to 20 Hz and reduce BLE advertise power when battery is below a configurable threshold (default 20%). Implemented in `HidViewModel` by observing `BatteryManager` broadcast or `BatteryManager.EXTRA_LEVEL`.

### Android home screen widget
A resizable `AppWidgetProvider` widget that shows connection status (device name + connected/idle) and provides a single Reconnect or Disconnect button, without opening the full app. Complements the Quick Settings tile for users who prefer widgets.
