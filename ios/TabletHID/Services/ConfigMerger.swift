import Foundation

/// Mirrors ConfigMerger.kt exactly — same enum names, same field-level mappings.
///
/// Returns a **new** config object; never mutates `target` or `source`.
/// Merging a GamepadConfig source into a TouchMouseConfig target (or vice versa)
/// is a programming error; callers must guard against mode mismatch before calling.

// MARK: - Subset enums

enum GamepadSubset: CaseIterable {
    case controlLayout
    case buttonBehavior
    case joystickSettings
    case macros
    case labels
    case vibration
}

enum TouchMouseSubset: CaseIterable {
    case zonePositions
    case sensitivity
    case buttonBehavior
    case macros
}

// MARK: - ConfigMerger

enum ConfigMerger {

    // MARK: - Gamepad merge

    static func mergeGamepad(
        target: GamepadConfig,
        source: GamepadConfig,
        subsets: Set<GamepadSubset>
    ) -> GamepadConfig {
        var result = target
        for subset in subsets {
            switch subset {
            case .controlLayout:
                result = applyControlLayout(target: result, source: source)
            case .buttonBehavior:
                result = applyButtonBehavior(target: result, source: source)
            case .joystickSettings:
                result = applyJoystickSettings(target: result, source: source)
            case .macros:
                result.macroButtons = source.macroButtons
                result.macroHostDefaults = source.macroHostDefaults
            case .labels:
                result.customButtonLabels = source.customButtonLabels
            case .vibration:
                result.vibrationIntensity = source.vibrationIntensity
            }
        }
        return result
    }

    // MARK: - Touch Mouse merge

    static func mergeTouchMouse(
        target: TouchMouseConfig,
        source: TouchMouseConfig,
        subsets: Set<TouchMouseSubset>
    ) -> TouchMouseConfig {
        var result = target
        for subset in subsets {
            switch subset {
            case .zonePositions:
                result = applyZonePositions(target: result, source: source)
            case .sensitivity:
                result = applySensitivity(target: result, source: source)
            case .buttonBehavior:
                result = applyTouchButtonBehavior(target: result, source: source)
            case .macros:
                result.macroButtons = source.macroButtons
                result.macroHostDefaults = source.macroHostDefaults
            }
        }
        return result
    }

    // MARK: - Gamepad private helpers

    private static func applyControlLayout(target: GamepadConfig, source: GamepadConfig) -> GamepadConfig {
        var result = target
        result.btnA     = applyLayoutToButton(target: result.btnA,     source: source.btnA)
        result.btnB     = applyLayoutToButton(target: result.btnB,     source: source.btnB)
        result.btnX     = applyLayoutToButton(target: result.btnX,     source: source.btnX)
        result.btnY     = applyLayoutToButton(target: result.btnY,     source: source.btnY)
        result.btnLB    = applyLayoutToButton(target: result.btnLB,    source: source.btnLB)
        result.btnRB    = applyLayoutToButton(target: result.btnRB,    source: source.btnRB)
        result.btnLT    = applyLayoutToButton(target: result.btnLT,    source: source.btnLT)
        result.btnRT    = applyLayoutToButton(target: result.btnRT,    source: source.btnRT)
        result.btnBack  = applyLayoutToButton(target: result.btnBack,  source: source.btnBack)
        result.btnStart = applyLayoutToButton(target: result.btnStart, source: source.btnStart)
        result.dpadUp    = applyLayoutToButton(target: result.dpadUp,    source: source.dpadUp)
        result.dpadDown  = applyLayoutToButton(target: result.dpadDown,  source: source.dpadDown)
        result.dpadLeft  = applyLayoutToButton(target: result.dpadLeft,  source: source.dpadLeft)
        result.dpadRight = applyLayoutToButton(target: result.dpadRight, source: source.dpadRight)
        result.leftJoystick  = applyLayoutToJoystick(target: result.leftJoystick,  source: source.leftJoystick)
        result.rightJoystick = applyLayoutToJoystick(target: result.rightJoystick, source: source.rightJoystick)
        return result
    }

