import Foundation

@MainActor
final class AppState: ObservableObject {
    @Published var connectionState: HIDConnectionState = .idle
    @Published var activeProfile: Profile
    @Published var customProfiles: [Profile]
    @Published var touchMouseConfig: TouchMouseConfig
    @Published var gamepadConfig: GamepadConfig

    private let store = ConfigStore()
    private let transport: HIDTransport

    init(transport: HIDTransport = ExperimentalBLEHIDTransport()) {
        self.transport = transport
        let customProfiles = store.loadCustomProfiles()
        let activeProfile = store.loadActiveProfile(customProfiles: customProfiles)
        self.customProfiles = customProfiles
        self.activeProfile = activeProfile
        self.touchMouseConfig = store.loadTouchMouseConfig(profile: activeProfile)
        self.gamepadConfig = store.loadGamepadConfig(profile: activeProfile)
        self.transport.onEvent = { [weak self] event in
            Task { @MainActor in
                self?.handleTransportEvent(event)
            }
        }
    }

    var allProfiles: [Profile] {
        Profile.builtIns + customProfiles
    }

    func setProfile(_ profile: Profile) {
        activeProfile = profile
        store.saveActiveProfile(profile)
        touchMouseConfig = store.loadTouchMouseConfig(profile: profile)
        gamepadConfig = store.loadGamepadConfig(profile: profile)
    }

    func addCustomProfile(named name: String) {
        let profile = store.addCustomProfile(named: name)
        customProfiles = store.loadCustomProfiles()
        setProfile(profile)
    }

    func initialize(mode: DeviceMode) {
        connectionState = .registering(mode)
        do {
            try transport.initialize(mode: mode)
            if !transport.isAvailable {
                connectionState = .unavailable(transport.unavailableReason)
            }
        } catch {
            connectionState = .error(error.localizedDescription)
        }
    }

    func developmentConnect(mode: DeviceMode) {
        connectionState = .connected(mode: mode, hostName: "Development Preview")
    }

    func disconnect() {
        transport.disconnect()
        connectionState = .idle
    }

    func updateTouchMouseConfig(_ config: TouchMouseConfig) {
        touchMouseConfig = config
        store.saveTouchMouseConfig(config, profile: activeProfile)
    }

    func updateGamepadConfig(_ config: GamepadConfig) {
        gamepadConfig = config
        store.saveGamepadConfig(config, profile: activeProfile)
    }

    func sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int = 0) {
        let report = HIDReportDescriptors.buildMouseReport(buttons: buttons, dx: dx, dy: dy, wheel: wheel)
        transport.sendReport(id: HIDReportDescriptors.reportIDMouse, data: report)
    }

    func sendGamepadReport(
        leftX: Int = 0,
        leftY: Int = 0,
        rightX: Int = 0,
        rightY: Int = 0,
        leftTrigger: Int = 0,
        rightTrigger: Int = 0,
        buttons: Int = 0,
        hat: Int = HIDReportDescriptors.hatNone
    ) {
        let report = HIDReportDescriptors.buildGamepadReport(
            leftX: leftX,
            leftY: leftY,
            rightX: rightX,
            rightY: rightY,
            leftTrigger: leftTrigger,
            rightTrigger: rightTrigger,
            buttons: buttons,
            hat: hat
        )
        transport.sendReport(id: HIDReportDescriptors.reportIDGamepad, data: report)
    }

    private func handleTransportEvent(_ event: HIDTransportEvent) {
        switch event {
        case .waiting(let mode):
            connectionState = .waitingForConnection(mode)
        case .connected(let mode, let hostName):
            connectionState = .connected(mode: mode, hostName: hostName)
        case .disconnected:
            connectionState = .idle
        case .unavailable(let reason):
            connectionState = .unavailable(reason)
        case .error(let message):
            connectionState = .error(message)
        }
    }
}
