import Foundation

/// Converts GamepadConfig to and from the canonical JSON dictionary format defined in
/// spec/server-schema.md. Field names and nesting structure are identical to
/// GamepadConfigSerializer.kt on Android.
enum GamepadConfigSerializer {

    // MARK: - To canonical JSON

    static func toCanonicalJson(_ config: GamepadConfig) -> [String: Any] {
        var root: [String: Any] = [:]
        var buttons: [String: Any] = [:]
        buttons["a"]         = buttonToJson(config.btnA,     isTrigger: false)
        buttons["b"]         = buttonToJson(config.btnB,     isTrigger: false)
        buttons["x"]         = buttonToJson(config.btnX,     isTrigger: false)
        buttons["y"]         = buttonToJson(config.btnY,     isTrigger: false)
        buttons["lb"]        = buttonToJson(config.btnLB,    isTrigger: false)
        buttons["rb"]        = buttonToJson(config.btnRB,    isTrigger: false)
        buttons["lt"]        = buttonToJson(config.btnLT,    isTrigger: true)
        buttons["rt"]        = buttonToJson(config.btnRT,    isTrigger: true)
        buttons["back"]      = buttonToJson(config.btnBack,  isTrigger: false)
        buttons["start"]     = buttonToJson(config.btnStart, isTrigger: false)
        buttons["dpadUp"]    = buttonToJson(config.dpadUp,    isTrigger: false)
        buttons["dpadDown"]  = buttonToJson(config.dpadDown,  isTrigger: false)
        buttons["dpadLeft"]  = buttonToJson(config.dpadLeft,  isTrigger: false)
        buttons["dpadRight"] = buttonToJson(config.dpadRight, isTrigger: false)
        root["buttons"] = buttons
        root["leftJoystick"]  = joystickToJson(config.leftJoystick)
        root["rightJoystick"] = joystickToJson(config.rightJoystick)
        root["singleJoystickMode"]             = config.singleJoystickMode
        root["singleJoystickSideToggleEnabled"] = config.singleJoystickSideToggleEnabled
        root["singleJoystickOutputSide"] = config.singleJoystickOutputSide.rawValue.uppercased()
        root["macroHostDefaults"] = config.macroHostDefaults.rawValue.uppercased()
        root["macroButtons"] = config.macroButtons.map { macroToJson($0) }
        root["vibrationIntensity"] = config.vibrationIntensity.rawValue.uppercased()
        var labels: [String: String] = [:]
        config.customButtonLabels.forEach { labels[$0.key] = $0.value }
        root["customButtonLabels"] = labels
        return root
    }

    // MARK: - From canonical JSON

    static func fromCanonicalJson(_ json: [String: Any]) -> GamepadConfig {
        let buttons = json["buttons"] as? [String: Any] ?? [:]
        let leftJs  = json["leftJoystick"]  as? [String: Any]
        let rightJs = json["rightJoystick"] as? [String: Any]
        var config = GamepadConfig()
        config.btnA     = buttonFromJson(buttons["a"]         as? [String: Any], isTrigger: false)
        config.btnB     = buttonFromJson(buttons["b"]         as? [String: Any], isTrigger: false)
        config.btnX     = buttonFromJson(buttons["x"]         as? [String: Any], isTrigger: false)
        config.btnY     = buttonFromJson(buttons["y"]         as? [String: Any], isTrigger: false)
        config.btnLB    = buttonFromJson(buttons["lb"]        as? [String: Any], isTrigger: false)
        config.btnRB    = buttonFromJson(buttons["rb"]        as? [String: Any], isTrigger: false)
        config.btnLT    = buttonFromJson(buttons["lt"]        as? [String: Any], isTrigger: true)
        config.btnRT    = buttonFromJson(buttons["rt"]        as? [String: Any], isTrigger: true)
        config.btnBack  = buttonFromJson(buttons["back"]      as? [String: Any], isTrigger: false)
        config.btnStart = buttonFromJson(buttons["start"]     as? [String: Any], isTrigger: false)
        config.dpadUp    = buttonFromJson(buttons["dpadUp"]    as? [String: Any], isTrigger: false)
        config.dpadDown  = buttonFromJson(buttons["dpadDown"]  as? [String: Any], isTrigger: false)
        config.dpadLeft  = buttonFromJson(buttons["dpadLeft"]  as? [String: Any], isTrigger: false)
        config.dpadRight = buttonFromJson(buttons["dpadRight"] as? [String: Any], isTrigger: false)
        config.leftJoystick  = joystickFromJson(leftJs)
        config.rightJoystick = joystickFromJson(rightJs)
        config.singleJoystickMode             = json["singleJoystickMode"] as? Bool ?? false
        config.singleJoystickSideToggleEnabled = json["singleJoystickSideToggleEnabled"] as? Bool ?? false
        config.singleJoystickOutputSide = joystickSide(from: json["singleJoystickOutputSide"] as? String) ?? .left
        config.macroHostDefaults = macroHost(from: json["macroHostDefaults"] as? String) ?? .windows
        config.macroButtons = (json["macroButtons"] as? [[String: Any]] ?? []).compactMap { macroFromJson($0) }
        config.vibrationIntensity = vibrationIntensity(from: json["vibrationIntensity"] as? String) ?? .off
        config.customButtonLabels = json["customButtonLabels"] as? [String: String] ?? [:]
        return config
    }

