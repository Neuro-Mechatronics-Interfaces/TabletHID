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

---

## Web — Community Configs Page (`/configs`)

A new top-level page and nav link for browsing, simulating, editing, and uploading community configs — all from the website, without the app. The API backend already exists. This section is a phased roadmap.

### Overview

Route: `/configs` with two tabs. **Tab 1 "Browser"** is the main feature. **Tab 2 "Graph"** is a reserved stub (see last section).

```
/configs
├── Tab: Browser  ← implement in phases 1–5
└── Tab: Graph    ← placeholder only for now (phase 6+)
```

The page layout on desktop: three columns.
- **Left (~280 px)** — config browser panel: filter chips, scrollable config list
- **Center (flex)** — device canvas: phone/tablet outline with the simulated control surface inside
- **Right (~280 px)** — config options panel: all settings mirroring the in-app sheets

On mobile the columns collapse to a stacked view with a floating toolbar.

---

### Phase 1 — Scaffold and Browse

Add `/configs` to `App.jsx` router and `Nav.jsx`. Create `ConfigsPage` with tab chrome (Browser active, Graph disabled/badge "Coming Soon"). No canvas yet — selecting a config just shows its metadata card.

**New files:**
- `web/src/pages/Configs.jsx` — page shell with tab state
- `web/src/pages/configs/ConfigBrowserPanel.jsx` — fetch, filter, list
- `web/src/pages/configs/ConfigCard.jsx` — single list item

**API calls:** `GET /api/v1/configs?mode=&platform=&sort=&limit=20&offset=` (existing endpoint, no server changes needed).

**UI elements:**
- Filter row: mode chips (All / Gamepad / Touch Mouse), platform chips (All / Android / iOS), sort toggle (Recent | Popular)
- Config list: profile name, device name+OS, diagonal size, mode icon, download count
- Load More button (pagination via `offset`)
- Click a card → sets active config in page state, enables canvas

---

### Phase 2 — Device Frame and Gamepad Canvas

Add the device picker and a CSS-based canvas that renders a gamepad config at its saved positions.

**Key technical decisions:**
1. **No `<canvas>` element.** Use CSS-positioned `<div>` elements inside a scaled wrapper. React synthetic events handle drag; no manual hit-testing required.
2. **Dp units throughout.** Each button's natural position is defined in dp matching the Android `ConstraintLayout` anchors. The saved `offsetX/Y` fields are also dp. The canvas wrapper uses `transform: scale(canvasScale)` so all internal maths stays in dp — no conversion needed inside components.
3. **Canvas scale.** `canvasScale = availableViewportHeight / device.heightDp` (portrait) or `/ device.widthDp` (landscape). The wrapper is a fixed dp-sized div that the CSS scale shrinks to fit.
4. **Config state = canonical API JSON.** No intermediate representation. The edit state object is exactly the shape that `validateGamepadConfig` / `validateTouchMouseConfig` accept. `JSON.stringify(state)` is what gets POSTed.

**New files:**
- `web/src/pages/configs/DeviceFrame.jsx` — phone or tablet CSS outline with screen slot
- `web/src/pages/configs/GamepadCanvas.jsx` — absolutely-positioned buttons and joystick circles
- `web/src/pages/configs/constants/devicePresets.js` — preset list (see below)

**Device presets** (store as `{ id, name, class: 'phone'|'tablet', widthDp, heightDp, density }`):
| id | name | class | widthDp | heightDp | density |
|---|---|---|---|---|---|
| `pixel-8` | Pixel 8 | phone | 411 | 914 | 2.625 |
| `pixel-fold-open` | Pixel Fold (open) | tablet | 841 | 701 | 2.2 |
| `pixel-tablet` | Pixel Tablet | tablet | 1280 | 800 | 2.0 |
| `galaxy-tab-s9` | Galaxy Tab S9 | tablet | 1035 | 663 | 2.5 |
| `iphone-15` | iPhone 15 | phone | 393 | 852 | 3.0 |
| `ipad-pro-13` | iPad Pro 13" | tablet | 1032 | 1376 | 2.0 |

The user can pick a preset or type custom dimensions. Portrait/landscape swaps widthDp and heightDp.

**GamepadCanvas** renders the 14 standard buttons, 2 joysticks, and optionally the side-toggle button. Each element:
```jsx
<div style={{
  position: 'absolute',
  left: naturalLeft,        // dp, same as Android ConstraintLayout anchor
  top: naturalTop,          // dp
  transform: `translate(${cfg.offsetX}px, ${cfg.offsetY}px) scale(${cfg.scaleX}, ${cfg.scaleY})`,
  opacity: cfg.enabled ? 1 : 0.2,
}} />
```
Joystick circles are CSS `border-radius: 50%` divs with a knob circle inside.
The active joystick tints blue/amber based on `singleJoystickOutputSide`, matching the Android `JoystickView.accentColor` behaviour.

