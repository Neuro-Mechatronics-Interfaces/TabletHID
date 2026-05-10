package com.tablet.hid.model

object KeyUsageConstants {
    const val MOD_LEFT_CTRL   = 0x01
    const val MOD_LEFT_SHIFT  = 0x02
    const val MOD_LEFT_ALT    = 0x04
    const val MOD_LEFT_GUI    = 0x08  // Win / Cmd
    const val MOD_RIGHT_CTRL  = 0x10
    const val MOD_RIGHT_SHIFT = 0x20
    const val MOD_RIGHT_ALT   = 0x40
    const val MOD_RIGHT_GUI   = 0x80

    val COMMON_KEYS: List<Pair<String, Int>> = listOf(
        "A" to 0x04, "B" to 0x05, "C" to 0x06, "D" to 0x07,
        "E" to 0x08, "F" to 0x09, "G" to 0x0A, "H" to 0x0B,
        "I" to 0x0C, "J" to 0x0D, "K" to 0x0E, "L" to 0x0F,
        "M" to 0x10, "N" to 0x11, "O" to 0x12, "P" to 0x13,
        "Q" to 0x14, "R" to 0x15, "S" to 0x16, "T" to 0x17,
        "U" to 0x18, "V" to 0x19, "W" to 0x1A, "X" to 0x1B,
        "Y" to 0x1C, "Z" to 0x1D,
        "1" to 0x1E, "2" to 0x1F, "3" to 0x20, "4" to 0x21,
        "5" to 0x22, "6" to 0x23, "7" to 0x24, "8" to 0x25,
        "9" to 0x26, "0" to 0x27,
        "Enter" to 0x28, "Esc" to 0x29, "Backspace" to 0x2A,
        "Tab" to 0x2B, "Space" to 0x2C,
        "F1" to 0x3A, "F2" to 0x3B, "F3" to 0x3C, "F4" to 0x3D,
        "F5" to 0x3E, "F6" to 0x3F, "F7" to 0x40, "F8" to 0x41,
        "F9" to 0x42, "F10" to 0x43, "F11" to 0x44, "F12" to 0x45,
        "Print Screen" to 0x46, "Delete" to 0x4C, "Home" to 0x4A,
        "End" to 0x4D, "Page Up" to 0x4B, "Page Down" to 0x4E,
        "Left" to 0x50, "Right" to 0x4F, "Up" to 0x52, "Down" to 0x51,
    )
}
