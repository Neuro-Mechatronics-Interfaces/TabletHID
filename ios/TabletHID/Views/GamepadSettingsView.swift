import SwiftUI

struct GamepadSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var draft: GamepadConfig
    @State private var selectedButton = 0
    let onSave: (GamepadConfig) -> Void

    private static let buttonLabels = [
        "A", "B", "X", "Y",
        "LB", "RB", "LT", "RT",
        "Back", "Start",
        "D-Pad ↑", "D-Pad ↓", "D-Pad ←", "D-Pad →"
    ]

    init(config: GamepadConfig, onSave: @escaping (GamepadConfig) -> Void) {
        _draft = State(initialValue: config)
        self.onSave = onSave
    }

    var body: some View {
        NavigationStack {
            Form {
                buttonSection
                joystickSection(title: "Left Joystick", config: $draft.leftJoystick)
                joystickSection(title: "Right Joystick", config: $draft.rightJoystick)
                Section {
                    Button("Reset Layout", role: .destructive) {
                        draft = draft.withResetLayout()
                    }
                }
            }
            .navigationTitle("Gamepad Settings")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { onSave(draft); dismiss() }
                }
            }
        }
    }

    // MARK: - Button section

    private var buttonSection: some View {
        Section("Buttons") {
            Picker("Button", selection: $selectedButton) {
                ForEach(Array(Self.buttonLabels.enumerated()), id: \.offset) { i, name in
                    Text(name).tag(i)
                }
            }

            let btn = currentButton
            Toggle("Enabled", isOn: btn.enabled)
            Picker("Behavior", selection: btn.behavior) {
                Text("Momentary").tag(ClickBehavior.momentary)
                Text("Latching").tag(ClickBehavior.latching)
            }
            Toggle("Turbo", isOn: btn.turbo)

            if btn.wrappedValue.turbo {
                GPSliderRow(
                    title: "Press duration",
                    value: Binding(
                        get: { Double(btn.wrappedValue.turboDurationMs) },
                        set: { btn.wrappedValue.turboDurationMs = Int($0) }
                    ),
                    range: 10...500, unit: "ms", decimals: 0
                )
                GPSliderRow(
                    title: "Repeat interval",
                    value: Binding(
                        get: { Double(btn.wrappedValue.turboIntervalMs) },
                        set: { btn.wrappedValue.turboIntervalMs = Int($0) }
                    ),
                    range: 50...1000, unit: "ms", decimals: 0
                )
            }

            if selectedButton == 6 || selectedButton == 7 {
                GPSliderRow(
                    title: "Trigger travel",
                    value: btn.triggerTravel,
                    range: 30...300, unit: "pt", decimals: 0
                )
                Picker("Trigger axis", selection: btn.triggerAxis) {
                    ForEach(TriggerDragAxis.allCases) { axis in
                        Text(axis.rawValue.capitalized).tag(axis)
                    }
                }
            }
        }
    }

    // MARK: - Joystick section

    private func joystickSection(title: String, config: Binding<JoystickConfig>) -> some View {
        Section(title) {
            Toggle("Enabled", isOn: config.enabled)
            if config.wrappedValue.enabled {
                GPSliderRow(
                    title: "Deadzone",
                    value: config.deadzone,
                    range: 0...0.30, unit: "%", decimals: 0, displayMultiplier: 100
                )
                GPSliderRow(
                    title: "Gain",
                    value: config.gain,
                    range: 0.5...3.0, unit: "×", decimals: 1
                )
            }
        }
    }

    // MARK: - Button binding

    private var currentButton: Binding<ButtonConfig> {
        switch selectedButton {
        case 0:  return $draft.btnA
        case 1:  return $draft.btnB
        case 2:  return $draft.btnX
        case 3:  return $draft.btnY
        case 4:  return $draft.btnLB
        case 5:  return $draft.btnRB
        case 6:  return $draft.btnLT
        case 7:  return $draft.btnRT
        case 8:  return $draft.btnBack
        case 9:  return $draft.btnStart
        case 10: return $draft.dpadUp
        case 11: return $draft.dpadDown
        case 12: return $draft.dpadLeft
        case 13: return $draft.dpadRight
        default: return $draft.btnA
        }
    }
}

// MARK: - Slider row

private struct GPSliderRow: View {
    let title: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    var unit: String = ""
    var decimals: Int = 2
    var displayMultiplier: Double = 1

    var body: some View {
        VStack(alignment: .leading) {
            HStack {
                Text(title)
                Spacer()
                Text(formatted).foregroundStyle(.secondary)
            }
            Slider(value: $value, in: range)
        }
    }

    private var formatted: String {
        let v = value * displayMultiplier
        if decimals == 0 { return "\(Int(v.rounded()))\(unit.isEmpty ? "" : " \(unit)")" }
        return String(format: "%.\(decimals)f\(unit.isEmpty ? "" : " \(unit)")", v)
    }
}
