# Community Config — Client Architecture

Client-side contract for browsing, downloading, and sharing configs via the server defined in `spec/server-schema.md`. Both Android and iOS must implement the same merge subsets, API client interface, caching strategy, and navigation structure.

---

## Navigation

From the Home screen a **"Community"** action (card or button consistent with the existing home UI style) opens the community screen. The community screen has two tabs:

| Tab | Purpose |
|-----|---------|
| **Browse** | Fetch, filter, and selectively import configs from the server |
| **Share** | Upload the current profile's gamepad or touch mouse config |

The active profile name is always visible at the top of both tabs as a persistent chip/header so the user always knows which profile will be affected.

---

## Merge subsets

These are the canonical subset names. Both platforms implement an identical logical mapping; the Kotlin and Swift implementations must mirror each other field-for-field.

### GamepadConfig subsets

| Subset key | Fields copied from source to target |
|------------|-------------------------------------|
| `GAMEPAD_CONTROL_LAYOUT` | For every button key and both joysticks: `offsetX`, `offsetY`, `scaleX`, `scaleY` |
| `GAMEPAD_BUTTON_BEHAVIOR` | For every button key: `enabled`, `behavior`, `turbo`, `turboDurationMs`, `turboIntervalMs`; for `lt`/`rt` only: `triggerTravelDp`, `triggerAxis` |
| `GAMEPAD_JOYSTICK_SETTINGS` | `leftJoystick.{deadzone, gain}`, `rightJoystick.{deadzone, gain}`, `singleJoystickMode`, `singleJoystickSideToggleEnabled`, `singleJoystickOutputSide` |
| `GAMEPAD_MACROS` | `macroButtons`, `macroHostDefaults` |
| `GAMEPAD_LABELS` | `customButtonLabels` |
| `GAMEPAD_VIBRATION` | `vibrationIntensity` |

### TouchMouseConfig subsets

| Subset key | Fields copied from source to target |
|------------|-------------------------------------|
| `TOUCH_ZONE_POSITIONS` | `leftButton.{zoneType, staticZone, dynamicZone, subRegions}`, `rightButton.{zoneType, staticZone, dynamicZone, subRegions}`, `sharedDynamicZone`, `sharedDynamic` |
| `TOUCH_SENSITIVITY` | `sensitivity`, `scrollEnabled`, `invertScroll`, `sniperEnabled`, `sniperLeft`, `sniperTop`, `sniperRight`, `sniperBottom`, `sniperDivisor` |
| `TOUCH_BUTTON_BEHAVIOR` | `leftButton.{enabled, behavior}`, `rightButton.{enabled, behavior}` |
| `TOUCH_MACROS` | `macroButtons`, `macroHostDefaults` |

### Quick presets

Presets set the checkbox state for the user; the user may then adjust individual checkboxes (changing the selection to "Custom").

| Preset label | Subsets included |
|---|---|
| Everything | All subsets for the config mode |
| Layout only | `GAMEPAD_CONTROL_LAYOUT` / `TOUCH_ZONE_POSITIONS` |
| Macros only | `GAMEPAD_MACROS` / `TOUCH_MACROS` |
| Behaviors only | `GAMEPAD_BUTTON_BEHAVIOR` + `GAMEPAD_JOYSTICK_SETTINGS` / `TOUCH_BUTTON_BEHAVIOR` + `TOUCH_SENSITIVITY` |
| Layout + macros | Layout + Macros subsets |

---

## Merge function contract

```
merge(target: Config, source: Config, subsets: Set<SubsetKey>) → Config
```

- Returns a **new** config object; never mutates `target` or `source`.
- For each subset in `subsets`, copy exactly the fields listed above from `source` into a copy of `target`.
- Fields not covered by any selected subset remain unchanged from `target`.
- Merging a `GamepadConfig` source into a `TouchMouseConfig` target (or vice versa) is a programming error; callers must guard against mode mismatch before calling.

---

## API client interface

