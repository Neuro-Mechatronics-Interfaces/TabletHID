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
        invertScroll: Bool = false
    ) {
        self.mode = mode
        self.sensitivity = sensitivity
        self.leftButton = leftButton
        self.rightButton = rightButton
        self.scrollEnabled = scrollEnabled
        self.invertScroll = invertScroll
    }

    enum CodingKeys: String, CodingKey {
        case mode, sensitivity, leftButton, rightButton, scrollEnabled, invertScroll
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
    }

    func normalizedForStorage() -> TouchMouseConfig {
        var config = self
        config.leftButton = config.leftButton.normalizedForStorage()
        config.rightButton = config.rightButton.normalizedForStorage()
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
