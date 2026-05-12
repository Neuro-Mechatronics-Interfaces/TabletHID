import SwiftUI

/// Bottom sheet for importing a community config into a local profile.
///
/// Behaviour mirrors the Android ImportSheet:
/// - Gamepad layout preview with landscape/portrait toggle (gamepad configs only).
/// - Metadata section at top.
/// - "Import to" profile picker.
/// - Quick-select preset chips (Everything, Layout, Macros, Behaviors, Custom).
/// - Individual subset toggles that update the quick-select chip.
/// - Apply: deserialise config_json → rescale offsets if source device differs → merge
///   selected subsets → save → dismiss.
struct ImportSheet: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss

    let record: CommunityConfigRecord
    let viewModel: CommunityViewModel

    @State private var targetProfile: Profile
    @State private var showProfilePicker = false
    @State private var selectedPreset: ImportPreset = .everything
    @State private var gamepadSubsets: Set<GamepadSubset> = Set(GamepadSubset.allCases)
    @State private var touchSubsets: Set<TouchMouseSubset>   = Set(TouchMouseSubset.allCases)
    @State private var isApplying = false
    @State private var applyError: String? = nil
    @State private var thumbnailLandscape = true

    init(record: CommunityConfigRecord, viewModel: CommunityViewModel) {
        self.record = record
        self.viewModel = viewModel
        _targetProfile = State(initialValue: Profile.defaultProfile)
        // Init thumbnail orientation from config JSON; default landscape when SYSTEM.
        let isLandscape: Bool = {
            guard record.mode == "gamepad", !record.configJson.isEmpty,
                  let data = record.configJson.data(using: .utf8),
                  let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let pref = root["orientationPreference"] as? String
            else { return true }
            return pref.uppercased() != "PORTRAIT"
        }()
        _thumbnailLandscape = State(initialValue: isLandscape)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if record.mode == "gamepad" && !record.configJson.isEmpty {
                        thumbnailSection
                    }
                    metadataSection
                    profilePickerRow
                    quickSelectSection
                    subsetsSection
                    if let err = applyError {
                        Text(err)
                            .font(.caption)
                            .foregroundStyle(.red)
                    }
                    applyButton
                }
                .padding(20)
            }
            .navigationTitle("Import Config")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .onAppear {
            targetProfile = appState.activeProfile
            resetToPreset(.everything)
        }
        .sheet(isPresented: $showProfilePicker) {
            profilePickerSheet
        }
    }

    // MARK: - Thumbnail

    private var thumbnailSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            GamepadThumbnailView(
                configJson: record.configJson,
                isLandscape: thumbnailLandscape,
                customCanvasLong: thumbnailCanvasLong,
                customCanvasShort: thumbnailCanvasShort
            )
            .cornerRadius(10)

            Button {
                thumbnailLandscape.toggle()
            } label: {
                Label(thumbnailLandscape ? "Landscape" : "Portrait",
                      systemImage: thumbnailLandscape ? "rectangle.landscape" : "rectangle.portrait")
                    .font(.caption.weight(.medium))
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
        }
    }

    /// Returns the rescaled canvas long/short to use for thumbnail rendering,
    /// or nil to use the default 600/340 reference.
    private var thumbnailCanvasLong: Double? {
        #if canImport(UIKit)
        guard let srcW = record.deviceScreenWidthPx,
              let srcH = record.deviceScreenHeightPx,
              let srcDpi = record.deviceScreenDensityDpi
        else { return nil }
        let (tgtLong, tgtShort) = LayoutRescaler.canvasDimsFromScreen()
        let (srcLong, srcShort) = LayoutRescaler.canvasDimsFromScreenPx(widthPx: srcW, heightPx: srcH, densityDpi: srcDpi)
        let srcW2 = thumbnailLandscape ? srcLong : srcShort
        let tgtW2 = thumbnailLandscape ? tgtLong : tgtShort
        let srcH2 = thumbnailLandscape ? srcShort : srcLong
        let tgtH2 = thumbnailLandscape ? tgtShort : tgtLong
        guard abs(srcW2 - tgtW2) > 1.0 || abs(srcH2 - tgtH2) > 1.0 else { return nil }
        return tgtLong
        #else
        return nil
        #endif
    }

    private var thumbnailCanvasShort: Double? {
        #if canImport(UIKit)
        guard thumbnailCanvasLong != nil else { return nil }
        return LayoutRescaler.canvasDimsFromScreen().1
        #else
        return nil
        #endif
    }

    // MARK: - Metadata

    private var metadataSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(record.profileName)
                .font(.title2.weight(.bold))
            HStack {
                Text(record.mode == "gamepad" ? "Gamepad" : "Touch Mouse")
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color.accentColor.opacity(0.12))
                    .clipShape(Capsule())
                Text(record.platform == "ios" ? "iOS" : "Android")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Label("\(record.downloadCount)", systemImage: "arrow.down.circle")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if let desc = record.description {
                Text(desc)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            if let deviceName = record.deviceName {
                Label(deviceName, systemImage: "iphone")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: - Profile picker row

    private var profilePickerRow: some View {
        HStack {
            Text("Import to:")
                .font(.subheadline)
            Button {
                showProfilePicker = true
            } label: {
                Label(targetProfile.name, systemImage: "person.crop.circle")
                    .font(.subheadline.weight(.medium))
            }
            .buttonStyle(.bordered)
            Spacer()
        }
    }

    // MARK: - Quick select

    private var quickSelectSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Quick select")
                .font(.headline)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(ImportPreset.allCases, id: \.self) { preset in
                        Button {
                            resetToPreset(preset)
                        } label: {
                            Text(preset.label)
                                .font(.caption.weight(.medium))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(selectedPreset == preset ? Color.accentColor : Color.secondary.opacity(0.15))
                                .foregroundStyle(selectedPreset == preset ? Color.white : Color.primary)
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    // MARK: - Subset toggles

    private var subsetsSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("What to import")
                .font(.headline)
            if record.mode == "gamepad" {
                gamepadToggleList
            } else {
                touchMouseToggleList
            }
        }
    }

    private var gamepadToggleList: some View {
        VStack(spacing: 0) {
            SubsetToggleRow(label: "Control positions & sizes", isOn: Binding(
                get: { gamepadSubsets.contains(.controlLayout) },
                set: { toggleGamepad(.controlLayout, on: $0) }
            ))
            SubsetToggleRow(label: "Button behavior & turbo", isOn: Binding(
                get: { gamepadSubsets.contains(.buttonBehavior) },
                set: { toggleGamepad(.buttonBehavior, on: $0) }
            ))
            SubsetToggleRow(label: "Joystick settings", isOn: Binding(
                get: { gamepadSubsets.contains(.joystickSettings) },
                set: { toggleGamepad(.joystickSettings, on: $0) }
            ))
            SubsetToggleRow(label: "Keyboard macros", isOn: Binding(
                get: { gamepadSubsets.contains(.macros) },
                set: { toggleGamepad(.macros, on: $0) }
            ))
            SubsetToggleRow(label: "Button labels", isOn: Binding(
                get: { gamepadSubsets.contains(.labels) },
                set: { toggleGamepad(.labels, on: $0) }
            ))
            SubsetToggleRow(label: "Vibration", isOn: Binding(
                get: { gamepadSubsets.contains(.vibration) },
                set: { toggleGamepad(.vibration, on: $0) }
            ))
        }
    }

    private var touchMouseToggleList: some View {
        VStack(spacing: 0) {
            SubsetToggleRow(label: "Zone positions & sizes", isOn: Binding(
                get: { touchSubsets.contains(.zonePositions) },
                set: { toggleTouch(.zonePositions, on: $0) }
            ))
            SubsetToggleRow(label: "Sensitivity & scroll", isOn: Binding(
                get: { touchSubsets.contains(.sensitivity) },
                set: { toggleTouch(.sensitivity, on: $0) }
            ))
            SubsetToggleRow(label: "Button behavior", isOn: Binding(
                get: { touchSubsets.contains(.buttonBehavior) },
                set: { toggleTouch(.buttonBehavior, on: $0) }
            ))
            SubsetToggleRow(label: "Keyboard macros", isOn: Binding(
                get: { touchSubsets.contains(.macros) },
                set: { toggleTouch(.macros, on: $0) }
            ))
        }
    }

    // MARK: - Apply button

    private var applyButton: some View {
        Button {
            Task { await applyImport() }
        } label: {
            Group {
                if isApplying {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .frame(maxWidth: .infinity)
                } else {
                    Text("Apply to \(targetProfile.name)")
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .buttonStyle(.borderedProminent)
        .disabled(isApplying || nothingSelected)
        .controlSize(.large)
    }

    // MARK: - Profile picker sheet

    private var profilePickerSheet: some View {
        NavigationStack {
            List {
                ForEach(appState.allProfiles, id: \.key) { (profile: Profile) in
                    Button {
                        targetProfile = profile
                        showProfilePicker = false
                    } label: {
                        HStack {
                            Text(profile.name)
                            Spacer()
                            if profile.key == targetProfile.key {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(Color.accentColor)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .navigationTitle("Import To Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showProfilePicker = false }
                }
            }
        }
    }

    // MARK: - Logic helpers

    private var nothingSelected: Bool {
        if record.mode == "gamepad" { return gamepadSubsets.isEmpty }
        return touchSubsets.isEmpty
    }

    private func toggleGamepad(_ subset: GamepadSubset, on: Bool) {
        if on { gamepadSubsets.insert(subset) } else { gamepadSubsets.remove(subset) }
        reconcileGamepadPreset()
    }

    private func toggleTouch(_ subset: TouchMouseSubset, on: Bool) {
        if on { touchSubsets.insert(subset) } else { touchSubsets.remove(subset) }
        reconcileTouchPreset()
    }

    private func resetToPreset(_ preset: ImportPreset) {
        selectedPreset = preset
        if record.mode == "gamepad" {
            gamepadSubsets = preset.gamepadSubsets
        } else {
            touchSubsets = preset.touchMouseSubsets
        }
    }

    private func reconcileGamepadPreset() {
        for preset in ImportPreset.allCases where preset != .custom {
            if preset.gamepadSubsets == gamepadSubsets { selectedPreset = preset; return }
        }
        selectedPreset = .custom
    }

    private func reconcileTouchPreset() {
        for preset in ImportPreset.allCases where preset != .custom {
            if preset.touchMouseSubsets == touchSubsets { selectedPreset = preset; return }
        }
        selectedPreset = .custom
    }

    // MARK: - Rescaling helpers

    private func rescaleIfNeeded(_ config: GamepadConfig, from rec: CommunityConfigRecord) -> GamepadConfig {
        #if canImport(UIKit)
        guard let srcWpx = rec.deviceScreenWidthPx,
              let srcHpx = rec.deviceScreenHeightPx,
              let srcDpi = rec.deviceScreenDensityDpi
        else { return config }
        let isLandscape = config.orientationPreference != .portrait
        let (srcLong, srcShort) = LayoutRescaler.canvasDimsFromScreenPx(widthPx: srcWpx, heightPx: srcHpx, densityDpi: srcDpi)
        let (tgtLong, tgtShort) = LayoutRescaler.canvasDimsFromScreen()
        let srcW = isLandscape ? srcLong : srcShort
        let srcH = isLandscape ? srcShort : srcLong
        let tgtW = isLandscape ? tgtLong : tgtShort
        let tgtH = isLandscape ? tgtShort : tgtLong
        return LayoutRescaler.rescaleGamepad(config: config, srcW: srcW, srcH: srcH, tgtW: tgtW, tgtH: tgtH)
        #else
        return config
        #endif
    }

    // MARK: - Apply import

    private func applyImport() async {
        isApplying = true
        applyError = nil

        let importRecord = (try? await viewModel.fetchConfigForImport(id: record.id)) ?? record
        guard let jsonData = importRecord.configJson.data(using: .utf8),
              let jsonDict = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any] else {
            applyError = "Config JSON is invalid."
            isApplying = false
            return
        }

        if importRecord.mode == "gamepad" {
            let parsed = GamepadConfigSerializer.fromCanonicalJson(jsonDict)
            let source = rescaleIfNeeded(parsed, from: importRecord)
            let target = appState.gamepadConfig(for: targetProfile)
            let merged = ConfigMerger.mergeGamepad(target: target, source: source, subsets: gamepadSubsets)
            appState.updateGamepadConfig(merged, profile: targetProfile)
        } else {
            let source = TouchMouseConfigSerializer.fromCanonicalJson(jsonDict)
            let target = appState.touchMouseConfig(for: targetProfile)
            let merged = ConfigMerger.mergeTouchMouse(target: target, source: source, subsets: touchSubsets)
            appState.updateTouchMouseConfig(merged, profile: targetProfile)
        }

        isApplying = false
        dismiss()
    }
}

// MARK: - SubsetToggleRow

private struct SubsetToggleRow: View {
    let label: String
    @Binding var isOn: Bool

    var body: some View {
        Toggle(label, isOn: $isOn)
            .font(.subheadline)
            .padding(.vertical, 8)
        Divider()
    }
}

// MARK: - ImportPreset

enum ImportPreset: CaseIterable {
    case everything
    case layout
    case macros
    case behaviors
    case custom

    var label: String {
        switch self {
        case .everything: return "Everything"
        case .layout:     return "Layout"
        case .macros:     return "Macros"
        case .behaviors:  return "Behaviors"
        case .custom:     return "Custom"
        }
    }

    var gamepadSubsets: Set<GamepadSubset> {
        switch self {
        case .everything: return Set(GamepadSubset.allCases)
        case .layout:     return [.controlLayout]
        case .macros:     return [.macros]
        case .behaviors:  return [.buttonBehavior, .joystickSettings]
        case .custom:     return []
        }
    }

    var touchMouseSubsets: Set<TouchMouseSubset> {
        switch self {
        case .everything: return Set(TouchMouseSubset.allCases)
        case .layout:     return [.zonePositions]
        case .macros:     return [.macros]
        case .behaviors:  return [.buttonBehavior, .sensitivity]
        case .custom:     return []
        }
    }
}