---

### Phase 3 — Touch Mouse Canvas

Add a `TouchMouseCanvas` component for touch mouse configs.

**New files:**
- `web/src/pages/configs/TouchMouseCanvas.jsx`

The canvas renders:
- A large touch pad area (takes most of the canvas)
- Left and right button zones as colored overlays (STATIC = rectangle, DYNAMIC = circle)
- Sniper zone if `sniper.enabled`
- Sub-regions as smaller overlaid zones within button zones
- Mode label ("Touch" or "Mouse") in a corner badge

Zone coordinates (`left/top/right/bottom`) in the API are 0–1 fractions of the screen. Multiply by `canvasWidthDp` / `canvasHeightDp` to get dp positions.

---

### Phase 4 — Edit Mode

Both canvases gain a draggable edit mode matching the in-app experience.

**Interaction model:**
- `onPointerDown` + `setPointerCapture` + `onPointerMove` / `onPointerUp` for each draggable element
- Delta applied to `offsetX/Y` each move event
- Clamp to canvas bounds (same formula as `clampTranslationToParent` in `GamepadEditController.kt`)
- Single click (no drag) opens an inline popover with fine controls (offset X/Y number inputs, scale sliders)
- Pinch-to-resize via two-pointer distance ratio (mirrors Android `ScaleGestureDetector` logic)

**Config Options Panel** (right sidebar) mirrors both in-app config sheets:

*Gamepad panel:*
- Button selector dropdown (A/B/X/Y/LB/RB/LT/RT/Back/Start/D-Pad dirs)
- Per-button: Enabled toggle, Momentary/Latching behavior, Turbo on/off + duration/interval sliders
- Per-button: Custom label text field
- Trigger-only: travel distance slider (30–300), drag axis picker
- Left / Right joystick: enabled, deadzone, gain
- Single Joystick mode: enabled, side toggle enabled, output side
- Custom macro list: label + key usages display, remove button
- Vibration: Off / Light / Medium / Strong
- Reset Layout button (resets all offsets/scales to defaults)

*Touch Mouse panel:*
- Mode: TOUCH / MOUSE toggle
- Sensitivity: 1–10 slider
- Scroll: enabled toggle, invert checkbox
- Shared dynamic zone: enabled toggle, offset + radius inputs
- Left / Right button zone: enabled toggle, zone type (Static/Dynamic), behavior (Momentary/Latching)
- Sniper zone: enabled toggle, zone bounds
- Macro list (same as gamepad)

---

### Phase 5 — Save New

A "Save New Config" button in the options panel opens a modal form.

**Form fields:**
- Profile name (required, max 80 chars)
- Description (optional, max 400 chars)
- Mode (locked to current canvas mode, shown as read-only)
- Platform (Android / iOS radio, defaults to Android)
- Device (autofilled from current preset selection, editable)
- Tags (comma-separated input, max 10 tags × 30 chars)
- Category (optional, max 40 chars)
- App version (optional)

On submit: POST to `/api/v1/configs` with `config_json = JSON.stringify(editorState)` plus all metadata fields. The server already validates both schemas.

On success: show the new config ID, a "View in browser" link, and a "Share link" button. The new config appears at the top of the browser list.

On validation error: show the server's error message inline.

---

### Phase 6 (future) — Graph Tab

Stub the tab now with a "Coming Soon" state. When implemented:

**Library:** D3.js v7 force simulation (`d3-force`). Lighter than vis-network and composable with React.

**Graph model:**
- **Nodes:** one per config record. Node size = `log(downloadCount + 1)` scaled to 6–20 px radius. Color = mode (gamepad blue, touch mouse green). Label = `profileName`.
- **Edges:** two nodes share an edge for each tag they have in common. Edge weight = number of shared tags. Only render edges with weight ≥ 1.
- **Category clustering:** apply a custom force that adds a mild gravitational pull toward a category centroid. Category centroids are spaced evenly around the canvas centre. This loosely clusters configs in the same category without rigid partitioning.
- **Interaction:** hover shows a tooltip with profile name, device, download count, tags. Click loads the config into the canvas editor (same as clicking a config card in the browser list).

**Data:** fetch all config records using `GET /api/v1/configs?limit=100` (may need pagination or a dedicated graph endpoint if the corpus grows large). The graph re-renders when filters change.

**Why defer:** the graph is valuable at scale (100+ configs) but adds D3 as a dependency and significant implementation effort. The browser+canvas features are immediately useful with any number of configs.

