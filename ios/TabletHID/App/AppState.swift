import Foundation
#if canImport(UIKit)
import UIKit
#endif

@MainActor
final class AppState: ObservableObject {
    @Published var connectionState: HIDConnectionState = .idle
    @Published var activeProfile: Profile
    @Published var customProfiles: [Profile]
    @Published var touchMouseConfig: TouchMouseConfig
    @Published var gamepadConfig: GamepadConfig
    @Published var knownHosts: [HIDHost]
    @Published var appearanceMode: AppearanceMode
    @Published var deviceName: String
    @Published var largeTextEnabled: Bool
    @Published var highContrastEnabled: Bool
    @Published var loggingEnabled: Bool
    @Published var autoReconnectEnabled: Bool
    @Published var onboardingCompleted: Bool
    @Published var orientationLock: OrientationLock
    @Published var pendingConnectionHost: HIDHost?

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
        self.deviceName = store.loadDeviceName()
        self.largeTextEnabled = store.loadLargeTextEnabled()
        self.highContrastEnabled = store.loadHighContrastEnabled()
        self.loggingEnabled = store.loadLoggingEnabled()
        self.autoReconnectEnabled = store.loadAutoReconnectEnabled()
        self.onboardingCompleted = store.loadOnboardingCompleted()
        self.pendingConnectionHost = nil
        let lock = store.loadOrientationLock()
        self.orientationLock = lock
        #if canImport(UIKit)
        AppDelegate.orientationLock = lock.interfaceOrientationMask
        #endif
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
            try transport.initialize(mode: mode, deviceName: deviceName)
            if !transport.isAvailable {
                connectionState = .unavailable(transport.unavailableReason)
            }
        } catch {
            connectionState = .error(error.localizedDescription)
        }
    }

    func startPairing(mode: DeviceMode) {
        pendingConnectionHost = nil
        connectionState = .registering(mode)
        do {
            try transport.startPairing(
                mode: mode,
                deviceName: deviceName,
                excludingHostIDs: Set(knownHosts.map(\.identifier))
            )
            if !transport.isAvailable {
                connectionState = .unavailable(transport.unavailableReason)
            }
        } catch {
            connectionState = .error(error.localizedDescription)
        }
    }

    func reconnect(mode: DeviceMode, host: HIDHost) {
        pendingConnectionHost = nil
        connectionState = .reconnecting(mode: mode, hostName: host.label)
        do {
            try transport.reconnect(mode: mode, host: host, deviceName: deviceName)
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
        pendingConnectionHost = nil
        transport.disconnect()
        connectionState = .idle
    }

    func cancelConnection() {
        pendingConnectionHost = nil
        transport.cancelConnection()
        connectionState = .idle
    }

    func approvePendingConnection() {
        guard let host = pendingConnectionHost else { return }
        transport.approvePendingConnection(identifier: host.identifier)
        pendingConnectionHost = nil
    }

    func rejectPendingConnection() {
        guard let host = pendingConnectionHost else { return }
        transport.rejectPendingConnection(identifier: host.identifier)
        pendingConnectionHost = nil
    }

    func setAppearanceMode(_ mode: AppearanceMode) {
        appearanceMode = mode
        store.saveAppearanceMode(mode)
    }

    func setDeviceName(_ name: String) {
        let sanitized = store.sanitizeDeviceName(name)
        deviceName = sanitized
        store.saveDeviceName(sanitized)
    }

    func setLargeTextEnabled(_ enabled: Bool) {
        largeTextEnabled = enabled
        store.saveLargeTextEnabled(enabled)
    }

    func setHighContrastEnabled(_ enabled: Bool) {
        highContrastEnabled = enabled
        store.saveHighContrastEnabled(enabled)
    }

    func setOrientationLock(_ lock: OrientationLock) {
        orientationLock = lock
        store.saveOrientationLock(lock)
        #if canImport(UIKit)
        AppDelegate.orientationLock = lock.interfaceOrientationMask
        if #available(iOS 16.0, *) {
            if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
                scene.requestGeometryUpdate(
                    UIWindowScene.GeometryPreferences.iOS(interfaceOrientations: lock.interfaceOrientationMask)
                ) { _ in }
            }
        }
        #endif
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

    func setAutoReconnectEnabled(_ enabled: Bool) {
        autoReconnectEnabled = enabled
        store.saveAutoReconnectEnabled(enabled)
    }

    func maybeAutoReconnectOnLaunch() {
        guard autoReconnectEnabled,
              onboardingCompleted,
              case .idle = connectionState,
              let host = lastHost else { return }
        reconnect(mode: host.lastMode, host: host)
    }

    func completeOnboarding(deviceName name: String) {
        setDeviceName(name)
        onboardingCompleted = true
        store.saveOnboardingCompleted(true)
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
        let normalized = config.normalizedForStorage()
        touchMouseConfig = normalized
        store.saveTouchMouseConfig(normalized, profile: activeProfile)
    }

    func updateGamepadConfig(_ config: GamepadConfig) {
        gamepadConfig = config
        store.saveGamepadConfig(config, profile: activeProfile)
    }

    // MARK: - HID reports

    func sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int = 0, horizontalWheel: Int = 0) {
        sessionLogger?.logMouse(buttons: buttons, dx: dx, dy: dy, wheel: wheel, horizontalWheel: horizontalWheel)
        let report = HIDReportDescriptors.buildMouseReport(
            buttons: buttons,
            dx: dx,
            dy: dy,
            wheel: wheel,
            horizontalWheel: horizontalWheel
        )
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

    func sendKeyboardReport(modifiers: Int = 0, keyUsages: [Int] = []) {
        let report = HIDReportDescriptors.buildKeyboardReport(modifiers: modifiers, keyUsages: keyUsages)
        transport.sendReport(id: HIDReportDescriptors.reportIDKeyboard, data: report)
    }

    // MARK: - Transport events

    private func handleTransportEvent(_ event: HIDTransportEvent) {
        switch event {
        case .waiting(let mode):
            connectionState = .waitingForConnection(mode: mode, deviceName: deviceName)
        case .reconnecting(let mode, let hostName):
            connectionState = .reconnecting(mode: mode, hostName: hostName)
        case .pendingConnection(let mode, let host):
            pendingConnectionHost = host
            connectionState = .waitingForConnection(mode: mode, deviceName: deviceName)
        case .connected(let mode, let host):
            pendingConnectionHost = nil
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
            pendingConnectionHost = nil
            connectionState = .idle
        case .unavailable(let reason):
            connectionState = .unavailable(reason)
        case .error(let message):
            connectionState = .error(message)
        }
        syncLogger()
    }
}
