import Foundation

@MainActor
final class AppState: ObservableObject {
    @Published var connectionState: HIDConnectionState = .idle
    @Published var activeProfile: Profile
    @Published var customProfiles: [Profile]
    @Published var touchMouseConfig: TouchMouseConfig
    @Published var gamepadConfig: GamepadConfig
    @Published var knownHosts: [HIDHost]
    @Published var appearanceMode: AppearanceMode
    @Published var loggingEnabled: Bool

    private let store = ConfigStore()
    private let transport: HIDTransport
    private var sessionLogger: SessionLogger?

    init(transport: HIDTransport = ExperimentalBLEHIDTransport()) {
        self.transport = transport
        let customProfiles = store.loadCustomProfiles()
        let activeProfile = store.loadActiveProfile(customProfiles: customProfiles)
        self.customProfiles = customProfiles
        self.activeProfile = activeProfile
        self.touchMouseConfig = store.loadTouchMouseConfig(profile: activeProfile)
        self.gamepadConfig = store.loadGamepadConfig(profile: activeProfile)
        self.knownHosts = store.loadKnownHosts()
        self.appearanceMode = store.loadAppearanceMode()
        self.loggingEnabled = store.loadLoggingEnabled()
        self.transport.onEvent = { [weak self] event in
            Task { @MainActor in
                self?.handleTransportEvent(event)
            }
        }
    }

    var allProfiles: [Profile] {
        Profile.builtIns + customProfiles
    }

    // MARK: - Profile

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

    // MARK: - Known hosts

    /// The most-recently connected host, used as the default reconnect target.
    var lastHost: HIDHost? { knownHosts.first }

    func renameHost(_ host: HIDHost, alias: String?) {
        store.updateHostAlias(identifier: host.identifier, alias: alias)
        knownHosts = store.loadKnownHosts()
    }

    func forgetHost(_ host: HIDHost) {
        store.removeHost(identifier: host.identifier)
        knownHosts = store.loadKnownHosts()
        if case .connected(_, let current) = connectionState, current.identifier == host.identifier {
            transport.disconnect()
            connectionState = .idle
        }
    }

    // MARK: - Transport

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

    func reconnect(mode: DeviceMode, host: HIDHost) {
        connectionState = .reconnecting(mode: mode, hostName: host.label)
        do {
            try transport.reconnect(mode: mode, host: host)
            if !transport.isAvailable {
                connectionState = .unavailable(transport.unavailableReason)
            }
        } catch {
            connectionState = .error(error.localizedDescription)
        }
    }

    func developmentConnect(mode: DeviceMode) {
        connectionState = .connected(
            mode: mode,
            host: HIDHost(
                identifier: "development-preview",
                displayName: "Development Preview",
                alias: nil,
                lastMode: mode,
                lastSeen: Date()
            )
        )
    }

    func disconnect() {
        transport.disconnect()
        connectionState = .idle
    }

    func setAppearanceMode(_ mode: AppearanceMode) {
        appearanceMode = mode
        store.saveAppearanceMode(mode)
    }

    func setLoggingEnabled(_ enabled: Bool) {
        loggingEnabled = enabled
        store.saveLoggingEnabled(enabled)
        if enabled {
            if case .connected(let mode, _) = connectionState { startSession(mode: mode) }
        } else {
            endSession()
        }
    }

    private func syncLogger() {
        if loggingEnabled, case .connected(let mode, _) = connectionState {
            if sessionLogger == nil { startSession(mode: mode) }
        } else {
            endSession()
        }
    }

    private func startSession(mode: DeviceMode) {
        endSession()
        sessionLogger = try? SessionLogger(
            mode: mode,
            profileName: activeProfile.name,
            touchConfig: mode == .touchMouse ? touchMouseConfig : nil
        )
    }

    private func endSession() {
        sessionLogger?.close()
        sessionLogger = nil
    }

    // MARK: - Config

    func updateTouchMouseConfig(_ config: TouchMouseConfig) {
        touchMouseConfig = config
        store.saveTouchMouseConfig(config, profile: activeProfile)
    }

    func updateGamepadConfig(_ config: GamepadConfig) {
        gamepadConfig = config
        store.saveGamepadConfig(config, profile: activeProfile)
    }

    // MARK: - HID reports

    func sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int = 0) {
        sessionLogger?.logMouse(buttons: buttons, dx: dx, dy: dy, wheel: wheel)
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
        sessionLogger?.logGamepad(lx: leftX, ly: leftY, rx: rightX, ry: rightY, lt: leftTrigger, rt: rightTrigger, buttons: buttons, hat: hat)
        let report = HIDReportDescriptors.buildGamepadReport(
            leftX: leftX, leftY: leftY,
            rightX: rightX, rightY: rightY,
            leftTrigger: leftTrigger, rightTrigger: rightTrigger,
            buttons: buttons, hat: hat
        )
        transport.sendReport(id: HIDReportDescriptors.reportIDGamepad, data: report)
    }

    // MARK: - Transport events

    private func handleTransportEvent(_ event: HIDTransportEvent) {
        switch event {
        case .waiting(let mode):
            connectionState = .waitingForConnection(mode)
        case .reconnecting(let mode, let hostName):
            connectionState = .reconnecting(mode: mode, hostName: hostName)
        case .connected(let mode, let host):
            let updatedHost = HIDHost(
                identifier: host.identifier,
                displayName: host.displayName,
                alias: knownHosts.first(where: { $0.identifier == host.identifier })?.alias,
                lastMode: mode,
                lastSeen: Date()
            )
            store.upsertHost(updatedHost)
            knownHosts = store.loadKnownHosts()
            connectionState = .connected(mode: mode, host: updatedHost)
        case .disconnected:
            connectionState = .idle
        case .unavailable(let reason):
            connectionState = .unavailable(reason)
        case .error(let message):
            connectionState = .error(message)
        }
        syncLogger()
    }
}
