import SwiftUI

struct AppSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var appState: AppState
    @State private var deviceNameDraft = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Peripheral Name", text: $deviceNameDraft)
                        .autocorrectionDisabled()
                        .onSubmit(saveDeviceName)
                    Text("Hosts will see this name when pairing. Changes apply the next time you prepare a Bluetooth connection.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } header: {
                    Text("Bluetooth")
                }

                Section("Appearance") {
                    Picker("Appearance", selection: Binding(
                        get: { appState.appearanceMode },
                        set: { appState.setAppearanceMode($0) }
                    )) {
                        ForEach(AppearanceMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)

                    Toggle("Large Text", isOn: Binding(
                        get: { appState.largeTextEnabled },
                        set: { appState.setLargeTextEnabled($0) }
                    ))

                    Toggle("High Contrast", isOn: Binding(
                        get: { appState.highContrastEnabled },
                        set: { appState.setHighContrastEnabled($0) }
                    ))
                }

                Section {
                    Picker("Orientation", selection: Binding(
                        get: { appState.orientationLock },
                        set: { appState.setOrientationLock($0) }
                    )) {
                        ForEach(OrientationLock.allCases) { lock in
                            Text(lock.label).tag(lock)
                        }
                    }
                    .pickerStyle(.segmented)
                    Text("Locks screen rotation for Touch Mouse and Gamepad canvas views.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } header: {
                    Text("Orientation Lock")
                }

                Section {
                    Toggle("Auto-reconnect on launch", isOn: Binding(
                        get: { appState.autoReconnectEnabled },
                        set: { appState.setAutoReconnectEnabled($0) }
                    ))
                    Text("When enabled, TabletHID restarts the experimental BLE HID reconnect flow for the most recent host when the app opens.")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    Toggle("Enable local session logging", isOn: Binding(
                        get: { appState.loggingEnabled },
                        set: { appState.setLoggingEnabled($0) }
                    ))
                    Text("On each connection a .config snapshot and a timestamped .log of all HID events are written to: \(SessionLogger.sessionDirDisplayPath)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } header: {
                    Text("Session Logging")
                }
            }
            .navigationTitle("Settings")
            .onAppear {
                deviceNameDraft = appState.deviceName
            }
            .onDisappear(perform: saveDeviceName)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        saveDeviceName()
                        dismiss()
                    }
                }
            }
        }
    }

    private func saveDeviceName() {
        appState.setDeviceName(deviceNameDraft)
        deviceNameDraft = appState.deviceName
    }
}
