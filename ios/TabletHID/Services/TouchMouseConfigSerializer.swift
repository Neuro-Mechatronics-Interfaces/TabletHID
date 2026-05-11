import Foundation

/// Converts TouchMouseConfig to and from the canonical JSON dictionary format defined in
/// spec/server-schema.md. Field names and nesting structure are identical to
/// TouchMouseConfigSerializer.kt on Android.
///
/// Note: iOS TouchMouseConfig does not yet expose subRegions or sniperEnabled/sniperDivisor
/// as first-class fields; they are preserved as pass-through values in canonical JSON where
/// available but default to empty/disabled when deserialising into the current model.
enum TouchMouseConfigSerializer {

    // MARK: - To canonical JSON

    static func toCanonicalJson(_ config: TouchMouseConfig) -> [String: Any] {
        var root: [String: Any] = [:]
        root["mode"]             = config.mode.rawValue.uppercased()
        root["sensitivity"]      = config.sensitivity
        root["scrollEnabled"]    = config.scrollEnabled
        root["invertScroll"]     = config.invertScroll
        root["sharedDynamicZone"] = config.sharedDynamicZone
        root["sharedDynamic"] = [
            "offsetX": config.sharedDynamicOffsetX,
            "offsetY": config.sharedDynamicOffsetY,
            "radius":  config.sharedDynamicRadius,
        ] as [String: Any]
        root["leftButton"]  = buttonZoneToJson(config.leftButton)
        root["rightButton"] = buttonZoneToJson(config.rightButton)
        // Sniper: iOS model stores flattened fields; serialise to nested object.
        root["sniper"] = [
            "enabled": false,
            "zone": [
                "left":   0.35,
                "top":    0.88,
                "right":  0.65,
                "bottom": 1.0,
            ],
            "divisor": 4.0,
        ] as [String: Any]
        root["macroHostDefaults"] = config.macroHostDefaults.rawValue.uppercased()
        root["macroButtons"] = config.macroButtons.map { macroToJson($0) }
        return root
    }

    // MARK: - From canonical JSON

    static func fromCanonicalJson(_ json: [String: Any]) -> TouchMouseConfig {
        let sharedDynamic = json["sharedDynamic"] as? [String: Any]
        let leftButtonJson  = json["leftButton"]  as? [String: Any]
        let rightButtonJson = json["rightButton"] as? [String: Any]
        var config = TouchMouseConfig()
        config.mode          = touchMode(from: json["mode"] as? String) ?? .touch
        config.sensitivity   = json["sensitivity"]   as? Int  ?? 5
        config.scrollEnabled = json["scrollEnabled"] as? Bool ?? true
        config.invertScroll  = json["invertScroll"]  as? Bool ?? false
        config.sharedDynamicZone = json["sharedDynamicZone"] as? Bool ?? false
        config.sharedDynamicOffsetX = (sharedDynamic?["offsetX"] as? Double) ?? 0
        config.sharedDynamicOffsetY = (sharedDynamic?["offsetY"] as? Double) ?? 0.18
        config.sharedDynamicRadius  = (sharedDynamic?["radius"]  as? Double) ?? 0.08
        config.leftButton   = buttonZoneFromJson(leftButtonJson,  defaults: config.leftButton)
        config.rightButton  = buttonZoneFromJson(rightButtonJson, defaults: config.rightButton)
        config.macroHostDefaults = macroHost(from: json["macroHostDefaults"] as? String) ?? .windows
        config.macroButtons = (json["macroButtons"] as? [[String: Any]] ?? []).compactMap { macroFromJson($0) }
        return config
    }

    // MARK: - ButtonZoneConfig helpers

    private static func buttonZoneToJson(_ btn: ButtonZoneConfig) -> [String: Any] {
        var j: [String: Any] = [:]
        j["enabled"]  = btn.enabled
        j["zoneType"] = zoneTypeName(btn.zoneType)
        j["behavior"] = clickBehaviorName(btn.behavior)
        j["staticZone"] = [
            "left":   btn.staticLeft,
            "top":    btn.staticTop,
            "right":  btn.staticRight,
            "bottom": btn.staticBottom,
        ] as [String: Any]
        j["dynamicZone"] = [
            "offsetX": btn.dynamicOffsetX,
            "offsetY": btn.dynamicOffsetY,
            "radius":  btn.dynamicRadius,
        ] as [String: Any]
        j["subRegions"] = [] as [[String: Any]]
        return j
    }

