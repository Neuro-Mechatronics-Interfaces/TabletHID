package com.tablet.hid.model

enum class TouchMode { TOUCH, MOUSE }
enum class ZoneType { STATIC, DYNAMIC }
enum class ClickBehavior { MOMENTARY, LATCHING }

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
    val dynamicRadius: Float = 0.07f
)

data class TouchMouseConfig(
    val mode: TouchMode = TouchMode.TOUCH,
    val sensitivity: Int = 5,
    val leftButton: ButtonZoneConfig = ButtonZoneConfig(
        staticLeft = 0f, staticTop = 0.75f, staticRight = 0.25f, staticBottom = 1f,
        dynamicOffsetX = -0.15f   // left of primary pointer in dynamic mode
    ),
    val rightButton: ButtonZoneConfig = ButtonZoneConfig(
        staticLeft = 0.75f, staticTop = 0.75f, staticRight = 1f, staticBottom = 1f,
        dynamicOffsetX = +0.15f // (default) — right of primary pointer
    )
)
