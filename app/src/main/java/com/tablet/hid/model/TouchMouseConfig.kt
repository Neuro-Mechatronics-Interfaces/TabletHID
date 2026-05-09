package com.tablet.hid.model

enum class TouchMode { TOUCH, MOUSE }
enum class ZoneType { STATIC, DYNAMIC }
enum class ClickBehavior { MOMENTARY, LATCHING }
enum class MouseButton(val bit: Int) { LEFT(1), RIGHT(2), MIDDLE(4) }
enum class MacroHostDefaults { WINDOWS, MAC }

data class KeyboardMacroButtonConfig(
    val label: String,
    val modifiers: Int,
    val keyUsages: List<Int>,
)

object KeyboardMacroPresets {
    const val MOD_LEFT_CONTROL = 0x01
    const val MOD_LEFT_ALT = 0x04
    const val MOD_LEFT_GUI = 0x08
    const val KEY_S = 0x16
    const val KEY_TAB = 0x2B

    val windowsDefaults = listOf(
        KeyboardMacroButtonConfig("Alt+Tab", MOD_LEFT_ALT, listOf(KEY_TAB)),
        KeyboardMacroButtonConfig("Ctrl+S", MOD_LEFT_CONTROL, listOf(KEY_S)),
    )

    val macDefaults = listOf(
        KeyboardMacroButtonConfig("Cmd+Tab", MOD_LEFT_GUI, listOf(KEY_TAB)),
        KeyboardMacroButtonConfig("Cmd+S", MOD_LEFT_GUI, listOf(KEY_S)),
    )

    fun defaultsFor(host: MacroHostDefaults): List<KeyboardMacroButtonConfig> =
        if (host == MacroHostDefaults.MAC) macDefaults else windowsDefaults
}

data class TouchMouseSubRegionConfig(
    val enabled: Boolean = true,
    val zoneType: ZoneType = ZoneType.STATIC,
    // Static zone: fractional screen coordinates in [0, 1]
    val staticLeft: Float = 0f,
    val staticTop: Float = 0f,
    val staticRight: Float = 0f,
    val staticBottom: Float = 0f,
    // Dynamic zone: offset from primary pointer as fraction of min(width, height)
    val dynamicOffsetX: Float = 0f,
    val dynamicOffsetY: Float = 0f,
    val dynamicRadius: Float = 0.07f,
    // Groundwork for future keyboard/mouse-modified sub-region actions.
    val keyboardModifiers: Int = 0,
    val alternateMouseButton: MouseButton? = null,
)

data class ButtonZoneConfig(
    val enabled: Boolean = false,
    val zoneType: ZoneType = ZoneType.STATIC,
    val behavior: ClickBehavior = ClickBehavior.MOMENTARY,
    // Static zone: fractional screen coordinates in [0, 1]
    val staticLeft: Float = 0f,
    val staticTop: Float = 0.75f,
    val staticRight: Float = 0.25f,
    val staticBottom: Float = 1f,
    // Dynamic zone: offset from primary pointer as fraction of min(width, height)
    val dynamicOffsetX: Float = 0.15f,
    val dynamicOffsetY: Float = 0f,
    val dynamicRadius: Float = 0.07f,
    val subRegions: List<TouchMouseSubRegionConfig> = emptyList(),
)

data class TouchMouseConfig(
    val mode: TouchMode = TouchMode.TOUCH,
    val sensitivity: Int = 5,
    val scrollEnabled: Boolean = true,
    val invertScroll: Boolean = false,
    val sharedDynamicZone: Boolean = false,
    val sharedDynamicOffsetX: Float = 0f,
    val sharedDynamicOffsetY: Float = 0.18f,
    val sharedDynamicRadius: Float = 0.08f,
    val leftButton: ButtonZoneConfig = ButtonZoneConfig(
        staticLeft = 0f, staticTop = 0.75f, staticRight = 0.25f, staticBottom = 1f,
        dynamicOffsetX = -0.15f   // left of primary pointer in dynamic mode
    ),
    val rightButton: ButtonZoneConfig = ButtonZoneConfig(
        staticLeft = 0.75f, staticTop = 0.75f, staticRight = 1f, staticBottom = 1f,
        dynamicOffsetX = +0.15f // (default) — right of primary pointer
    ),
    val macroHostDefaults: MacroHostDefaults = MacroHostDefaults.WINDOWS,
    val macroButtons: List<KeyboardMacroButtonConfig> = emptyList(),
)
