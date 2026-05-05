import Foundation

struct ConfigStore {
    private let defaults = UserDefaults.standard

    func loadActiveProfile(customProfiles: [Profile]) -> Profile {
        let key = defaults.string(forKey: "active_profile_key") ?? Profile.defaultProfile.key
        return Profile.builtIns.first { $0.key == key }
            ?? customProfiles.first { $0.key == key }
            ?? .defaultProfile
    }

    func saveActiveProfile(_ profile: Profile) {
        defaults.set(profile.key, forKey: "active_profile_key")
    }

    func loadCustomProfiles() -> [Profile] {
        guard let data = defaults.data(forKey: "custom_profiles") else { return [] }
        return (try? JSONDecoder().decode([Profile].self, from: data)) ?? []
    }

    func addCustomProfile(named name: String) -> Profile {
        let profile = Profile(name: name, key: "custom_\(Int(Date().timeIntervalSince1970 * 1000))")
        var profiles = loadCustomProfiles()
        profiles.append(profile)
        if let data = try? JSONEncoder().encode(profiles) {
            defaults.set(data, forKey: "custom_profiles")
        }
        return profile
    }

    func loadTouchMouseConfig(profile: Profile) -> TouchMouseConfig {
        load(TouchMouseConfig.self, key: touchKey(profile)) ?? TouchMouseConfig.defaultForProfile(profile)
    }

    func saveTouchMouseConfig(_ config: TouchMouseConfig, profile: Profile) {
        save(config, key: touchKey(profile))
    }

    func loadGamepadConfig(profile: Profile) -> GamepadConfig {
        load(GamepadConfig.self, key: gamepadKey(profile)) ?? GamepadConfig.defaultForProfile(profile)
    }

    func saveGamepadConfig(_ config: GamepadConfig, profile: Profile) {
        save(config, key: gamepadKey(profile))
    }

    // MARK: - Known hosts (list, replaces single last_hid_host)

    private static let keyHosts  = "known_hid_hosts"
    private static let keyLegacy = "last_hid_host"
    private static let maxHosts  = 10

    func loadKnownHosts() -> [HIDHost] {
        // One-time migration from the old single-host key.
        if defaults.object(forKey: Self.keyHosts) == nil,
           let host = load(HIDHost.self, key: Self.keyLegacy) {
            let list = [host]
            saveHosts(list)
            defaults.removeObject(forKey: Self.keyLegacy)
            return list
        }
        return load([HIDHost].self, key: Self.keyHosts) ?? []
    }

    func upsertHost(_ host: HIDHost) {
        var list = loadKnownHosts()
        if let idx = list.firstIndex(where: { $0.identifier == host.identifier }) {
            // Preserve alias; update displayName and lastSeen from the live connection.
            list[idx] = HIDHost(
                identifier: host.identifier,
                displayName: host.displayName.isEmpty ? list[idx].displayName : host.displayName,
                alias: list[idx].alias,
                lastMode: host.lastMode,
                lastSeen: host.lastSeen
            )
        } else {
            list.insert(host, at: 0)
        }
        let trimmed = list.sorted { $0.lastSeen > $1.lastSeen }.prefix(Self.maxHosts)
        saveHosts(Array(trimmed))
    }

    func updateHostAlias(identifier: String, alias: String?) {
        let trimmed = alias?.trimmingCharacters(in: .whitespacesAndNewlines)
            .nilIfEmpty
        var list = loadKnownHosts()
        if let idx = list.firstIndex(where: { $0.identifier == identifier }) {
            list[idx] = HIDHost(
                identifier: list[idx].identifier,
                displayName: list[idx].displayName,
                alias: trimmed,
                lastMode: list[idx].lastMode,
                lastSeen: list[idx].lastSeen
            )
            saveHosts(list)
        }
    }

    func removeHost(identifier: String) {
        saveHosts(loadKnownHosts().filter { $0.identifier != identifier })
    }

    private func saveHosts(_ hosts: [HIDHost]) {
        save(hosts, key: Self.keyHosts)
    }

    func loadAppearanceMode() -> AppearanceMode {
        guard let raw = defaults.string(forKey: "appearance_mode"),
              let mode = AppearanceMode(rawValue: raw) else { return .system }
        return mode
    }

    func saveAppearanceMode(_ mode: AppearanceMode) {
        defaults.set(mode.rawValue, forKey: "appearance_mode")
    }

    private func touchKey(_ profile: Profile) -> String {
        "touch_mouse_config_\(profile.key)"
    }

    private func gamepadKey(_ profile: Profile) -> String {
        "gamepad_config_\(profile.key)"
    }

    private func load<T: Decodable>(_ type: T.Type, key: String) -> T? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    private func save<T: Encodable>(_ value: T, key: String) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        defaults.set(data, forKey: key)
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
