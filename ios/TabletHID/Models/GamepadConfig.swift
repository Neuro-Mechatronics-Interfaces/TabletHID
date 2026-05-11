import Foundation

enum TriggerDragAxis: String, Codable, CaseIterable, Identifiable {
    case up
    case down
    case left
    case right

    var id: String { rawValue }
}

enum JoystickSide: String, Codable, CaseIterable, Identifiable {
    case left
    case right

    var id: String { rawValue }
    var label: String { self == .left ? "Left" : "Right" }
}

enum VibrationIntensity: String, Codable, CaseIterable, Identifiable {
    case off
    case light
    case medium
    case strong

    var id: String { rawValue }
    var label: String { rawValue.capitalized }
}

struct ButtonConfig: Codable, Equatable {
    var enabled = true
    var behavior = ClickBehavior.momentary
    var turbo = false
    var turboDurationMs = 50
    var turboIntervalMs = 100
    var offsetX: Double = 0
    var offsetY: Double = 0
    var scaleX: Double = 1
    var scaleY: Double = 1
    var triggerTravel: Double = 150
    var triggerAxis = TriggerDragAxis.up
}

struct JoystickConfig: Codable, Equatable {
    var enabled = true
    var deadzone: Double = 0.08
    var gain: Double = 1.0
    var offsetX: Double = 0
    var offsetY: Double = 0
    var scaleX: Double = 1
    var scaleY: Double = 1
}

struct GamepadConfig: Codable, Equatable {
    var btnA = ButtonConfig()
    var btnB = ButtonConfig()
    var btnX = ButtonConfig()
    var btnY = ButtonConfig()
    var btnLB = ButtonConfig()
    var btnRB = ButtonConfig()
    var btnLT = ButtonConfig()
    var btnRT = ButtonConfig()
    var btnBack = ButtonConfig()
    var btnStart = ButtonConfig()
    var dpadUp = ButtonConfig()
    var dpadDown = ButtonConfig()
    var dpadLeft = ButtonConfig()
    var dpadRight = ButtonConfig()
    var leftJoystick = JoystickConfig()
    var rightJoystick = JoystickConfig()
    var singleJoystickMode = false
    var singleJoystickSideToggleEnabled = false
    var singleJoystickOutputSide = JoystickSide.left
    var macroHostDefaults = MacroHostDefaults.windows
    var macroButtons: [KeyboardMacroButtonConfig] = []
    var vibrationIntensity = VibrationIntensity.off
    var customButtonLabels: [String: String] = [:]

    init() {}

