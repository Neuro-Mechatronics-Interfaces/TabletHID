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
