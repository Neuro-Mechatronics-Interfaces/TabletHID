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
