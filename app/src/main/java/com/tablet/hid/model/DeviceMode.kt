package com.tablet.hid.model

enum class DeviceMode(val displayName: String) {
    TOUCH_MOUSE("Touch Mouse"),
    GAMEPAD("Gamepad");

    // Both modes share one Bluetooth bond via the combined HID descriptor.
    val deviceName: String get() = "TabletHID"
}
