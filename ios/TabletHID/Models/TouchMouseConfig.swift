import Foundation

enum TouchMode: String, Codable, CaseIterable, Identifiable {
    case touch
    case mouse

    var id: String { rawValue }
}

enum ZoneType: String, Codable, CaseIterable, Identifiable {
    case staticZone
    case dynamic

    var id: String { rawValue }
}

enum ClickBehavior: String, Codable, CaseIterable, Identifiable {
    case momentary
    case latching

    var id: String { rawValue }
}

enum MacroHostDefaults: String, Codable, CaseIterable, Identifiable {
    case windows
    case mac

    var id: String { rawValue }
    var label: String { self == .windows ? "Windows" : "Mac" }
}

struct KeyboardMacroButtonConfig: Codable, Equatable, Identifiable {
    var id = UUID()
    var label: String
    var modifiers: Int
    var keyUsages: [Int]
    var layoutOffsetX: Double = 0
    var layoutOffsetY: Double = 0
    var layoutScaleX: Double = 1
    var layoutScaleY: Double = 1

    enum CodingKeys: String, CodingKey {
        case id, label, modifiers, keyUsages, layoutOffsetX, layoutOffsetY, layoutScaleX, layoutScaleY
    }

    init(
        id: UUID = UUID(),
        label: String,
        modifiers: Int,
        keyUsages: [Int],
        layoutOffsetX: Double = 0,
        layoutOffsetY: Double = 0,
        layoutScaleX: Double = 1,
        layoutScaleY: Double = 1
    ) {
        self.id = id
        self.label = label
        self.modifiers = modifiers
        self.keyUsages = keyUsages
        self.layoutOffsetX = layoutOffsetX
        self.layoutOffsetY = layoutOffsetY
        self.layoutScaleX = layoutScaleX
        self.layoutScaleY = layoutScaleY
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        label = try values.decodeIfPresent(String.self, forKey: .label) ?? "Macro"
        modifiers = try values.decodeIfPresent(Int.self, forKey: .modifiers) ?? 0
        keyUsages = try values.decodeIfPresent([Int].self, forKey: .keyUsages) ?? []
        layoutOffsetX = try values.decodeIfPresent(Double.self, forKey: .layoutOffsetX) ?? 0
        layoutOffsetY = try values.decodeIfPresent(Double.self, forKey: .layoutOffsetY) ?? 0
        layoutScaleX = try values.decodeIfPresent(Double.self, forKey: .layoutScaleX) ?? 1
        layoutScaleY = try values.decodeIfPresent(Double.self, forKey: .layoutScaleY) ?? 1
    }
}

enum KeyboardMacroPresets {
    static let modLeftControl = 0x01
    static let modLeftAlt = 0x04
    static let modLeftGUI = 0x08
    static let keyS = 0x16
    static let keyTab = 0x2B

    static let windowsDefaults = [
        KeyboardMacroButtonConfig(label: "Alt+Tab", modifiers: modLeftAlt, keyUsages: [keyTab]),
        KeyboardMacroButtonConfig(label: "Ctrl+S", modifiers: modLeftControl, keyUsages: [keyS])
    ]

    static let macDefaults = [
        KeyboardMacroButtonConfig(label: "Cmd+Tab", modifiers: modLeftGUI, keyUsages: [keyTab]),
        KeyboardMacroButtonConfig(label: "Cmd+S", modifiers: modLeftGUI, keyUsages: [keyS])
    ]

    static func defaults(for host: MacroHostDefaults) -> [KeyboardMacroButtonConfig] {
        host == .mac ? macDefaults : windowsDefaults
    }
}

struct ButtonZoneConfig: Codable, Equatable {
    var enabled = false
    var zoneType = ZoneType.staticZone
    var behavior = ClickBehavior.momentary
    var staticLeft: Double = 0
    var staticTop: Double = 0.75
    var staticRight: Double = 0.25
    var staticBottom: Double = 1
    var dynamicOffsetX: Double = 0.15
    var dynamicOffsetY: Double = 0
    var dynamicRadius: Double = 0.07
}

struct TouchMouseConfig: Codable, Equatable {
    var mode = TouchMode.touch
    var sensitivity = 5
    var leftButton = ButtonZoneConfig(dynamicOffsetX: -0.15)
    var rightButton = ButtonZoneConfig(
        staticLeft: 0.75,
        staticTop: 0.75,
        staticRight: 1,
        staticBottom: 1
    )
    var scrollEnabled = true
    var invertScroll = false
    var sharedDynamicZone = false
    var sharedDynamicOffsetX: Double = 0
    var sharedDynamicOffsetY: Double = 0.18
    var sharedDynamicRadius: Double = 0.08
    var macroHostDefaults = MacroHostDefaults.windows
    var macroButtons: [KeyboardMacroButtonConfig] = []