    private static func buttonZoneFromJson(_ j: [String: Any]?, defaults: ButtonZoneConfig) -> ButtonZoneConfig {
        var btn = defaults
        guard let j = j else { return btn }
        btn.enabled  = j["enabled"]  as? Bool ?? btn.enabled
        btn.zoneType = zoneType(from: j["zoneType"] as? String) ?? btn.zoneType
        btn.behavior = clickBehavior(from: j["behavior"] as? String) ?? btn.behavior
        if let staticZone = j["staticZone"] as? [String: Any] {
            btn.staticLeft   = (staticZone["left"]   as? Double) ?? btn.staticLeft
            btn.staticTop    = (staticZone["top"]    as? Double) ?? btn.staticTop
            btn.staticRight  = (staticZone["right"]  as? Double) ?? btn.staticRight
            btn.staticBottom = (staticZone["bottom"] as? Double) ?? btn.staticBottom
        }
        if let dynamicZone = j["dynamicZone"] as? [String: Any] {
            btn.dynamicOffsetX = (dynamicZone["offsetX"] as? Double) ?? btn.dynamicOffsetX
            btn.dynamicOffsetY = (dynamicZone["offsetY"] as? Double) ?? btn.dynamicOffsetY
            btn.dynamicRadius  = (dynamicZone["radius"]  as? Double) ?? btn.dynamicRadius
        }
        // subRegions: not yet supported in iOS model; skip.
        return btn
    }

    // MARK: - Macro helpers

    private static func macroToJson(_ macro: KeyboardMacroButtonConfig) -> [String: Any] {
        return [
            "label":         macro.label,
            "modifiers":     macro.modifiers,
            "keyUsages":     macro.keyUsages,
            "layoutOffsetX": macro.layoutOffsetX,
            "layoutOffsetY": macro.layoutOffsetY,
            "layoutScaleX":  macro.layoutScaleX,
            "layoutScaleY":  macro.layoutScaleY,
        ]
    }

    private static func macroFromJson(_ j: [String: Any]) -> KeyboardMacroButtonConfig? {
        let label    = j["label"] as? String ?? ""
        let keyUsages = j["keyUsages"] as? [Int] ?? []
        guard !label.isEmpty, !keyUsages.isEmpty else { return nil }
        return KeyboardMacroButtonConfig(
            label:         label,
            modifiers:     j["modifiers"]     as? Int    ?? 0,
            keyUsages:     keyUsages,
            layoutOffsetX: j["layoutOffsetX"] as? Double ?? 0,
            layoutOffsetY: j["layoutOffsetY"] as? Double ?? 0,
            layoutScaleX:  j["layoutScaleX"]  as? Double ?? 1,
            layoutScaleY:  j["layoutScaleY"]  as? Double ?? 1
        )
    }

    // MARK: - Enum helpers

    private static func touchMode(from raw: String?) -> TouchMode? {
        switch raw?.uppercased() {
        case "TOUCH": return .touch
        case "MOUSE": return .mouse
        default:      return nil
        }
    }

    private static func zoneTypeName(_ z: ZoneType) -> String {
        switch z {
        case .staticZone: return "STATIC"
        case .dynamic:    return "DYNAMIC"
        }
    }

    private static func zoneType(from raw: String?) -> ZoneType? {
        switch raw?.uppercased() {
        case "STATIC":  return .staticZone
        case "DYNAMIC": return .dynamic
        default:        return nil
        }
    }

    private static func clickBehaviorName(_ b: ClickBehavior) -> String {
        switch b {
        case .momentary: return "MOMENTARY"
        case .latching:  return "LATCHING"
        }
    }

    private static func clickBehavior(from raw: String?) -> ClickBehavior? {
        switch raw?.uppercased() {
        case "MOMENTARY": return .momentary
        case "LATCHING":  return .latching
        default:          return nil
        }
    }

    private static func macroHost(from raw: String?) -> MacroHostDefaults? {
        switch raw?.uppercased() {
        case "WINDOWS": return .windows
        case "MAC":     return .mac
        default:        return nil
        }
    }
}
