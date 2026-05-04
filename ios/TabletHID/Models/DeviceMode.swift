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

    var deviceName: String {
        switch self {
        case .touchMouse: "TabletHID Mouse"
        case .gamepad: "TabletHID Gamepad"
        }
    }

    var symbolName: String {
        switch self {
        case .touchMouse: "magicmouse"
        case .gamepad: "gamecontroller"
        }
    }
}

enum HIDConnectionState: Equatable {
    case idle
    case registering(DeviceMode)
    case waitingForConnection(DeviceMode)
    case connected(mode: DeviceMode, hostName: String)
    case unavailable(String)
    case error(String)

    var label: String {
        switch self {
        case .idle: "Idle"
        case .registering: "Preparing HID profile"
        case .waitingForConnection(let mode): "Waiting for \(mode.deviceName)"
        case .connected(_, let hostName): "Connected to \(hostName)"
        case .unavailable: "Transport unavailable"
        case .error: "Error"
        }
    }

    var isConnected: Bool {
        if case .connected = self { true } else { false }
    }
}