    // MARK: - ButtonConfig helpers

    private static func buttonToJson(_ btn: ButtonConfig, isTrigger: Bool) -> [String: Any] {
        let j: [String: Any] = [
            "enabled":         btn.enabled,
            "behavior":        btn.behavior.rawValue.uppercased(),
            "turbo":           btn.turbo,
            "turboDurationMs": btn.turboDurationMs,
            "turboIntervalMs": btn.turboIntervalMs,
            "offsetX":         btn.offsetX,
            "offsetY":         btn.offsetY,
            "scaleX":          btn.scaleX,
            "scaleY":          btn.scaleY,
            "triggerTravelDp": btn.triggerTravel,
            "triggerAxis":     btn.triggerAxis.rawValue.uppercased(),
        ]
        return j
    }

    private static func buttonFromJson(_ j: [String: Any]?, isTrigger: Bool) -> ButtonConfig {
        var btn = ButtonConfig()
        guard let j = j else { return btn }
        btn.enabled         = j["enabled"]         as? Bool   ?? btn.enabled
        btn.behavior        = clickBehavior(from: j["behavior"] as? String) ?? btn.behavior
        btn.turbo           = j["turbo"]           as? Bool   ?? btn.turbo
        btn.turboDurationMs = j["turboDurationMs"] as? Int    ?? btn.turboDurationMs
        btn.turboIntervalMs = j["turboIntervalMs"] as? Int    ?? btn.turboIntervalMs
        btn.offsetX         = (j["offsetX"] as? Double) ?? btn.offsetX
        btn.offsetY         = (j["offsetY"] as? Double) ?? btn.offsetY
        btn.scaleX          = (j["scaleX"]  as? Double) ?? btn.scaleX
        btn.scaleY          = (j["scaleY"]  as? Double) ?? btn.scaleY
        if isTrigger {
            btn.triggerTravel = (j["triggerTravelDp"] as? Double) ?? btn.triggerTravel
            btn.triggerAxis   = triggerAxis(from: j["triggerAxis"] as? String) ?? btn.triggerAxis
        }
        return btn
    }

    // MARK: - JoystickConfig helpers

    private static func joystickToJson(_ js: JoystickConfig) -> [String: Any] {
        return [
            "enabled":  js.enabled,
            "deadzone": js.deadzone,
            "gain":     js.gain,
            "offsetX":  js.offsetX,
            "offsetY":  js.offsetY,
            "scaleX":   js.scaleX,
            "scaleY":   js.scaleY,
        ]
    }

    private static func joystickFromJson(_ j: [String: Any]?) -> JoystickConfig {
        var js = JoystickConfig()
        guard let j = j else { return js }
        js.enabled  = j["enabled"]  as? Bool   ?? js.enabled
        js.deadzone = (j["deadzone"] as? Double) ?? js.deadzone
        js.gain     = (j["gain"]     as? Double) ?? js.gain
        js.offsetX  = (j["offsetX"]  as? Double) ?? js.offsetX
        js.offsetY  = (j["offsetY"]  as? Double) ?? js.offsetY
        js.scaleX   = (j["scaleX"]   as? Double) ?? js.scaleX
        js.scaleY   = (j["scaleY"]   as? Double) ?? js.scaleY
        return js
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

    // MARK: - Enum parsing (server uses UPPER_CASE, iOS model uses lower camelCase)

    private static func clickBehavior(from raw: String?) -> ClickBehavior? {
        switch raw?.uppercased() {
        case "MOMENTARY": return .momentary
        case "LATCHING":  return .latching
        default:          return nil
        }
    }

    private static func joystickSide(from raw: String?) -> JoystickSide? {
        switch raw?.uppercased() {
        case "LEFT":  return .left
        case "RIGHT": return .right
        default:      return nil
        }
    }

    private static func triggerAxis(from raw: String?) -> TriggerDragAxis? {
        switch raw?.uppercased() {
        case "UP":    return .up
        case "DOWN":  return .down
        case "LEFT":  return .left
        case "RIGHT": return .right
        default:      return nil
        }
    }

    private static func macroHost(from raw: String?) -> MacroHostDefaults? {
        switch raw?.uppercased() {
        case "WINDOWS": return .windows
        case "MAC":     return .mac
        default:        return nil
        }
    }

    private static func vibrationIntensity(from raw: String?) -> VibrationIntensity? {
        switch raw?.uppercased() {
        case "OFF":    return .off
        case "LIGHT":  return .light
        case "MEDIUM": return .medium
        case "STRONG": return .strong
        default:       return nil
        }
    }
}
