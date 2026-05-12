package com.tablet.hid.model

import kotlin.math.round

enum class TriggerDragAxis { UP, DOWN, LEFT, RIGHT }

enum class JoystickSide { LEFT, RIGHT }

enum class VibrationIntensity { OFF, LIGHT, MEDIUM, STRONG }

enum class OrientationPreference { SYSTEM, LANDSCAPE, PORTRAIT }

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
    val singleJoystickMode: Boolean = false,
    val singleJoystickSideToggleEnabled: Boolean = false,
    val singleJoystickOutputSide: JoystickSide = JoystickSide.LEFT,
    val macroHostDefaults: MacroHostDefaults = MacroHostDefaults.WINDOWS,
    val macroButtons: List<KeyboardMacroButtonConfig> = emptyList(),
    val vibrationIntensity: VibrationIntensity = VibrationIntensity.OFF,
    val customButtonLabels: Map<String, String> = emptyMap(),
    val singleJoystickSideBtn: ButtonConfig = ButtonConfig(),
    val orientationPreference: OrientationPreference = OrientationPreference.SYSTEM,
)

// Rounds all layout fields to the granularity enforced by the ButtonLayoutSheet sliders:
//   offsetX/Y → nearest 2 dp  (slider stepSize=2, range -400..400)
//   scaleX/Y  → nearest 0.1×  (slider stepSize=0.1, range 0.3..3.0)
// Call this after any operation that produces arbitrary-precision floats (rescaling, JSON import).
private fun roundOffset(v: Float) = (round(v / 2f) * 2f).coerceIn(-400f, 400f)
private fun roundScale(v: Float)  = (round(v * 10f) / 10f).coerceIn(0.3f, 3.0f)

fun ButtonConfig.normalizeLayout() = copy(
    offsetX = roundOffset(offsetX),
    offsetY = roundOffset(offsetY),
    scaleX  = roundScale(scaleX),
    scaleY  = roundScale(scaleY),
)

fun JoystickConfig.normalizeLayout() = copy(
    offsetX = roundOffset(offsetX),
    offsetY = roundOffset(offsetY),
    scaleX  = roundScale(scaleX),
    scaleY  = roundScale(scaleY),
)

fun KeyboardMacroButtonConfig.normalizeLayout() = copy(
    layoutOffsetX = roundOffset(layoutOffsetX),
    layoutOffsetY = roundOffset(layoutOffsetY),
    layoutScaleX  = roundScale(layoutScaleX),
    layoutScaleY  = roundScale(layoutScaleY),
)

fun GamepadConfig.normalizeLayout() = copy(
    btnA          = btnA.normalizeLayout(),
    btnB          = btnB.normalizeLayout(),
    btnX          = btnX.normalizeLayout(),
    btnY          = btnY.normalizeLayout(),
    btnLb         = btnLb.normalizeLayout(),
    btnRb         = btnRb.normalizeLayout(),
    btnLt         = btnLt.normalizeLayout(),
    btnRt         = btnRt.normalizeLayout(),
    btnBack       = btnBack.normalizeLayout(),
    btnStart      = btnStart.normalizeLayout(),
    dpadUp        = dpadUp.normalizeLayout(),
    dpadDown      = dpadDown.normalizeLayout(),
    dpadLeft      = dpadLeft.normalizeLayout(),
    dpadRight     = dpadRight.normalizeLayout(),
    leftJoystick  = leftJoystick.normalizeLayout(),
    rightJoystick = rightJoystick.normalizeLayout(),
    singleJoystickSideBtn = singleJoystickSideBtn.normalizeLayout(),
    macroButtons  = macroButtons.map { it.normalizeLayout() },
)

fun TouchMouseConfig.normalizeLayout() = copy(
    macroButtons = macroButtons.map { it.normalizeLayout() },
)
