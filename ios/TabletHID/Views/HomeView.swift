import SwiftUI
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

struct HomeView: View {
    @EnvironmentObject private var appState: AppState
    @State private var newProfileName = ""
    @State private var showingNewProfile = false
    @State private var showingSettings = false
    @State private var showingDeviceNameEditor = false
    @State private var deviceNameDraft = ""
    @State private var showingCommunity = false

    let onSelectMode: (DeviceMode) -> Void
    let onShowSetup: (DeviceMode) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                header
                connectionCard
                profilePicker
                modeGrid
                communityCard
            }
            .padding(24)
            .frame(maxWidth: 900, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .background(groupedBackgroundColor)
        .navigationTitle("TabletHID")
        .toolbar {
            ToolbarItem {
                Button {
                    showingSettings = true
                } label: {
                    Image(systemName: "gearshape")
                }
            }
            ToolbarItem {
                Button {
                    showingNewProfile = true
                } label: {
                    Label("Add Profile", systemImage: "plus")
                }
            }
        }
        .sheet(isPresented: $showingSettings) {
            AppSettingsView()
                #if os(iOS)
                .presentationDetents([.medium, .large])
                #else
                .frame(minWidth: 360, minHeight: 280)
                #endif
        }
        .sheet(isPresented: $showingCommunity) {
            NavigationStack {
                CommunityView()
            }
        }
        .alert("New Profile", isPresented: $showingNewProfile) {
            TextField("Profile name", text: $newProfileName)
            Button("Create") {
                let name = newProfileName.trimmingCharacters(in: .whitespacesAndNewlines)
                if !name.isEmpty { appState.addCustomProfile(named: name) }
                newProfileName = ""
            }
            Button("Cancel", role: .cancel) {}
        }
        .alert("Device Name", isPresented: $showingDeviceNameEditor) {
            TextField("Device name", text: $deviceNameDraft)
                .autocorrectionDisabled()
            Button("Save") {
                appState.setDeviceName(deviceNameDraft)
                deviceNameDraft = appState.deviceName
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("A virtual HID control surface.")
        }
    }

    private var profilePicker: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Profile")
                .font(.headline)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(appState.allProfiles) { profile in
                        Button {
                            appState.setProfile(profile)
                        } label: {
                            Label(profile.name, systemImage: profile.key == appState.activeProfile.key ? "checkmark.circle.fill" : "person.crop.circle")
                                .labelStyle(.titleAndIcon)
                        }
                        .buttonStyle(.bordered)
                        .tint(profile.key == appState.activeProfile.key ? .blue : .gray)
                    }
                }
            }
        }
    }

    private var connectionCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                Circle()
                    .fill(appState.connectionState.isConnected ? Color.green : Color.red)
                    .frame(width: 12, height: 12)

                VStack(alignment: .leading, spacing: 2) {
                    Text(connectionStatusText)
                        .font(.headline)
                    if let detail = connectionDetailText {
                        Text(detail)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                Spacer()

                Button {
                    deviceNameDraft = appState.deviceName
                    showingDeviceNameEditor = true
                } label: {
                    Label(appState.deviceName, systemImage: "pencil")
                        .lineLimit(1)
                }
                .buttonStyle(.bordered)
            }

            pendingConnectionPrompt

            HStack(spacing: 10) {
                if shouldShowIdleActions {
                    Button {
                        appState.startPairing(mode: .touchMouse)
                    } label: {
                        Label("Make Discoverable", systemImage: "antenna.radiowaves.left.and.right")
                    }
                    .buttonStyle(.borderedProminent)

                    if let host = appState.lastHost {
                        Button {
                            appState.reconnect(mode: .touchMouse, host: host)
                        } label: {
                            Label("Reconnect", systemImage: "arrow.triangle.2.circlepath")
                        }
                        .buttonStyle(.bordered)
                    }
                } else if appState.connectionState.isConnected {
                    Button(role: .destructive) {
                        appState.disconnect()
                    } label: {
                        Label("Disconnect", systemImage: "xmark.circle")
                    }
                    .buttonStyle(.bordered)
                } else {
                    Button(role: .destructive) {
                        appState.cancelConnection()
                    } label: {
                        Label("Cancel", systemImage: "xmark.circle")
                    }
                    .buttonStyle(.bordered)
                }

                Spacer()

                Button {
                    onShowSetup(.touchMouse)
                } label: {
                    Label("Setup", systemImage: "questionmark.circle")
                }
                .buttonStyle(.bordered)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(cardBackgroundColor)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    @ViewBuilder
    private var pendingConnectionPrompt: some View {
        if let host = appState.pendingConnectionHost {
            VStack(alignment: .leading, spacing: 10) {
                Label("Incoming host", systemImage: "person.crop.circle.badge.questionmark")
                    .font(.subheadline.weight(.semibold))
                Text("\(host.label) is asking to use TabletHID. Allow only the computer you are pairing right now.")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                HStack {
                    Button {
                        appState.approvePendingConnection()
                    } label: {
                        Label("Allow", systemImage: "checkmark.circle")
                    }
                    .buttonStyle(.borderedProminent)

                    Button(role: .destructive) {
                        appState.rejectPendingConnection()
                    } label: {
                        Label("Ignore", systemImage: "xmark.circle")
                    }
                    .buttonStyle(.bordered)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.accentColor.opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }

    private var shouldShowIdleActions: Bool {
        switch appState.connectionState {
        case .idle, .error, .unavailable:
            true
        case .registering, .waitingForConnection, .reconnecting, .connected:
            false
        }
    }

    private var connectionStatusText: String {
        switch appState.connectionState {
        case .idle:
            "Disconnected"
        case .registering:
            "Starting"
        case .waitingForConnection:
            "Waiting for connection"
        case .reconnecting(_, let hostName):
            "Reconnecting to \(hostName)"
        case .connected(_, let host):
            "Connected to \(host.label)"
        case .unavailable:
            "Transport unavailable"
        case .error:
            "Connection error"
        }
    }

    private var connectionDetailText: String? {
        switch appState.connectionState {
        case .idle:
            if let host = appState.lastHost {
                "Last host: \(host.label)"
            } else {
                "Make the iPad discoverable, then pair from the host Bluetooth settings."
            }
        case .waitingForConnection(_, let deviceName):
            if appState.pendingConnectionHost != nil {
                "Approve the incoming host on this iPad before entering a control surface."
            } else {
                "Pair with \(deviceName) from the host Bluetooth settings."
            }
        case .registering:
            "Publishing the experimental BLE HID service."
        case .reconnecting(_, let hostName):
            "Advertising for \(hostName). If it does not reconnect, open Setup and pair again."
        case .connected:
            "Tap Touch Mouse or Gamepad to enter a control surface."
        case .unavailable(let reason):
            reason
        case .error(let message):
            message
        }
    }

    private var modeGrid: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 260), spacing: 16)], spacing: 16) {
            ForEach(DeviceMode.allCases) { mode in
                Button {
                    onSelectMode(mode)
                } label: {
                    VStack(alignment: .leading, spacing: 16) {
                        Image(systemName: mode.symbolName)
                            .font(.system(size: 42, weight: .semibold))
                            .foregroundStyle(.blue)
                        Text(mode.title)
                            .font(.title2.weight(.bold))
                        Text(mode == .touchMouse ? "Relative mouse movement with click zones." : "Virtual Xbox-style controller layout.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                    .background(cardBackgroundColor)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var communityCard: some View {
        Button {
            showingCommunity = true
        } label: {
            HStack(spacing: 16) {
                Image(systemName: "person.2.circle")
                    .font(.system(size: 34, weight: .semibold))
                    .foregroundStyle(.blue)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Community")
                        .font(.title2.weight(.bold))
                    Text("Browse and share configs with other users.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(20)
            .background(cardBackgroundColor)
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
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