    init(
        mode: TouchMode = .touch,
        sensitivity: Int = 5,
        leftButton: ButtonZoneConfig = ButtonZoneConfig(dynamicOffsetX: -0.15),
        rightButton: ButtonZoneConfig = ButtonZoneConfig(
            staticLeft: 0.75,
            staticTop: 0.75,
            staticRight: 1,
            staticBottom: 1
        ),
        scrollEnabled: Bool = true,
        invertScroll: Bool = false,
        sharedDynamicZone: Bool = false,
        sharedDynamicOffsetX: Double = 0,
        sharedDynamicOffsetY: Double = 0.18,
        sharedDynamicRadius: Double = 0.08,
        macroHostDefaults: MacroHostDefaults = .windows,
        macroButtons: [KeyboardMacroButtonConfig] = []
    ) {
        self.mode = mode
        self.sensitivity = sensitivity
        self.leftButton = leftButton
        self.rightButton = rightButton
        self.scrollEnabled = scrollEnabled
        self.invertScroll = invertScroll
        self.sharedDynamicZone = sharedDynamicZone
        self.sharedDynamicOffsetX = sharedDynamicOffsetX
        self.sharedDynamicOffsetY = sharedDynamicOffsetY
        self.sharedDynamicRadius = sharedDynamicRadius
        self.macroHostDefaults = macroHostDefaults
        self.macroButtons = macroButtons
    }

    enum CodingKeys: String, CodingKey {
        case mode, sensitivity, leftButton, rightButton, scrollEnabled, invertScroll
        case sharedDynamicZone, sharedDynamicOffsetX, sharedDynamicOffsetY, sharedDynamicRadius
        case macroHostDefaults, macroButtons
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        let defaults = TouchMouseConfig()
        mode = try values.decodeIfPresent(TouchMode.self, forKey: .mode) ?? defaults.mode
        sensitivity = try values.decodeIfPresent(Int.self, forKey: .sensitivity) ?? defaults.sensitivity
        leftButton = try values.decodeIfPresent(ButtonZoneConfig.self, forKey: .leftButton) ?? defaults.leftButton
        rightButton = try values.decodeIfPresent(ButtonZoneConfig.self, forKey: .rightButton) ?? defaults.rightButton
        scrollEnabled = try values.decodeIfPresent(Bool.self, forKey: .scrollEnabled) ?? defaults.scrollEnabled
        invertScroll = try values.decodeIfPresent(Bool.self, forKey: .invertScroll) ?? defaults.invertScroll
        sharedDynamicZone = try values.decodeIfPresent(Bool.self, forKey: .sharedDynamicZone) ?? defaults.sharedDynamicZone
        sharedDynamicOffsetX = try values.decodeIfPresent(Double.self, forKey: .sharedDynamicOffsetX) ?? defaults.sharedDynamicOffsetX
        sharedDynamicOffsetY = try values.decodeIfPresent(Double.self, forKey: .sharedDynamicOffsetY) ?? defaults.sharedDynamicOffsetY
        sharedDynamicRadius = try values.decodeIfPresent(Double.self, forKey: .sharedDynamicRadius) ?? defaults.sharedDynamicRadius
        macroHostDefaults = try values.decodeIfPresent(MacroHostDefaults.self, forKey: .macroHostDefaults) ?? defaults.macroHostDefaults
        macroButtons = try values.decodeIfPresent([KeyboardMacroButtonConfig].self, forKey: .macroButtons) ?? defaults.macroButtons
    }

    func normalizedForStorage() -> TouchMouseConfig {
        var config = self
        config.leftButton = config.leftButton.normalizedForStorage()
        config.rightButton = config.rightButton.normalizedForStorage()
        config.sharedDynamicOffsetX = config.sharedDynamicOffsetX.clamped(to: -1...1).snapped(to: 0.05)
        config.sharedDynamicOffsetY = config.sharedDynamicOffsetY.clamped(to: -1...1).snapped(to: 0.05)
        config.sharedDynamicRadius = config.sharedDynamicRadius.clamped(to: 0.03...0.2).snapped(to: 0.01)
        return config
    }

    static func defaultForProfile(_ profile: Profile) -> TouchMouseConfig {
        var config = TouchMouseConfig()
        config.mode = .mouse
        config.sensitivity = profile.key == Profile.accessAdvanced.key ? 3 : 7
        config.leftButton.enabled = true
        config.leftButton.zoneType = .dynamic
        config.leftButton.dynamicOffsetY = 0.2
        config.leftButton.dynamicRadius = 0.1
        config.rightButton.enabled = true
        config.rightButton.zoneType = .dynamic
        config.rightButton.dynamicOffsetX = 0.2
        config.rightButton.dynamicOffsetY = 0.2
        return config
    }
}

private extension ButtonZoneConfig {
    func normalizedForStorage() -> ButtonZoneConfig {
        var button = self
        button.staticLeft = button.staticLeft.clamped(to: 0...1).snapped(to: 0.01)
        button.staticTop = button.staticTop.clamped(to: 0...1).snapped(to: 0.01)
        button.staticRight = button.staticRight.clamped(to: 0...1).snapped(to: 0.01)
        button.staticBottom = button.staticBottom.clamped(to: 0...1).snapped(to: 0.01)
        button.dynamicOffsetX = button.dynamicOffsetX.clamped(to: -1...1).snapped(to: 0.05)
        button.dynamicOffsetY = button.dynamicOffsetY.clamped(to: -1...1).snapped(to: 0.05)
        button.dynamicRadius = button.dynamicRadius.clamped(to: 0.03...0.2).snapped(to: 0.01)
        return button
    }
}

extension Double {
    func clamped(to range: ClosedRange<Double>) -> Double {
        min(max(self, range.lowerBound), range.upperBound)
    }

    func snapped(to step: Double) -> Double {
        guard step > 0 else { return self }
        return (self / step).rounded() * step
    }
}
