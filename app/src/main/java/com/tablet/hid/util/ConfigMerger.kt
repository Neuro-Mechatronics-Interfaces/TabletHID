package com.tablet.hid.util

import com.tablet.hid.model.ButtonConfig
import com.tablet.hid.model.ButtonZoneConfig
import com.tablet.hid.model.GamepadConfig
import com.tablet.hid.model.JoystickConfig
import com.tablet.hid.model.TouchMouseConfig

object ConfigMerger {

    enum class GamepadSubset {
        GAMEPAD_CONTROL_LAYOUT,
        GAMEPAD_BUTTON_BEHAVIOR,
        GAMEPAD_JOYSTICK_SETTINGS,
        GAMEPAD_MACROS,
        GAMEPAD_LABELS,
        GAMEPAD_VIBRATION,
    }

    enum class TouchMouseSubset {
        TOUCH_ZONE_POSITIONS,
        TOUCH_SENSITIVITY,
        TOUCH_BUTTON_BEHAVIOR,
        TOUCH_MACROS,
    }

    fun mergeGamepad(
        target: GamepadConfig,
        source: GamepadConfig,
        subsets: Set<GamepadSubset>,
    ): GamepadConfig {
        var result = target
        for (subset in subsets) {
            result = when (subset) {
                GamepadSubset.GAMEPAD_CONTROL_LAYOUT -> applyControlLayout(result, source)
                GamepadSubset.GAMEPAD_BUTTON_BEHAVIOR -> applyButtonBehavior(result, source)
                GamepadSubset.GAMEPAD_JOYSTICK_SETTINGS -> applyJoystickSettings(result, source)
                GamepadSubset.GAMEPAD_MACROS -> result.copy(
                    macroButtons = source.macroButtons,
                    macroHostDefaults = source.macroHostDefaults,
                )
                GamepadSubset.GAMEPAD_LABELS -> result.copy(
                    customButtonLabels = source.customButtonLabels,
                )
                GamepadSubset.GAMEPAD_VIBRATION -> result.copy(
                    vibrationIntensity = source.vibrationIntensity,
                )
            }
        }
        return result
    }

    fun mergeTouchMouse(
        target: TouchMouseConfig,
        source: TouchMouseConfig,
        subsets: Set<TouchMouseSubset>,
    ): TouchMouseConfig {
        var result = target
        for (subset in subsets) {
            result = when (subset) {
                TouchMouseSubset.TOUCH_ZONE_POSITIONS -> applyZonePositions(result, source)
                TouchMouseSubset.TOUCH_SENSITIVITY -> applySensitivity(result, source)
                TouchMouseSubset.TOUCH_BUTTON_BEHAVIOR -> applyTouchButtonBehavior(result, source)
                TouchMouseSubset.TOUCH_MACROS -> result.copy(
                    macroButtons = source.macroButtons,
                    macroHostDefaults = source.macroHostDefaults,
                )
            }
        }
        return result
    }

    private fun applyControlLayout(target: GamepadConfig, source: GamepadConfig): GamepadConfig =
        target.copy(
            btnA     = applyLayoutToButton(target.btnA,     source.btnA),
            btnB     = applyLayoutToButton(target.btnB,     source.btnB),
            btnX     = applyLayoutToButton(target.btnX,     source.btnX),
            btnY     = applyLayoutToButton(target.btnY,     source.btnY),
            btnLb    = applyLayoutToButton(target.btnLb,    source.btnLb),
            btnRb    = applyLayoutToButton(target.btnRb,    source.btnRb),
            btnLt    = applyLayoutToButton(target.btnLt,    source.btnLt),
            btnRt    = applyLayoutToButton(target.btnRt,    source.btnRt),
            btnBack  = applyLayoutToButton(target.btnBack,  source.btnBack),
            btnStart = applyLayoutToButton(target.btnStart, source.btnStart),
            dpadUp    = applyLayoutToButton(target.dpadUp,    source.dpadUp),
            dpadDown  = applyLayoutToButton(target.dpadDown,  source.dpadDown),
            dpadLeft  = applyLayoutToButton(target.dpadLeft,  source.dpadLeft),
            dpadRight = applyLayoutToButton(target.dpadRight, source.dpadRight),
            leftJoystick  = applyLayoutToJoystick(target.leftJoystick,  source.leftJoystick),
            rightJoystick = applyLayoutToJoystick(target.rightJoystick, source.rightJoystick),
        )

