import CoreBluetooth
import Foundation

final class ExperimentalBLEHIDTransport: NSObject, HIDTransport {
    var onEvent: ((HIDTransportEvent) -> Void)?

    private(set) var isAvailable = true
    private(set) var unavailableReason = ""

    private var peripheralManager: CBPeripheralManager?
    private var activeMode: DeviceMode?
    private var activeDeviceName = "TabletHID"
    private var reconnectTarget: HIDHost?
    private var subscribedCentrals: [CBCentral] = []

    // The GATT service is built once and kept alive for the app session.
    // Rebuilding it changes attribute handles; hosts identify the two same-UUID (2A4D)
    // Input Report characteristics by handle, so a rebuild scrambles routing and
    // breaks cross-mode switching even when the descriptor bytes are identical.
    private var hidService: CBMutableService?
    private var protocolModeCharacteristic: CBMutableCharacteristic?
    private var hidControlPointCharacteristic: CBMutableCharacteristic?
    private var mouseReportCharacteristic: CBMutableCharacteristic?
    private var gamepadReportCharacteristic: CBMutableCharacteristic?

    private var protocolMode = Data([0x01])
    private var lastMouseReport   = HIDReportDescriptors.buildMouseReport(buttons: 0, dx: 0, dy: 0)
    private var lastGamepadReport = HIDReportDescriptors.buildGamepadReport(
        leftX: 0, leftY: 0, rightX: 0, rightY: 0,
        leftTrigger: 0, rightTrigger: 0, buttons: 0,
        hat: HIDReportDescriptors.hatNone
    )

    private enum UUIDs {
        // Expanded form is intentional. Short "1812" is rejected on many iOS versions.
        static let hidService      = CBUUID(string: "00001812-0000-1000-8000-00805F9B34FB")
        static let hidInformation  = CBUUID(string: "2A4A")
        static let reportMap       = CBUUID(string: "2A4B")
        static let hidControlPoint = CBUUID(string: "2A4C")
        static let report          = CBUUID(string: "2A4D")
        static let protocolMode    = CBUUID(string: "2A4E")
        static let reportReference = CBUUID(string: "2908")
    }

    // MARK: – HIDTransport

    func initialize(mode: DeviceMode, deviceName: String) throws {
        activeMode = mode
        activeDeviceName = sanitizeDeviceName(deviceName)
        reconnectTarget = nil
        unavailableReason = ""
        isAvailable = true

        if peripheralManager == nil {
            peripheralManager = CBPeripheralManager(delegate: self, queue: .main)
            return  // evaluateState(.poweredOn) will call startHIDPeripheral when ready
        }

        guard peripheralManager?.state == .poweredOn else {
            evaluateState(peripheralManager?.state ?? .unknown)
            return
        }

        if hidService != nil {
            // Service is already live — just switch mode without any teardown.
            // The GATT handles stay stable, so the host keeps routing correctly.
            if !subscribedCentrals.isEmpty {
                let host = HIDHost.fromCentralIdentifier(
                    subscribedCentrals[0].identifier.uuidString, mode: mode)
                onEvent?(.connected(mode: mode, host: host))
            } else {
                restartAdvertisingIfNeeded()
                onEvent?(.waiting(mode))
            }
        } else {
            startHIDPeripheral()
        }
    }

    func reconnect(mode: DeviceMode, host: HIDHost, deviceName: String) throws {
        activeMode = mode
        activeDeviceName = sanitizeDeviceName(deviceName)
        reconnectTarget = host
        unavailableReason = ""
        isAvailable = true
        onEvent?(.reconnecting(mode: mode, hostName: host.displayName))

        if peripheralManager == nil {
            peripheralManager = CBPeripheralManager(delegate: self, queue: .main)
            return
        }

        guard peripheralManager?.state == .poweredOn else {
            evaluateState(peripheralManager?.state ?? .unknown)
            return
        }

        if hidService != nil {
            if !subscribedCentrals.isEmpty {
                reconnectTarget = nil
                let h = HIDHost.fromCentralIdentifier(
                    subscribedCentrals[0].identifier.uuidString, mode: mode)
                onEvent?(.connected(mode: mode, host: h))
            } else {
                // Service up but host disconnected — restart advertising.
                // The host will auto-reconnect using the existing bond.
                restartAdvertisingIfNeeded()
                // Stay in reconnecting state; didSubscribeTo will fire when host rejoins.
            }
        } else {
            startHIDPeripheral()
        }
    }