    enum CodingKeys: String, CodingKey {
        case btnA, btnB, btnX, btnY, btnLB, btnRB, btnLT, btnRT, btnBack, btnStart
        case dpadUp, dpadDown, dpadLeft, dpadRight, leftJoystick, rightJoystick
        case singleJoystickMode, singleJoystickSideToggleEnabled, singleJoystickOutputSide
        case macroHostDefaults, macroButtons, vibrationIntensity, customButtonLabels
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        let defaults = GamepadConfig()
        btnA = try values.decodeIfPresent(ButtonConfig.self, forKey: .btnA) ?? defaults.btnA
        btnB = try values.decodeIfPresent(ButtonConfig.self, forKey: .btnB) ?? defaults.btnB
        btnX = try values.decodeIfPresent(ButtonConfig.self, forKey: .btnX) ?? defaults.btnX
        btnY = try values.decodeIfPresent(ButtonConfig.self, forKey: .btnY) ?? defaults.btnY
        btnLB = try values.decodeIfPresent(ButtonConfig.self, forKey: .btnLB) ?? defaults.btnLB
        btnRB = try values.decodeIfPresent(ButtonConfig.self, forKey: .btnRB) ?? defaults.btnRB
        btnLT = try values.decodeIfPresent(ButtonConfig.self, forKey: .btnLT) ?? defaults.btnLT
        btnRT = try values.decodeIfPresent(ButtonConfig.self, forKey: .btnRT) ?? defaults.btnRT
        btnBack = try values.decodeIfPresent(ButtonConfig.self, forKey: .btnBack) ?? defaults.btnBack
        btnStart = try values.decodeIfPresent(ButtonConfig.self, forKey: .btnStart) ?? defaults.btnStart
        dpadUp = try values.decodeIfPresent(ButtonConfig.self, forKey: .dpadUp) ?? defaults.dpadUp
        dpadDown = try values.decodeIfPresent(ButtonConfig.self, forKey: .dpadDown) ?? defaults.dpadDown
        dpadLeft = try values.decodeIfPresent(ButtonConfig.self, forKey: .dpadLeft) ?? defaults.dpadLeft
        dpadRight = try values.decodeIfPresent(ButtonConfig.self, forKey: .dpadRight) ?? defaults.dpadRight
        leftJoystick = try values.decodeIfPresent(JoystickConfig.self, forKey: .leftJoystick) ?? defaults.leftJoystick
        rightJoystick = try values.decodeIfPresent(JoystickConfig.self, forKey: .rightJoystick) ?? defaults.rightJoystick
        singleJoystickMode = try values.decodeIfPresent(Bool.self, forKey: .singleJoystickMode) ?? defaults.singleJoystickMode
        singleJoystickSideToggleEnabled = try values.decodeIfPresent(Bool.self, forKey: .singleJoystickSideToggleEnabled) ?? defaults.singleJoystickSideToggleEnabled
        singleJoystickOutputSide = try values.decodeIfPresent(JoystickSide.self, forKey: .singleJoystickOutputSide) ?? defaults.singleJoystickOutputSide
        macroHostDefaults = try values.decodeIfPresent(MacroHostDefaults.self, forKey: .macroHostDefaults) ?? defaults.macroHostDefaults
        macroButtons = try values.decodeIfPresent([KeyboardMacroButtonConfig].self, forKey: .macroButtons) ?? defaults.macroButtons
        vibrationIntensity = try values.decodeIfPresent(VibrationIntensity.self, forKey: .vibrationIntensity) ?? defaults.vibrationIntensity
        customButtonLabels = try values.decodeIfPresent([String: String].self, forKey: .customButtonLabels) ?? defaults.customButtonLabels
    }

    func withResetLayout() -> GamepadConfig {
        func r(_ b: ButtonConfig) -> ButtonConfig {
            var b = b; b.offsetX = 0; b.offsetY = 0; b.scaleX = 1; b.scaleY = 1; return b
        }
        func rj(_ j: JoystickConfig) -> JoystickConfig {
            var j = j; j.offsetX = 0; j.offsetY = 0; j.scaleX = 1; j.scaleY = 1; return j
        }
        var c = self
        c.btnA = r(c.btnA); c.btnB = r(c.btnB); c.btnX = r(c.btnX); c.btnY = r(c.btnY)
        c.btnLB = r(c.btnLB); c.btnRB = r(c.btnRB); c.btnLT = r(c.btnLT); c.btnRT = r(c.btnRT)
        c.btnBack = r(c.btnBack); c.btnStart = r(c.btnStart)
        c.dpadUp = r(c.dpadUp); c.dpadDown = r(c.dpadDown)
        c.dpadLeft = r(c.dpadLeft); c.dpadRight = r(c.dpadRight)
        c.leftJoystick = rj(c.leftJoystick); c.rightJoystick = rj(c.rightJoystick)
        c.macroButtons = c.macroButtons.map {
            KeyboardMacroButtonConfig(
                id: $0.id,
                label: $0.label,
                modifiers: $0.modifiers,
                keyUsages: $0.keyUsages
            )
        }
        return c
    }

    static func defaultForProfile(_ profile: Profile) -> GamepadConfig {
        var config = GamepadConfig()
        if profile.key == Profile.accessBasic.key {
            config.btnLT.enabled = false
            config.btnRT.enabled = false
            config.dpadUp.enabled = false
            config.dpadDown.enabled = false
            config.dpadLeft.enabled = false
            config.dpadRight.enabled = false
        }
        if profile.key == Profile.accessAdvanced.key {
            config.btnA.behavior = .latching
            config.btnB.behavior = .latching
            config.btnX.behavior = .latching
            config.btnY.behavior = .latching
            config.btnLB.behavior = .latching
            config.btnRB.behavior = .latching
        }
        return config
    }
}
