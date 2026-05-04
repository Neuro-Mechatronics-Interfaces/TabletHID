import SwiftUI

struct TouchMouseSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var draft: TouchMouseConfig
    let onSave: (TouchMouseConfig) -> Void

    init(config: TouchMouseConfig, onSave: @escaping (TouchMouseConfig) -> Void) {
        _draft = State(initialValue: config)
        self.onSave = onSave
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Mode") {
                    Picker("Mode", selection: $draft.mode) {
                        Text("Touch").tag(TouchMode.touch)
                        Text("Mouse").tag(TouchMode.mouse)
                    }
                    .pickerStyle(.segmented)
                    Stepper("Sensitivity \(draft.sensitivity)", value: $draft.sensitivity, in: 1...10)
                }

                Section("Left Button") {
                    buttonEditor(button: $draft.leftButton)
                }

                Section("Right Button") {
                    buttonEditor(button: $draft.rightButton)
                }
            }
            .navigationTitle("Touch Mouse")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        onSave(draft)
                        dismiss()
                    }
                }
            }
        }
    }

    private func buttonEditor(button: Binding<ButtonZoneConfig>) -> some View {
        Group {
            Toggle("Enabled", isOn: button.enabled)
            Picker("Zone", selection: button.zoneType) {
                Text("Static").tag(ZoneType.staticZone)
                Text("Dynamic").tag(ZoneType.dynamic)
            }
            Picker("Behavior", selection: button.behavior) {
                Text("Momentary").tag(ClickBehavior.momentary)
                Text("Latching").tag(ClickBehavior.latching)
            }
            SliderRow(title: "Offset X", value: button.dynamicOffsetX, range: -1...1)
            SliderRow(title: "Offset Y", value: button.dynamicOffsetY, range: -1...1)
            SliderRow(title: "Radius", value: button.dynamicRadius, range: 0.03...0.2)
        }
    }
}

struct SliderRow: View {
    let title: String
    @Binding var value: Double
    let range: ClosedRange<Double>

    var body: some View {
        VStack(alignment: .leading) {
            HStack {
                Text(title)
                Spacer()
                Text(value, format: .number.precision(.fractionLength(2)))
                    .foregroundStyle(.secondary)
            }
            Slider(value: $value, in: range)
        }
    }
}
