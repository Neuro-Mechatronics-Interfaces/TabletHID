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
                singleJoystickSection
                joystickSection(title: "Left Joystick", config: $draft.leftJoystick)
                if !draft.singleJoystickMode {
                    joystickSection(title: "Right Joystick", config: $draft.rightJoystick)
                }
                feedbackSection
                MacroConfigSection(
                    hostDefaults: $draft.macroHostDefaults,
                    macros: $draft.macroButtons
                )
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
            TextField("Label", text: Binding(
                get: { labelValue(for: currentButtonKey, fallback: currentButtonDefaultLabel) },
                set: { setLabel($0, for: currentButtonKey, fallback: currentButtonDefaultLabel) }
            ))
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

    private var singleJoystickSection: some View {
        Section("Single Joystick") {
            Toggle("Use one visible joystick", isOn: $draft.singleJoystickMode)
            if draft.singleJoystickMode {
                Toggle("Show L/R output toggle", isOn: $draft.singleJoystickSideToggleEnabled)
                Picker("Output side", selection: $draft.singleJoystickOutputSide) {
                    ForEach(JoystickSide.allCases) { side in
                        Text(side.label).tag(side)
                    }
                }
            }
        }
    }

    private var feedbackSection: some View {
        Section("Feedback") {
            Picker("Haptics", selection: $draft.vibrationIntensity) {
                ForEach(VibrationIntensity.allCases) { intensity in
                    Text(intensity.label).tag(intensity)
                }
            }
        }
    }

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

    private var currentButtonKey: String {
        ["a", "b", "x", "y", "lb", "rb", "lt", "rt", "back", "start", "dup", "ddown", "dleft", "dright"][selectedButton]
    }

    private var currentButtonDefaultLabel: String {
        ["A", "B", "X", "Y", "LB", "RB", "LT", "RT", "Back", "Start", "Up", "Down", "Left", "Right"][selectedButton]
    }

    private func labelValue(for key: String, fallback: String) -> String {
        draft.customButtonLabels[key] ?? fallback
    }

    private func setLabel(_ value: String, for key: String, fallback: String) {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed == fallback {
            draft.customButtonLabels.removeValue(forKey: key)
        } else {
            draft.customButtonLabels[key] = String(trimmed.prefix(16))
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

// MARK: - Shared macro editor

struct MacroConfigSection: View {
    @Binding var hostDefaults: MacroHostDefaults
    @Binding var macros: [KeyboardMacroButtonConfig]
    @State private var showingCustomEditor = false

    var body: some View {
        Section("Keyboard Macros") {
            Picker("Preset target", selection: $hostDefaults) {
                ForEach(MacroHostDefaults.allCases) { host in
                    Text(host.label).tag(host)
                }
            }
            Button {
                addDefaults()
            } label: {
                Label("Add \(hostDefaults.label) Defaults", systemImage: "keyboard")
            }
            Button {
                showingCustomEditor = true
            } label: {
                Label("Add Custom Macro", systemImage: "plus")
            }
            if !macros.isEmpty {
                ForEach(macros) { macro in
                    HStack {
                        Text(macro.label)
                        Spacer()
                        Button(role: .destructive) {
                            macros.removeAll { $0.id == macro.id }
                        } label: {
                            Image(systemName: "trash")
                        }
                    }
                }
                Button("Clear Macros", role: .destructive) {
                    macros.removeAll()
                }
            }
        }
        .sheet(isPresented: $showingCustomEditor) {
            CustomMacroEditorView { macro in
                macros.append(macro)
                showingCustomEditor = false
            }
        }
    }

    private func addDefaults() {
        let existing = Set(macros.map { "\($0.modifiers):\($0.keyUsages.map(String.init).joined(separator: ","))" })
        let additions = KeyboardMacroPresets.defaults(for: hostDefaults).filter {
            !existing.contains("\($0.modifiers):\($0.keyUsages.map(String.init).joined(separator: ","))")
        }
        macros.append(contentsOf: additions)
    }
}

private struct CustomMacroEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var label = ""
    @State private var leftControl = false
    @State private var leftShift = false
    @State private var leftAlt = false
    @State private var leftGUI = false
    @State private var selectedKey = KeyOption.common[0]
    let onCreate: (KeyboardMacroButtonConfig) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("Label") {
                    TextField("Macro label", text: $label)
                }
                Section("Modifiers") {
                    Toggle("Control", isOn: $leftControl)
                    Toggle("Shift", isOn: $leftShift)
                    Toggle("Alt / Option", isOn: $leftAlt)
                    Toggle("Win / Command", isOn: $leftGUI)
                }
                Section("Key") {
                    Picker("Key", selection: $selectedKey) {
                        ForEach(KeyOption.common) { key in
                            Text(key.label).tag(key)
                        }
                    }
                }
            }
            .navigationTitle("Custom Macro")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        onCreate(KeyboardMacroButtonConfig(
                            label: finalLabel,
                            modifiers: modifiers,
                            keyUsages: [selectedKey.usage]
                        ))
                    }
                    .disabled(finalLabel.isEmpty)
                }
            }
        }
    }

    private var modifiers: Int {
        (leftControl ? 0x01 : 0) |
            (leftShift ? 0x02 : 0) |
            (leftAlt ? 0x04 : 0) |
            (leftGUI ? 0x08 : 0)
    }

    private var finalLabel: String {
        let trimmed = label.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { return String(trimmed.prefix(24)) }
        let parts = [
            leftControl ? "Ctrl" : nil,
            leftShift ? "Shift" : nil,
            leftAlt ? "Alt" : nil,
            leftGUI ? "Cmd" : nil,
            selectedKey.label
        ].compactMap { $0 }
        return parts.joined(separator: "+")
    }
}

