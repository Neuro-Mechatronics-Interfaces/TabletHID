import SwiftUI

struct AppSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var appState: AppState

    var body: some View {
        NavigationStack {
            Form {
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
                }

                Section {
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
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