    func sendReport(id: UInt8, data: Data) {
        switch id {
        case HIDReportDescriptors.reportIDMouse:
            lastMouseReport = data
            send(data, via: mouseReportCharacteristic)
        case HIDReportDescriptors.reportIDGamepad:
            lastGamepadReport = data
            send(data, via: gamepadReportCharacteristic)
        default:
            break
        }
    }

    /// Soft disconnect — keeps the GATT service and its attribute handles alive so the
    /// host can reconnect (same or different mode) without re-pairing.
    /// Advertising is NOT restarted here; call initialize() or reconnect() to become
    /// discoverable again. This avoids the peripheral emitting .waiting immediately
    /// after the user intentionally exits a control screen.
    func disconnect() {
        reconnectTarget = nil
        let mode = activeMode
        onEvent?(.disconnected(mode))
        // activeMode, hidService, subscribedCentrals intentionally preserved.
    }

    // MARK: – Private

    private func startHIDPeripheral() {
        guard let peripheralManager else { return }

        peripheralManager.stopAdvertising()
        peripheralManager.removeAllServices()
        subscribedCentrals.removeAll()

        let service = CBMutableService(type: UUIDs.hidService, primary: true)

        let hidInformation = CBMutableCharacteristic(
            type: UUIDs.hidInformation,
            properties: [.read],
            value: Data([0x11, 0x01, 0x00, 0x02]),
            permissions: [.readable]
        )

        let reportMap = CBMutableCharacteristic(
            type: UUIDs.reportMap,
            properties: [.read],
            value: Data(HIDReportDescriptors.combinedReportDescriptor),
            permissions: [.readable]
        )

        let protocolModeChar = CBMutableCharacteristic(
            type: UUIDs.protocolMode,
            properties: [.read, .writeWithoutResponse],
            value: nil,
            permissions: [.readable, .writeable]
        )

        let hidControlPoint = CBMutableCharacteristic(
            type: UUIDs.hidControlPoint,
            properties: [.writeWithoutResponse],
            value: nil,
            permissions: [.writeable]
        )

        let mouseReport   = inputReportCharacteristic(reportID: HIDReportDescriptors.reportIDMouse,   initialValue: lastMouseReport)
        let gamepadReport = inputReportCharacteristic(reportID: HIDReportDescriptors.reportIDGamepad, initialValue: lastGamepadReport)

        service.characteristics = [
            hidInformation,
            reportMap,
            protocolModeChar,
            hidControlPoint,
            mouseReport,
            gamepadReport
        ]

        self.hidService                    = service
        self.protocolModeCharacteristic    = protocolModeChar
        self.hidControlPointCharacteristic = hidControlPoint
        self.mouseReportCharacteristic     = mouseReport
        self.gamepadReportCharacteristic   = gamepadReport

        peripheralManager.add(service)
        startAdvertising()
    }

    private func restartAdvertisingIfNeeded() {
        guard let pm = peripheralManager,
              pm.state == .poweredOn,
              hidService != nil else { return }
        if pm.isAdvertising {
            pm.stopAdvertising()
        }
        startAdvertising()
    }

    private func startAdvertising() {
        guard let peripheralManager,
              peripheralManager.state == .poweredOn else { return }
        peripheralManager.startAdvertising([
            CBAdvertisementDataLocalNameKey:    activeDeviceName,
            CBAdvertisementDataServiceUUIDsKey: [UUIDs.hidService]
        ])
    }

