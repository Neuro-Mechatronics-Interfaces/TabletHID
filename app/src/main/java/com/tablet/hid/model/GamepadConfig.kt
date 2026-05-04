package com.tablet.hid.model

enum class TriggerDragAxis { UP, DOWN, LEFT, RIGHT }

data class ButtonConfig(
    val enabled: Boolean = true,
    val behavior: ClickBehavior = ClickBehavior.MOMENTARY,
    val turbo: Boolean = false,
    val turboDurationMs: Int = 50,
    val turboIntervalMs: Int = 100,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    // Trigger-only fields (ignored for non-trigger buttons)
    val triggerTravelDp: Float = 150f,
    val triggerAxis: TriggerDragAxis = TriggerDragAxis.UP,
)

data class JoystickConfig(
    val enabled: Boolean = true,
    val deadzone: Float = 0.08f,
    val gain: Float = 1.0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
)

data class GamepadConfig(
    val btnA: ButtonConfig     = ButtonConfig(),
    val btnB: ButtonConfig     = ButtonConfig(),
    val btnX: ButtonConfig     = ButtonConfig(),
    val btnY: ButtonConfig     = ButtonConfig(),
    val btnLb: ButtonConfig    = ButtonConfig(),
    val btnRb: ButtonConfig    = ButtonConfig(),
    val btnLt: ButtonConfig    = ButtonConfig(),
    val btnRt: ButtonConfig    = ButtonConfig(),
    val btnBack: ButtonConfig  = ButtonConfig(),
    val btnStart: ButtonConfig = ButtonConfig(),
    val dpadUp: ButtonConfig    = ButtonConfig(),
    val dpadDown: ButtonConfig  = ButtonConfig(),
    val dpadLeft: ButtonConfig  = ButtonConfig(),
    val dpadRight: ButtonConfig = ButtonConfig(),
    val leftJoystick: JoystickConfig  = JoystickConfig(),
    val rightJoystick: JoystickConfig = JoystickConfig(),
)