Both platforms implement these three operations (HTTP calls to the server at the configured base URL):

```
fetchConfigs(
  mode: String?,
  platform: String?,
  tags: List<String>?,
  sort: "recent"|"popular",
  limit: Int,
  offset: Int,
  since: String?      // ISO 8601 delta cursor; null for full refresh
) → Result<ListResponse>

fetchConfig(id: String) → Result<ConfigRecord>

uploadConfig(body: UploadBody) → Result<String>   // returns id
```

`ListResponse` shape:
```
configs:   List<ConfigRecord>
total:     Int
latest_at: String?   // ISO 8601; null if result set is empty
```

`ConfigRecord` shape: all columns from the DB schema in `spec/server-schema.md`, with `config_json` as a parsed object and `tags` as a list of strings.

---

## Local cache

Both platforms maintain a **local cache** of `ConfigRecord` objects so browse and filter are instant after the first load.

| Key | Type | Notes |
|-----|------|-------|
| `community_latest_at` | String (ISO 8601) | Cursor for next delta sync; absent = never synced |
| `community_cache_v1` | JSON array of `ConfigRecord` | All fetched records keyed by `id` |

Cache update rules:
- Full refresh (user pull-to-refresh or first open): fetch without `since`; replace cache entirely; store new `latest_at`.
- Incremental sync (background, on tab open): fetch with `since = community_latest_at`; merge new records into cache by `id` (insert new, never replace existing); update `latest_at`.
- All filtering, sorting, and search for display is performed against the in-memory cache. No additional server calls for filter changes.

---

## Browse tab UI structure

```
[Profile: Default ▾]    ← persistent chip showing active profile

[Filter chips: All | Gamepad | Touch Mouse]
[Filter chips: All | Android | iOS]
[Sort: Recent ▾]

[RecyclerView / List]
  ┌─────────────────────────────────┐
  │ 🎮  Max's weekend layout        │
  │     Motorola Moto G Stylus 5G   │
  │     Android 14 · 6.8"           │
  │     ↓ 12  •  gamepad            │
  └─────────────────────────────────┘
  ...

[Pull to refresh → full reset]
```

Tapping a row opens the **import sheet** (bottom sheet dialog).

---

## Import sheet UI structure

```
[Config title and metadata at top]

Import to: [Profile Name]  ← tappable to change profile

Quick select:
  [Everything] [Layout] [Macros] [Behaviors] [Custom]

Individual options (checkboxes):
  ☑ Control positions & sizes       (GAMEPAD_CONTROL_LAYOUT)
  ☑ Button behavior & turbo         (GAMEPAD_BUTTON_BEHAVIOR)
  ☑ Joystick settings               (GAMEPAD_JOYSTICK_SETTINGS)
  ☑ Keyboard macros                 (GAMEPAD_MACROS)
  ☐ Button labels                   (GAMEPAD_LABELS)
  ☐ Vibration                       (GAMEPAD_VIBRATION)

                        [Apply to Profile]
```

- Tapping a quick-select chip sets the checkboxes to the preset's subset combination.
- Manually toggling a checkbox changes the quick-select chip to "Custom" if the combination no longer matches any preset.
- The "Import to: [Profile Name]" line is tappable and opens a profile picker so the user can import into any profile, not just the active one.
- If the downloaded config's `mode` does not match the current screen context (e.g., a touch mouse config viewed from the gamepad flow), show a mode-mismatch notice and disable Apply.

---

## Share tab UI structure

```
[Profile: Default ▾]   ← tappable; changes which profile's configs are shown

Gamepad config
  ┌──────────────────────────────────┐
  │ Buttons: 10 enabled              │
  │ Macros: 2 (Windows defaults)     │
  │ Single joystick: off             │
  │                    [Share ↑]     │
  └──────────────────────────────────┘

Touch Mouse config
  ┌──────────────────────────────────┐
  │ Mode: Touch · Sensitivity: 7     │
  │ Left: Dynamic · Right: Dynamic   │
  │ Macros: 0                        │
  │                    [Share ↑]     │
  └──────────────────────────────────┘
```