    private fun applyButtonBehavior(target: GamepadConfig, source: GamepadConfig): GamepadConfig =
        target.copy(
            btnA     = applyBehaviorToButton(target.btnA,     source.btnA,     isTrigger = false),
            btnB     = applyBehaviorToButton(target.btnB,     source.btnB,     isTrigger = false),
            btnX     = applyBehaviorToButton(target.btnX,     source.btnX,     isTrigger = false),
            btnY     = applyBehaviorToButton(target.btnY,     source.btnY,     isTrigger = false),
            btnLb    = applyBehaviorToButton(target.btnLb,    source.btnLb,    isTrigger = false),
            btnRb    = applyBehaviorToButton(target.btnRb,    source.btnRb,    isTrigger = false),
            btnLt    = applyBehaviorToButton(target.btnLt,    source.btnLt,    isTrigger = true),
            btnRt    = applyBehaviorToButton(target.btnRt,    source.btnRt,    isTrigger = true),
            btnBack  = applyBehaviorToButton(target.btnBack,  source.btnBack,  isTrigger = false),
            btnStart = applyBehaviorToButton(target.btnStart, source.btnStart, isTrigger = false),
            dpadUp    = applyBehaviorToButton(target.dpadUp,    source.dpadUp,    isTrigger = false),
            dpadDown  = applyBehaviorToButton(target.dpadDown,  source.dpadDown,  isTrigger = false),
            dpadLeft  = applyBehaviorToButton(target.dpadLeft,  source.dpadLeft,  isTrigger = false),
            dpadRight = applyBehaviorToButton(target.dpadRight, source.dpadRight, isTrigger = false),
        )

    private fun applyJoystickSettings(target: GamepadConfig, source: GamepadConfig): GamepadConfig =
        target.copy(
            leftJoystick = target.leftJoystick.copy(
                deadzone = source.leftJoystick.deadzone,
                gain     = source.leftJoystick.gain,
            ),
            rightJoystick = target.rightJoystick.copy(
                deadzone = source.rightJoystick.deadzone,
                gain     = source.rightJoystick.gain,
            ),
            singleJoystickMode             = source.singleJoystickMode,
            singleJoystickSideToggleEnabled = source.singleJoystickSideToggleEnabled,
            singleJoystickOutputSide        = source.singleJoystickOutputSide,
        )

    private fun applyZonePositions(target: TouchMouseConfig, source: TouchMouseConfig): TouchMouseConfig =
        target.copy(
            leftButton  = applyZonePositionToButton(target.leftButton,  source.leftButton),
            rightButton = applyZonePositionToButton(target.rightButton, source.rightButton),
            sharedDynamicZone   = source.sharedDynamicZone,
            sharedDynamicOffsetX = source.sharedDynamicOffsetX,
            sharedDynamicOffsetY = source.sharedDynamicOffsetY,
            sharedDynamicRadius  = source.sharedDynamicRadius,
        )

    private fun applySensitivity(target: TouchMouseConfig, source: TouchMouseConfig): TouchMouseConfig =
        target.copy(
            sensitivity   = source.sensitivity,
            scrollEnabled = source.scrollEnabled,
            invertScroll  = source.invertScroll,
            sniperEnabled = source.sniperEnabled,
            sniperLeft    = source.sniperLeft,
            sniperTop     = source.sniperTop,
            sniperRight   = source.sniperRight,
            sniperBottom  = source.sniperBottom,
            sniperDivisor = source.sniperDivisor,
        )

    private fun applyTouchButtonBehavior(target: TouchMouseConfig, source: TouchMouseConfig): TouchMouseConfig =
        target.copy(
            leftButton  = target.leftButton.copy(
                enabled  = source.leftButton.enabled,
                behavior = source.leftButton.behavior,
            ),
            rightButton = target.rightButton.copy(
                enabled  = source.rightButton.enabled,
                behavior = source.rightButton.behavior,
            ),
        )

    private fun applyLayoutToButton(target: ButtonConfig, source: ButtonConfig): ButtonConfig =
        target.copy(
            offsetX = source.offsetX,
            offsetY = source.offsetY,
            scaleX  = source.scaleX,
            scaleY  = source.scaleY,
        )

    private fun applyBehaviorToButton(
        target: ButtonConfig,
        source: ButtonConfig,
        isTrigger: Boolean,
    ): ButtonConfig {
        var result = target.copy(
            enabled         = source.enabled,
            behavior        = source.behavior,
            turbo           = source.turbo,
            turboDurationMs = source.turboDurationMs,
            turboIntervalMs = source.turboIntervalMs,
        )
        if (isTrigger) {
            result = result.copy(
                triggerTravelDp = source.triggerTravelDp,
                triggerAxis     = source.triggerAxis,
            )
        }
        return result
    }

    private fun applyLayoutToJoystick(target: JoystickConfig, source: JoystickConfig): JoystickConfig =
        target.copy(
            offsetX = source.offsetX,
            offsetY = source.offsetY,
            scaleX  = source.scaleX,
            scaleY  = source.scaleY,
        )

    private fun applyZonePositionToButton(target: ButtonZoneConfig, source: ButtonZoneConfig): ButtonZoneConfig =
        target.copy(
            zoneType       = source.zoneType,
            staticLeft     = source.staticLeft,
            staticTop      = source.staticTop,
            staticRight    = source.staticRight,
            staticBottom   = source.staticBottom,
            dynamicOffsetX = source.dynamicOffsetX,
            dynamicOffsetY = source.dynamicOffsetY,
            dynamicRadius  = source.dynamicRadius,
            subRegions     = source.subRegions,
        )
}
