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
    let displayName: String
    let lastMode: DeviceMode
    let lastSeen: Date

    var id: String { identifier }

    static func fromCentralIdentifier(_ identifier: String, mode: DeviceMode) -> HIDHost {
        HIDHost(
            identifier: identifier,
            displayName: "Host \(identifier.prefix(8))",
            lastMode: mode,
            lastSeen: Date()
        )
    }
}

enum HIDConnectionState: Equatable {
    case idle
    case registering(DeviceMode)
    case reconnecting(mode: DeviceMode, hostName: String)
    case waitingForConnection(DeviceMode)
    case connected(mode: DeviceMode, host: HIDHost)
    case unavailable(String)
    case error(String)

    var label: String {
        switch self {
        case .idle: "Idle"
        case .registering: "Preparing HID profile"
        case .reconnecting(_, let hostName): "Reconnecting to \(hostName)"
        case .waitingForConnection(let mode): "Waiting for \(mode.deviceName)"
        case .connected(_, let host): "Connected to \(host.displayName)"
        case .unavailable: "Transport unavailable"
        case .error: "Error"
        }
    }

    var isConnected: Bool {
        if case .connected = self { true } else { false }
    }
}
