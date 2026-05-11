# TabletHID Agent Instructions

These instructions apply to the whole repository.

## Project Shape

- `app/` contains the Android implementation.
- `ios/` contains the iOS implementation.
- `spec/` is the source of truth for feature status, platform parity, and cross-platform implementation notes.
- `web/` contains the public documentation/support website.

Before implementing or reviewing a feature, look up its current status in `spec/`, especially the feature-status-by-platform material in `spec/platform-feature-status.md`. Treat that file as the canonical matrix for what Android and iOS currently support.

## Feature Workflow

1. Identify the feature area in `spec/platform-feature-status.md`.
2. Confirm the current Android and iOS statuses before changing code.
3. Implement the feature in the appropriate platform directory, following the local style and architecture.
4. Update tests or add focused coverage when the change affects HID reports, config persistence, transport state, or user-visible workflows.
5. After implementation, update `spec/platform-feature-status.md` in the same change.

## Updating `spec/`

When a feature is implemented, changed, or intentionally deferred:

- Update the relevant row in `spec/platform-feature-status.md`.
- Keep Android and iOS statuses explicit. Use concise status labels such as `Implemented`, `Ported`, `Initial`, `Experimental`, `TODO`, or a short clarifying phrase.
- If the feature behavior differs by platform, state the difference in the table cell instead of hiding it in prose.
- If implementation changes Bluetooth descriptors, report bytes, pairing behavior, persistence keys, profile defaults, or platform limitations, update the relevant explanatory section too.
- If a feature remains incomplete, describe the missing part directly, for example `Implemented; physical-device validation pending`.
- Add new rows under the closest existing heading rather than creating duplicate sections.
- Do not mark a feature complete unless the code path exists and has been manually or automatically verified enough for the claim.

Use this completion checklist before finishing feature work:

- Code is implemented.
- Relevant tests or manual validation notes are complete.
- `spec/platform-feature-status.md` reflects the final state on every affected platform.
- If a config data class changed (`GamepadConfig`, `ButtonConfig`, `JoystickConfig`, `TouchMouseConfig`, `ButtonZoneConfig`, `TouchMouseSubRegionConfig`, `KeyboardMacroButtonConfig`, or iOS equivalents): `spec/server-schema.md` is updated per the agent workflow in that file.
- `README.md`, `TODO.md`, `ios/README.md`, or other project docs are updated if their claims changed.
- Website docs under `web/` are reviewed using the workflow below.

## Using SUGGESTIONS.md

`SUGGESTIONS.md` is an informal staging area for feature ideas that have not yet been accepted into the backlog. It exists one step before `TODO.md` in the planning pipeline.

- Do **not** implement items from `SUGGESTIONS.md` directly. They have not been reviewed or prioritised by the user.
- When you have a new feature idea that is not already captured in `TODO.md` or `ios/TODO.md`, add it to `SUGGESTIONS.md` under the most relevant heading. Keep the description concise and practical: what the feature does, a hint at where it would live in the code, and any obvious constraints.
- Do not duplicate items that are already in `TODO.md`. If a suggestion is accepted, the user moves it to `TODO.md` and deletes it from `SUGGESTIONS.md`.
- When asked to propose improvements or brainstorm features, write them to `SUGGESTIONS.md` rather than directly into `TODO.md`.

## Using TODO Files

Use TODO files as the working backlog, not as the canonical feature-status record. The spec tells you what is currently true; TODO files tell you what still needs doing.

- `TODO.md` is the root/product backlog. It tracks cross-platform feature ideas, Android implementation work, shared HID/report work, website-facing polish, and project-wide quality tasks.
- Platform-level TODO files, currently `ios/TODO.md`, track platform-specific bring-up, parity gaps, validation blockers, release tasks, and implementation details that do not belong in the shared backlog.
- Before starting a feature, check both `TODO.md` and the relevant platform TODO. A root item may need one or more platform-specific child tasks.
- When completing a root TODO that has platform-specific work, mark the root item done only when every required platform is done or the remaining platform gap is explicitly split into that platform's TODO.
- When adding new work, put user-visible feature intent in `TODO.md` and platform implementation details in the matching platform TODO.
- When a TODO changes from planned to implemented, update `spec/platform-feature-status.md` at the same time.
- Keep TODO wording actionable: start with the user-visible capability, then note important constraints such as descriptor stability, pairing impact, or platform limitations.

## Website Documentation Workflow

Any user-facing feature, setup change, permission change, limitation change, release availability change, screenshot-worthy UI change, or troubleshooting change requires a `web/` documentation review.

### Website Content Map

| Documentation need | Primary files |
| --- | --- |
| Product overview, platform availability, core feature list | `web/src/pages/Home.jsx` |
| Setup, walkthroughs, troubleshooting, FAQ, local logs | `web/src/pages/Support.jsx` |
| Privacy, local storage, permissions, data handling | `web/src/pages/Privacy.jsx` |
| Navigation or route structure | `web/src/App.jsx`, `web/src/components/Nav.jsx`, `web/src/components/Footer.jsx` |
| Styling and responsive layout | `web/src/index.css` |
| Screenshots and static images | `web/img/`, referenced from page components |
| SEO/static metadata | `web/index.html`, `web/public/robots.txt`, `web/public/sitemap.xml` |