    private static func applyButtonBehavior(target: GamepadConfig, source: GamepadConfig) -> GamepadConfig {
        var result = target
        result.btnA     = applyBehaviorToButton(target: result.btnA,     source: source.btnA,     isTrigger: false)
        result.btnB     = applyBehaviorToButton(target: result.btnB,     source: source.btnB,     isTrigger: false)
        result.btnX     = applyBehaviorToButton(target: result.btnX,     source: source.btnX,     isTrigger: false)
        result.btnY     = applyBehaviorToButton(target: result.btnY,     source: source.btnY,     isTrigger: false)
        result.btnLB    = applyBehaviorToButton(target: result.btnLB,    source: source.btnLB,    isTrigger: false)
        result.btnRB    = applyBehaviorToButton(target: result.btnRB,    source: source.btnRB,    isTrigger: false)
        result.btnLT    = applyBehaviorToButton(target: result.btnLT,    source: source.btnLT,    isTrigger: true)
        result.btnRT    = applyBehaviorToButton(target: result.btnRT,    source: source.btnRT,    isTrigger: true)
        result.btnBack  = applyBehaviorToButton(target: result.btnBack,  source: source.btnBack,  isTrigger: false)
        result.btnStart = applyBehaviorToButton(target: result.btnStart, source: source.btnStart, isTrigger: false)
        result.dpadUp    = applyBehaviorToButton(target: result.dpadUp,    source: source.dpadUp,    isTrigger: false)
        result.dpadDown  = applyBehaviorToButton(target: result.dpadDown,  source: source.dpadDown,  isTrigger: false)
        result.dpadLeft  = applyBehaviorToButton(target: result.dpadLeft,  source: source.dpadLeft,  isTrigger: false)
        result.dpadRight = applyBehaviorToButton(target: result.dpadRight, source: source.dpadRight, isTrigger: false)
        result.orientationPreference = source.orientationPreference
        return result
    }

    private static func applyJoystickSettings(target: GamepadConfig, source: GamepadConfig) -> GamepadConfig {
        var result = target
        result.leftJoystick.deadzone  = source.leftJoystick.deadzone
        result.leftJoystick.gain      = source.leftJoystick.gain
        result.rightJoystick.deadzone = source.rightJoystick.deadzone
        result.rightJoystick.gain     = source.rightJoystick.gain
        result.singleJoystickMode              = source.singleJoystickMode
        result.singleJoystickSideToggleEnabled = source.singleJoystickSideToggleEnabled
        result.singleJoystickOutputSide        = source.singleJoystickOutputSide
        return result
    }

    // MARK: - Touch Mouse private helpers

    private static func applyZonePositions(target: TouchMouseConfig, source: TouchMouseConfig) -> TouchMouseConfig {
        var result = target
        result.leftButton  = applyZonePositionToButton(target: result.leftButton,  source: source.leftButton)
        result.rightButton = applyZonePositionToButton(target: result.rightButton, source: source.rightButton)
        result.sharedDynamicZone   = source.sharedDynamicZone
        result.sharedDynamicOffsetX = source.sharedDynamicOffsetX
        result.sharedDynamicOffsetY = source.sharedDynamicOffsetY
        result.sharedDynamicRadius  = source.sharedDynamicRadius
        return result
    }

    private static func applySensitivity(target: TouchMouseConfig, source: TouchMouseConfig) -> TouchMouseConfig {
        var result = target
        result.sensitivity   = source.sensitivity
        result.scrollEnabled = source.scrollEnabled
        result.invertScroll  = source.invertScroll
        // sniperEnabled / sniper fields are not yet stored in iOS TouchMouseConfig;
        // these subset fields are covered for future parity.
        return result
    }

    private static func applyTouchButtonBehavior(target: TouchMouseConfig, source: TouchMouseConfig) -> TouchMouseConfig {
        var result = target
        result.leftButton.enabled  = source.leftButton.enabled
        result.leftButton.behavior = source.leftButton.behavior
        result.rightButton.enabled  = source.rightButton.enabled
        result.rightButton.behavior = source.rightButton.behavior
        return result
    }

    // MARK: - Field-level helpers

    private static func applyLayoutToButton(target: ButtonConfig, source: ButtonConfig) -> ButtonConfig {
        var result = target
        result.offsetX = source.offsetX
        result.offsetY = source.offsetY
        result.scaleX  = source.scaleX
        result.scaleY  = source.scaleY
        return result
    }

    private static func applyBehaviorToButton(
        target: ButtonConfig,
        source: ButtonConfig,
        isTrigger: Bool
    ) -> ButtonConfig {
        var result = target
        result.enabled         = source.enabled
        result.behavior        = source.behavior
        result.turbo           = source.turbo
        result.turboDurationMs = source.turboDurationMs
        result.turboIntervalMs = source.turboIntervalMs
        if isTrigger {
            result.triggerTravel = source.triggerTravel
            result.triggerAxis   = source.triggerAxis
        }
        return result
    }

    private static func applyLayoutToJoystick(target: JoystickConfig, source: JoystickConfig) -> JoystickConfig {
        var result = target
        result.offsetX = source.offsetX
        result.offsetY = source.offsetY
        result.scaleX  = source.scaleX
        result.scaleY  = source.scaleY
        return result
    }

    private static func applyZonePositionToButton(
        target: ButtonZoneConfig,
        source: ButtonZoneConfig
    ) -> ButtonZoneConfig {
        var result = target
        result.zoneType       = source.zoneType
        result.staticLeft     = source.staticLeft
        result.staticTop      = source.staticTop
        result.staticRight    = source.staticRight
        result.staticBottom   = source.staticBottom
        result.dynamicOffsetX = source.dynamicOffsetX
        result.dynamicOffsetY = source.dynamicOffsetY
        result.dynamicRadius  = source.dynamicRadius
        // subRegions not yet in iOS model; skip.
        return result
    }
}
