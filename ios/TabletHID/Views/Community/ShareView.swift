import SwiftUI

/// Share tab: shows Gamepad and Touch Mouse config summary cards with Share buttons.
struct ShareView: View {
    @EnvironmentObject private var appState: AppState
    let viewModel: CommunityViewModel

    @State private var showProfilePicker = false
    @State private var uploadMode: UploadMode? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                profileChip
                gamepadCard
                touchMouseCard
            }
            .padding(16)
        }
        .sheet(isPresented: $showProfilePicker) {
            profilePickerSheet
        }
        .sheet(item: $uploadMode) { uploadMode in
            UploadSheet(mode: uploadMode.rawValue, viewModel: viewModel)
        }
    }

    // MARK: - Profile chip

    private var profileChip: some View {
        Button {
            showProfilePicker = true
        } label: {
            Label(appState.activeProfile.name, systemImage: "person.crop.circle")
                .font(.subheadline.weight(.medium))
        }
        .buttonStyle(.bordered)
    }

    // MARK: - Gamepad card

    private var gamepadCard: some View {
        let config = appState.gamepadConfig
        let enabledCount = [
            config.btnA, config.btnB, config.btnX, config.btnY,
            config.btnLB, config.btnRB, config.btnLT, config.btnRT,
            config.btnBack, config.btnStart,
            config.dpadUp, config.dpadDown, config.dpadLeft, config.dpadRight,
        ].filter { $0.enabled }.count
        let macroCount = config.macroButtons.count
        let joystickMode = config.singleJoystickMode ? "Single \(config.singleJoystickOutputSide.label)" : "Dual"

        return configCard(
            title: "Gamepad",
            symbol: "gamecontroller",
            rows: [
                "Buttons: \(enabledCount) enabled",
                "Macros: \(macroCount) (\(config.macroHostDefaults.label) defaults)",
                "Joystick: \(joystickMode)",
                "Vibration: \(config.vibrationIntensity.label)",
            ],
            uploadModeValue: .gamepad
        )
    }

    // MARK: - Touch Mouse card

    private var touchMouseCard: some View {
        let config = appState.touchMouseConfig
        let modeLabel = config.mode == .mouse ? "Mouse" : "Touch"
        let leftLabel = config.leftButton.enabled ? zoneTypeLabel(config.leftButton.zoneType) : "Off"
        let rightLabel = config.rightButton.enabled ? zoneTypeLabel(config.rightButton.zoneType) : "Off"
        let macroCount = config.macroButtons.count

        return configCard(
            title: "Touch Mouse",
            symbol: "cursorarrow.click.2",
            rows: [
                "Mode: \(modeLabel) · Sensitivity: \(config.sensitivity)",
                "Left: \(leftLabel) · Right: \(rightLabel)",
                "Macros: \(macroCount)",
                "Scroll: \(config.scrollEnabled ? "on" : "off")",
            ],
            uploadModeValue: .touchMouse
        )
    }

    private func zoneTypeLabel(_ zoneType: ZoneType) -> String {
        switch zoneType {
        case .staticZone: return "Static"
        case .dynamic:    return "Dynamic"
        }
    }

    // MARK: - Generic card builder

    private func configCard(
        title: String,
        symbol: String,
        rows: [String],
        uploadModeValue: UploadMode
    ) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: symbol)
                    .font(.title3)
                    .foregroundStyle(.blue)
                Text(title)
                    .font(.headline)
                Spacer()
            }
            Divider()
            ForEach(rows, id: \.self) { row in
                Text(row)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            HStack {
                Spacer()
                Button {
                    uploadMode = uploadModeValue
                } label: {
                    Label("Share", systemImage: "square.and.arrow.up")
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.small)
            }
        }
        .padding(16)
        .background(cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var cardBackground: Color {
        #if canImport(UIKit)
        Color(uiColor: .secondarySystemGroupedBackground)
        #else
        Color(nsColor: .controlBackgroundColor)
        #endif
    }

    // MARK: - Profile picker sheet

    private var profilePickerSheet: some View {
        NavigationStack {
            List(appState.allProfiles) { profile in
                Button {
                    appState.setProfile(profile)
                    showProfilePicker = false
                } label: {
                    HStack {
                        Text(profile.name)
                        Spacer()
                        if profile.key == appState.activeProfile.key {
                            Image(systemName: "checkmark")
                                .foregroundStyle(.accentColor)
                        }
                    }
                }
                .buttonStyle(.plain)
            }
            .navigationTitle("Share From Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showProfilePicker = false }
                }
            }
        }
    }
}

// MARK: - UploadMode

enum UploadMode: String, Identifiable {
    case gamepad     = "gamepad"
    case touchMouse  = "touch_mouse"

    var id: String { rawValue }
}
