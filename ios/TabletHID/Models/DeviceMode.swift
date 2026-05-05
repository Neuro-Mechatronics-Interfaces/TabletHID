import Foundation

enum DeviceMode: String, CaseIterable, Identifiable, Codable {
    case touchMouse
    case gamepad

    var id: String { rawValue }

    var title: String {
        switch self {
        case .touchMouse: "Touch Mouse"
        case .gamepad: "Gamepad"
        }
    }

    // Single fixed name so both modes share one BLE bond — host never needs to re-pair.
    var deviceName: String { "TabletHID" }

    var symbolName: String {
        switch self {
        case .touchMouse: "magicmouse"
        case .gamepad: "gamecontroller"
        }
    }
}

struct HIDHost: Codable, Equatable, Identifiable {
    let identifier: String
    let displayName: String   // BLE/BT name captured at connection time
    var alias: String?        // User-assigned label (overrides displayName in UI)
    let lastMode: DeviceMode
    let lastSeen: Date

    var id: String { identifier }

    /// What to show in the UI: alias if set, otherwise displayName, otherwise a short identifier.
    var label: String {
        let a = alias?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !a.isEmpty { return a }
        return displayName.isEmpty ? String(identifier.prefix(8)) : displayName
    }

    static func fromCentralIdentifier(_ identifier: String, mode: DeviceMode) -> HIDHost {
        HIDHost(
            identifier: identifier,
            displayName: "Host \(identifier.prefix(8))",
            alias: nil,
            lastMode: mode,
            lastSeen: Date()
        )
    }
}

enum HIDConnectionState: Equatable {
    case idle
    case registering(DeviceMode)
    case reconnecting(mode: DeviceMode, hostName: String)
    case waitingForConnection(mode: DeviceMode, deviceName: String)
    case connected(mode: DeviceMode, host: HIDHost)
    case unavailable(String)
    case error(String)

    var label: String {
        switch self {
        case .idle: "Idle"
        case .registering: "Preparing HID profile"
        case .reconnecting(_, let hostName): "Reconnecting to \(hostName)"
        case .waitingForConnection(_, let deviceName): "Waiting for \(deviceName)"
        case .connected(_, let host): "Connected to \(host.displayName)"
        case .unavailable: "Transport unavailable"
        case .error: "Error"
        }
    }

    var isConnected: Bool {
        if case .connected = self { true } else { false }
    }
}
