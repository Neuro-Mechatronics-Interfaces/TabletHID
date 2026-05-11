import Foundation

enum HIDTransportEvent {
    case waiting(DeviceMode)
    case reconnecting(mode: DeviceMode, hostName: String)
    case pendingConnection(mode: DeviceMode, host: HIDHost)
    case connected(mode: DeviceMode, host: HIDHost)
    case disconnected(DeviceMode?)
    case unavailable(String)
    case error(String)
}

protocol HIDTransport: AnyObject {
    var isAvailable: Bool { get }
    var unavailableReason: String { get }
    var onEvent: ((HIDTransportEvent) -> Void)? { get set }

    func initialize(mode: DeviceMode, deviceName: String) throws
    func startPairing(mode: DeviceMode, deviceName: String, excludingHostIDs: Set<String>) throws
    func reconnect(mode: DeviceMode, host: HIDHost, deviceName: String) throws
    func sendReport(id: UInt8, data: Data)
    func approvePendingConnection(identifier: String)
    func rejectPendingConnection(identifier: String)
    func disconnect()
    func cancelConnection()
}

final class NoopHIDTransport: HIDTransport {
    let isAvailable = false
    let unavailableReason = "iOS does not expose a public Bluetooth HID peripheral API equivalent to Android BluetoothHidDevice."
    var onEvent: ((HIDTransportEvent) -> Void)?

    func initialize(mode: DeviceMode, deviceName: String) throws {}
    func startPairing(mode: DeviceMode, deviceName: String, excludingHostIDs: Set<String>) throws {}
    func reconnect(mode: DeviceMode, host: HIDHost, deviceName: String) throws {}
    func sendReport(id: UInt8, data: Data) {}
    func approvePendingConnection(identifier: String) {}
    func rejectPendingConnection(identifier: String) {}
    func disconnect() {}
    func cancelConnection() {}
}
