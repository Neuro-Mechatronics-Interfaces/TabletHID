import SwiftUI

struct TouchMouseSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var draft: TouchMouseConfig
    let onSave: (TouchMouseConfig) -> Void
    let onCalibrateRequested: (() -> Void)?

    init(config: TouchMouseConfig, onSave: @escaping (TouchMouseConfig) -> Void, onCalibrateRequested: (() -> Void)? = nil) {
        _draft = State(initialValue: config)
        self.onSave = onSave
        self.onCalibrateRequested = onCalibrateRequested
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

                if draft.mode == .mouse, let calibrate = onCalibrateRequested {
                    Section {
                        Button {
                            calibrate()
                            dismiss()
                        } label: {
                            Label("Auto-calibrate Dynamic Zones", systemImage: "hand.raised.fingers.spread")
                        }
                        Text("Place three fingers in sequence to set comfortable click positions automatically. Sets both buttons to Dynamic mode.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } header: {
                        Text("Calibration")
                    }
                }

                Section("Three-Finger Scroll") {
                    Toggle("Enable", isOn: $draft.scrollEnabled)
                    if draft.scrollEnabled {
                        Toggle("Invert direction", isOn: $draft.invertScroll)
                    }
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
            if button.zoneType.wrappedValue == .staticZone {
                SliderRow(title: "Left", value: button.staticLeft, range: 0...1, step: 0.01)
                SliderRow(title: "Top", value: button.staticTop, range: 0...1, step: 0.01)
                SliderRow(title: "Right", value: button.staticRight, range: 0...1, step: 0.01)
                SliderRow(title: "Bottom", value: button.staticBottom, range: 0...1, step: 0.01)
            } else {
                SliderRow(title: "Offset X", value: button.dynamicOffsetX, range: -1...1, step: 0.05)
                SliderRow(title: "Offset Y", value: button.dynamicOffsetY, range: -1...1, step: 0.05)
                SliderRow(title: "Radius", value: button.dynamicRadius, range: 0.03...0.2, step: 0.01)
            }
        }
    }
}

struct SliderRow: View {
    let title: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    let step: Double

    var body: some View {
        VStack(alignment: .leading) {
            HStack {
                Text(title)
                Spacer()
                Text(value, format: .number.precision(.fractionLength(2)))
                    .foregroundStyle(.secondary)
            }
            Slider(value: Binding(
                get: { value.clamped(to: range).snapped(to: step) },
                set: { value = $0.clamped(to: range).snapped(to: step) }
            ), in: range, step: step)
        }
    }
}