Tapping **Share ↑** on either card opens an **upload sheet** (bottom sheet):

```
Share "[Profile Name] — Gamepad" publicly?

Profile name (editable): ________________
Description (optional):  ________________
Tags (optional):         ________________

Device: Motorola Moto G Stylus 5G (auto-filled, shown for transparency)
OS: Android 14 (auto-filled)

⚠ Uploads are public. This sends the selected config, profile name,
  description, tags, category, app version, device model/hardware
  identifier, OS version/API level, and screen size/density.
  Do not include personal information in text fields. Community content
  is user-generated; inappropriate language may be removed when detected,
  but listings are not guaranteed to be completely clean.

                [Cancel]  [Upload]
```

On success: show a snackbar/toast "Uploaded! Others can now find your config."

---

## Profile picker

Both browse and share tabs allow switching which profile is affected without leaving the screen. The profile picker is a small bottom sheet or popup showing all available profiles as a radio list. Changing profile in the picker updates the persistent chip and re-renders the share tab cards with that profile's config data.

---

## Implementation files

### Android

| File | Purpose |
|------|---------|
| `util/ConfigApiClient.kt` | HTTP client; all three API calls; uses `HttpURLConnection` (no new deps) |
| `util/CommunityConfigCache.kt` | SharedPreferences-backed cache; `latest_at` cursor; merge-insert logic |
| `util/ConfigMerger.kt` | Pure merge function for `GamepadConfig` and `TouchMouseConfig`; all subset keys |
| `ui/community/CommunityViewModel.kt` | StateFlow state; orchestrates cache + API; exposes filtered list |
| `ui/community/CommunityFragment.kt` | Two-tab host (ViewPager2 + TabLayout) |
| `ui/community/BrowseFragment.kt` | Browse tab content |
| `ui/community/ShareFragment.kt` | Share tab content |
| `ui/community/ImportSheet.kt` | BottomSheetDialogFragment; merge subset checkboxes + quick presets |
| `ui/community/UploadSheet.kt` | BottomSheetDialogFragment; editable upload form + consent disclosure |
| `res/layout/fragment_community.xml` | Tab host layout |
| `res/layout/fragment_browse.xml` | Browse RecyclerView + filter chips |
| `res/layout/fragment_share.xml` | Two config summary cards |
| `res/layout/sheet_import.xml` | Import sheet layout |
| `res/layout/sheet_upload.xml` | Upload sheet layout |
| `res/layout/item_config.xml` | RecyclerView row layout |

Navigation: add `communityFragment` destination to the nav graph; add a "Community" entry point on the Home screen.

### iOS

Mirror the Android structure using SwiftUI views:

| File | Purpose |
|------|---------|
| `Services/ConfigApiClient.swift` | HTTP client matching Android's interface |
| `Services/CommunityConfigCache.swift` | UserDefaults-backed cache; same cursor logic |
| `Services/ConfigMerger.swift` | Pure merge; same subset keys and field mappings as Android |
| `ViewModels/CommunityViewModel.swift` | `@Observable`; same state shape as Android ViewModel |
| `Views/Community/CommunityView.swift` | TabView host |
| `Views/Community/BrowseView.swift` | Browse tab |
| `Views/Community/ShareView.swift` | Share tab |
| `Views/Community/ImportSheet.swift` | Sheet with merge subset options |
| `Views/Community/UploadSheet.swift` | Upload form sheet |

---

## Base URL configuration

Both platforms read the API base URL from a build-time constant (not hardcoded):
- Android: `BuildConfig.COMMUNITY_API_BASE_URL` sourced from `local.properties`
- iOS: `Bundle.main.infoDictionary["CommunityApiBaseUrl"]` sourced from `Secrets.xcconfig`

Default value in `local.properties.example` / xcconfig template: empty string (feature is disabled until configured).
