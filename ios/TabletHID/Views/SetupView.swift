import SwiftUI
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

struct SetupView: View {
    @EnvironmentObject private var appState: AppState
    let mode: DeviceMode
    let onEnter: () -> Void

    @State private var hostToRename: HIDHost?
    @State private var renameText = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            statusCard
            instructions
            knownHostsCard
            actionButtons

            Button {
                onEnter()
            } label: {
                Label("Enter \(mode.title)", systemImage: "arrow.right.circle.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(!appState.connectionState.isConnected)

            Spacer()
        }
        .padding(24)
        .navigationTitle(mode.title)
        .background(groupedBackgroundColor)
        .sheet(item: $hostToRename) { host in
            renameSheet(host: host)
        }
    }

    // MARK: - Status card

    private var statusCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(appState.connectionState.label)
                .font(.headline)
            if case .unavailable(let reason) = appState.connectionState {
                Text(reason)
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(cardBackgroundColor)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    // MARK: - Instructions note

    private var instructions: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Implementation Note")
                .font(.headline)
            Text("Android can register a Bluetooth HID Device profile directly. This iOS build uses an experimental Core Bluetooth HID-over-GATT advertisement with the expanded HID service UUID.")
                .foregroundStyle(.secondary)
        }
    }

    // MARK: - Known hosts card

    @ViewBuilder
    private var knownHostsCard: some View {
        if !appState.knownHosts.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                Label("Known hosts", systemImage: "link")
                    .font(.headline)

                ForEach(appState.knownHosts) { host in
                    hostRow(host)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(cardBackgroundColor)
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }

    private func hostRow(_ host: HIDHost) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(host.label)
                    .font(.body.weight(.medium))
                if host.alias != nil {
                    Text(host.displayName)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Text("Last seen in \(host.lastMode.title)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            // Rename
            Button {
                renameText = host.alias ?? ""
                hostToRename = host
            } label: {
                Image(systemName: "pencil")
                    .imageScale(.medium)
            }
            .buttonStyle(.plain)
            .foregroundStyle(.secondary)

            // Reconnect
            Button {
                appState.reconnect(mode: mode, host: host)
            } label: {
                Image(systemName: "arrow.triangle.2.circlepath")
            }
            .buttonStyle(.bordered)
            .tint(.blue)

            // Forget
            Button(role: .destructive) {
                appState.forgetHost(host)
            } label: {
                Image(systemName: "trash")
            }
            .buttonStyle(.bordered)
        }
        .padding(.vertical, 4)
    }

    // MARK: - Rename sheet

    private func renameSheet(host: HIDHost) -> some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Label", text: $renameText)
                        .autocorrectionDisabled()
                } header: {
                    Text("Custom label for \(host.displayName)")
                } footer: {
                    Text("Leave blank to use the device's Bluetooth name.")
                }
            }
            .navigationTitle("Rename Host")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { hostToRename = nil }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        appState.renameHost(host, alias: renameText)
                        hostToRename = nil
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }

    // MARK: - Action buttons

    private var actionButtons: some View {
        HStack {
            Button {
                appState.initialize(mode: mode)
            } label: {
                Label(
                    appState.knownHosts.isEmpty ? "Prepare Transport" : "Prepare New Pair",
                    systemImage: "antenna.radiowaves.left.and.right"
                )
            }
            .buttonStyle(appState.knownHosts.isEmpty ? .borderedProminent : .bordered)

            Button {
                appState.developmentConnect(mode: mode)
            } label: {
                Label("Use Development Mode", systemImage: "hammer")
            }
            .buttonStyle(.bordered)
        }
    }

    // MARK: - Colors

    private var groupedBackgroundColor: Color {
        #if canImport(UIKit)
        Color(uiColor: .systemGroupedBackground)
        #elseif canImport(AppKit)
        Color(nsColor: .windowBackgroundColor)
        #else
        Color.white
        #endif
    }

    private var cardBackgroundColor: Color {
        #if canImport(UIKit)
        Color(uiColor: .secondarySystemGroupedBackground)
        #elseif canImport(AppKit)
        Color(nsColor: .controlBackgroundColor)
        #else
        Color.white
        #endif
    }
}
