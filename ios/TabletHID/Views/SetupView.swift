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
            HStack {
                Button {
                    appState.initialize(mode: mode)
                } label: {
                    Label("Prepare Transport", systemImage: "antenna.radiowaves.left.and.right")
                }
                .buttonStyle(.borderedProminent)

                Button {
                    appState.developmentConnect(mode: mode)
                } label: {
                    Label("Use Development Mode", systemImage: "hammer")
                }
                .buttonStyle(.bordered)
            }

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
            Text("If pairing is unreliable on a host, use Development Mode to enter the controls and validate UI, persistence, and HID report bytes while we tune the BLE transport.")
                .foregroundStyle(.secondary)
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
