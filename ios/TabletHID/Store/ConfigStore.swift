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

    func loadLastHost() -> HIDHost? {
        load(HIDHost.self, key: "last_hid_host")
    }

    func saveLastHost(_ host: HIDHost) {
        save(host, key: "last_hid_host")
    }

    func clearLastHost() {
        defaults.removeObject(forKey: "last_hid_host")
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
