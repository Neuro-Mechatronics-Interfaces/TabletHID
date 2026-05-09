# Verification/Release Validation

Use this role for pre-finish checks, release readiness, HID validation, and cross-platform confidence passes.

## Scope

Validate only what changed, but widen checks when the change touches shared HID reports, descriptor bytes, pairing/reconnect behavior, profile/config persistence, permissions, public docs, or release metadata.

## Android Checks

- Build coordination: run only one Gradle command at a time for this repo. Do not parallelize Gradle builds/tests across agents or terminals; overlapping Kotlin compile tasks can corrupt incremental caches under `app/build/kotlin`.
- Build/test: run relevant Gradle checks such as `./gradlew test`, `./gradlew assembleDebug`, or release build tasks when signing/R8 behavior is in scope.
- Cache recovery: if a Gradle/Kotlin cache is corrupted after an interrupted or overlapping run, run `./gradlew.bat --stop` and rerun verification sequentially with `"-Dkotlin.incremental=false"`.
- HID validation: physical Android hardware is required for `BluetoothHidDevice`; emulator validation is not enough.
- Pairing/reconnect: verify discoverable pairing, bonded-host reconnect, disconnect, forget/unbond where affected.
- Input reports: verify mouse report size/fields, gamepad report size/fields, neutral values for disabled controls, hat diagonals, triggers, scroll axes.

## iOS Checks

- Project generation/build: use existing Xcode project or XcodeGen flow when relevant.
- Build/test: run `xcodebuild build`, `xcodebuild build-for-testing`, and `xcodebuild test` when local simulator launch permits it.
- BLE HID validation: record physical iPhone/iPad and host OS coverage; do not treat simulator success as HID transport validation.
- Release readiness: review `ios/APP_STORE.md`, bundle ID, privacy manifest, screenshots, TestFlight archive/upload, and App Review notes when release work is in scope.

## Website/Docs Checks

- If `web/` changes, run `npm run build` from `web/`.
- For layout or screenshot-heavy changes, run `npm run dev` and inspect affected routes when possible.
- Confirm website claims match `spec/platform-feature-status.md`.

## Finish Criteria

- Code/docs changed as intended.
- Relevant automated checks ran, or blockers are named.
- Manual HID or release validation is recorded with device/host details when required.
- `spec/platform-feature-status.md`, TODO files, README/platform docs, and website docs reflect the final truth.