    private func sanitizeDeviceName(_ name: String) -> String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        return String((trimmed.isEmpty ? "TabletHID" : trimmed).prefix(32))
    }

    private func inputReportCharacteristic(reportID: UInt8, initialValue: Data) -> CBMutableCharacteristic {
        let characteristic = CBMutableCharacteristic(
            type: UUIDs.report,
            properties: [.read, .notify],
            value: nil,
            permissions: [.readable]
        )
        characteristic.descriptors = [
            CBMutableDescriptor(type: UUIDs.reportReference, value: Data([reportID, 0x01]))
        ]
        return characteristic
    }

    private func send(_ data: Data, via characteristic: CBMutableCharacteristic?) {
        guard let peripheralManager, let characteristic else { return }
        // Pass nil so CoreBluetooth routes to whichever centrals are subscribed to
        // this specific characteristic — avoids stale CBCentral references.
        peripheralManager.updateValue(data, for: characteristic, onSubscribedCentrals: nil)
    }

    private func evaluateState(_ state: CBManagerState) {
        switch state {
        case .poweredOn:
            isAvailable = true
            unavailableReason = ""
            if hidService == nil, activeMode != nil {
                startHIDPeripheral()
            }
        case .unsupported:
            setUnavailable("Bluetooth LE peripheral mode is not supported on this device.")
        case .unauthorized:
            setUnavailable("Bluetooth permission is not authorized for TabletHID.")
        case .poweredOff:
            setUnavailable("Bluetooth is powered off.")
        case .resetting, .unknown:
            break
        @unknown default:
            setUnavailable("Bluetooth is in an unknown unavailable state.")
        }
    }

    private func setUnavailable(_ reason: String) {
        isAvailable = false
        unavailableReason = reason
        onEvent?(.unavailable(reason))
    }
}

// MARK: – CBPeripheralManagerDelegate

extension ExperimentalBLEHIDTransport: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        evaluateState(peripheral.state)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error {
            onEvent?(.error("Failed to publish BLE HID service: \(error.localizedDescription)"))
        }
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error {
            onEvent?(.error("Failed to advertise BLE HID service: \(error.localizedDescription)"))
            return
        }
        guard let activeMode else { return }
        if let reconnectTarget {
            onEvent?(.reconnecting(mode: activeMode, hostName: reconnectTarget.displayName))
        } else {
            onEvent?(.waiting(activeMode))
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        if !subscribedCentrals.contains(where: { $0.identifier == central.identifier }) {
            subscribedCentrals.append(central)
        }
        if let activeMode {
            reconnectTarget = nil
            onEvent?(.connected(
                mode: activeMode,
                host: .fromCentralIdentifier(central.identifier.uuidString, mode: activeMode)
            ))
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        subscribedCentrals.removeAll { $0.identifier == central.identifier }
        if subscribedCentrals.isEmpty {
            // Host disconnected — restart advertising so it (or a new host) can reconnect.
            restartAdvertisingIfNeeded()
            if let activeMode {
                onEvent?(.waiting(activeMode))
            }
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        switch request.characteristic {
        case protocolModeCharacteristic:
            request.value = protocolMode
        case mouseReportCharacteristic:
            request.value = lastMouseReport
        case gamepadReportCharacteristic:
            request.value = lastGamepadReport
        default:
            peripheral.respond(to: request, withResult: .attributeNotFound)
            return
        }
        peripheral.respond(to: request, withResult: .success)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        for request in requests {
            switch request.characteristic {
            case protocolModeCharacteristic:
                if let value = request.value, value.count == 1 {
                    protocolMode = value
                    peripheral.respond(to: request, withResult: .success)
                } else {
                    peripheral.respond(to: request, withResult: .invalidAttributeValueLength)
                }
            case hidControlPointCharacteristic:
                peripheral.respond(to: request, withResult: .success)
            default:
                peripheral.respond(to: request, withResult: .attributeNotFound)
            }
        }
    }
}
