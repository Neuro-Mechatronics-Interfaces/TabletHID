import Foundation

enum TriggerDragAxis: String, Codable, CaseIterable, Identifiable {
    case up
    case down
    case left
    case right

    var id: String { rawValue }
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