private struct KeyOption: Identifiable, Hashable {
    let label: String
    let usage: Int
    var id: Int { usage }

    static let common: [KeyOption] = [
        KeyOption(label: "A", usage: 0x04), KeyOption(label: "B", usage: 0x05),
        KeyOption(label: "C", usage: 0x06), KeyOption(label: "D", usage: 0x07),
        KeyOption(label: "E", usage: 0x08), KeyOption(label: "F", usage: 0x09),
        KeyOption(label: "G", usage: 0x0A), KeyOption(label: "H", usage: 0x0B),
        KeyOption(label: "I", usage: 0x0C), KeyOption(label: "J", usage: 0x0D),
        KeyOption(label: "K", usage: 0x0E), KeyOption(label: "L", usage: 0x0F),
        KeyOption(label: "M", usage: 0x10), KeyOption(label: "N", usage: 0x11),
        KeyOption(label: "O", usage: 0x12), KeyOption(label: "P", usage: 0x13),
        KeyOption(label: "Q", usage: 0x14), KeyOption(label: "R", usage: 0x15),
        KeyOption(label: "S", usage: 0x16), KeyOption(label: "T", usage: 0x17),
        KeyOption(label: "U", usage: 0x18), KeyOption(label: "V", usage: 0x19),
        KeyOption(label: "W", usage: 0x1A), KeyOption(label: "X", usage: 0x1B),
        KeyOption(label: "Y", usage: 0x1C), KeyOption(label: "Z", usage: 0x1D),
        KeyOption(label: "Tab", usage: 0x2B), KeyOption(label: "Space", usage: 0x2C),
        KeyOption(label: "Enter", usage: 0x28), KeyOption(label: "Esc", usage: 0x29),
        KeyOption(label: "F4", usage: 0x3D), KeyOption(label: "Delete", usage: 0x4C),
        KeyOption(label: "Left", usage: 0x50), KeyOption(label: "Right", usage: 0x4F),
        KeyOption(label: "Up", usage: 0x52), KeyOption(label: "Down", usage: 0x51)
    ]
}