### Website Update Schema

For each feature change, decide whether each field below is `yes`, `no`, or `n/a`, then update the named files when needed:

```text
Feature:
Platforms affected:
User-facing behavior changed:
Setup/pairing steps changed:
Permissions/privacy/storage changed:
Troubleshooting implications:
Screenshots need refresh:
Website files updated:
Website validation performed:
```

Apply these rules:

- If the change affects what users can do, update feature descriptions in `Home.jsx` and any relevant walkthrough copy in `Support.jsx`.
- If the change affects setup, pairing, reconnect, host compatibility, logs, or failure modes, update `Support.jsx`.
- If the change affects permissions, local storage, Bluetooth behavior, logs, analytics, networking, or data sharing, update `Privacy.jsx`.
- If a UI screen changed enough that existing screenshots would mislead users, replace or add images in `web/img/` and update imports/usages in the relevant page.
- If routes are added, removed, or renamed, update `App.jsx`, navigation/footer links, `web/public/sitemap.xml`, and any internal links.
- Keep website copy consistent with `spec/platform-feature-status.md`. If they disagree, update both or explain the temporary difference in the spec.

### Website Validation

When `web/` changes:

- Run `npm install` in `web/` only if dependencies are missing or `package.json` changed.
- Run `npm run build` from `web/` before finishing.
- For layout or screenshot-heavy changes, also run `npm run dev` and inspect the affected route in a browser when possible.
- Do not commit generated `web/dist/` output unless the repository already tracks it for deployment.

## Using BUGFIXES.md

`BUGFIXES.md` is the bug tracker. It sits alongside `TODO.md` in the planning pipeline but follows a different workflow because bugs require diagnosis before a fix can be scoped.

### Filing a bug

When the user reports a bug, add an entry to the **Active bugs** section of `BUGFIXES.md` using the template at the top of that file. Fill in as many fields as you can from what the user described, then ask for any missing **Diagnostic data** (logcat, report bytes, descriptor hex, host-side screenshots) before investigating.

### Investigating a bug

1. Read the full bug entry.
2. Match the **Category** field to the corresponding specialist section in `.skills/bug-investigation.md`.
3. Read every file listed under "Read first" for that category — do not hypothesise before reading the source.
4. Write your root-cause explanation under **Investigation** in the bug entry, citing file paths and line numbers.
5. Implement the fix, then fill in **Resolution** with what changed and whether re-pairing is required.

### Choosing the right specialist

| Category | Trigger | Key domain knowledge needed |
| --- | --- | --- |
| **HID Descriptor** | Wrong axis direction, swapped sticks, button index off, triggers not analog | HID 1.11 spec, Usage Pages/IDs, axis byte order, Logical Min/Max polarity |
| **BLE Transport** | Pairing fails, "incorrect PIN", GATT errors, not discovered | SMP bonding, `createBond()`, stale LTK, GATT server lifecycle |
| **Touch Input** | Zone miss, wrong pointer, drift, dynamic follower jump | `MotionEvent` pointer routing, accumulation, hit-test order |
| **Gamepad UI** | Widget off-screen, edit mode broken, trigger travel wrong | Fractional layout coords, overlay lifecycle, trigger float→byte mapping |
| **Config Persistence** | Profile not saved, wrong defaults, reset fails | Profile-namespaced keys, `__saved` sentinel, raw resource defaults |
| **Host Compatibility** | Works on Windows but not macOS, Steam-specific, axis differs by host | Windows XInput vs HID, macOS IOHIDDevice open requirement |
| **Android Platform** | Permission crash, service killed, orientation ignored | AndroidManifest, foreground service, API-level quirks |
| **iOS Platform** | CBPeripheralManager error, background disconnect | Core Bluetooth peripheral mode, HoG entitlements |

### After fixing

- Move the entry from **Active bugs** to **Resolved bugs** in `BUGFIXES.md`.
- Update `spec/platform-feature-status.md` if the bug revealed a status that was wrong.
- Note whether the fix requires the host to forget and re-pair (applies to any HID descriptor or BLE transport change).

## Build Coordination

- Do not run Gradle builds, tests, or Kotlin compile tasks in parallel with another agent or shell session.
- Before starting `./gradlew.bat` or `./gradlew`, make sure no other Gradle command is already running for this repo.
- Run Android verification sequentially, for example `:app:testDebugUnitTest` first and `:app:assembleDebug` after it completes.
- If Kotlin incremental caches are corrupted after an interrupted or overlapping build, run `./gradlew.bat --stop` and rerun sequentially with `"-Dkotlin.incremental=false"`.

## Platform Notes

- Android uses Classic Bluetooth HID through `BluetoothHidDevice`; emulator support is not sufficient for HID peripheral validation.
- iOS BLE HID transport is experimental unless the spec says otherwise; be careful not to present it as fully equivalent to Android without validation.
- HID descriptor or report-layout changes can require host re-pairing because hosts cache descriptors.

## Git Hygiene

- Keep edits scoped to the requested work.
- Do not revert user changes.
- Prefer updating the spec and docs in the same change as implementation so feature status does not drift.
