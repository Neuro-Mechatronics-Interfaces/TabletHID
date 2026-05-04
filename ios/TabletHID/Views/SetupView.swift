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

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            statusCard
            instructions
            reconnectCard
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
    }

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

    private var instructions: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Implementation Note")
                .font(.headline)
            Text("Android can register a Bluetooth HID Device profile directly. This iOS build uses an experimental Core Bluetooth HID-over-GATT advertisement with the expanded HID service UUID.")
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private var reconnectCard: some View {
        if let lastHost = appState.lastHost {
            VStack(alignment: .leading, spacing: 10) {
                Label("Previously paired host found", systemImage: "link")
                    .font(.headline)
                Text("\(lastHost.displayName) was last seen in \(lastHost.lastMode.title). Reconnect restarts the same HID advertisement so the host can reattach without a fresh pairing flow.")
                    .font(.callout)
                    .foregroundStyle(.secondary)

                HStack {
                    Button {
                        appState.reconnect(mode: mode)
                    } label: {
                        Label("Reconnect to \(lastHost.displayName)", systemImage: "arrow.triangle.2.circlepath")
                    }
                    .buttonStyle(.borderedProminent)

                    Button(role: .destructive) {
                        appState.forgetLastHost()
                    } label: {
                        Label("Forget Host", systemImage: "trash")
                    }
                    .buttonStyle(.bordered)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(cardBackgroundColor)
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }

    private var actionButtons: some View {
        HStack {
            if appState.lastHost == nil {
                Button {
                    appState.initialize(mode: mode)
                } label: {
                    Label("Prepare Transport", systemImage: "antenna.radiowaves.left.and.right")
                }
                .buttonStyle(.borderedProminent)
            } else {
                Button {
                    appState.initialize(mode: mode)
                } label: {
                    Label("Prepare New Pair", systemImage: "antenna.radiowaves.left.and.right")
                }
                .buttonStyle(.bordered)
            }

            Button {
                appState.developmentConnect(mode: mode)
            } label: {
                Label("Use Development Mode", systemImage: "hammer")
            }
            .buttonStyle(.bordered)
        }
    }

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
