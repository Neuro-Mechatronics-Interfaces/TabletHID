package com.tablet.hid.util

import android.content.Context
import android.graphics.Color

/**
 * A named color palette for gaming UI elements: touch zones, macro buttons, and the
 * layout-sheet preview indicator. All RGB values are stored without alpha; alpha is
 * applied contextually (idle / active / tint).
 */
data class UiPalette(
    val name: String,
    val leftRgb: Int,
    val rightRgb: Int,
    val sniperRgb: Int,
    val macroRgb: Int,
) {
    val leftIdle     get() = withAlpha(100, leftRgb)
    val leftActive   get() = withAlpha(215, leftRgb)
    val rightIdle    get() = withAlpha(100, rightRgb)
    val rightActive  get() = withAlpha(215, rightRgb)
    val sniperIdle   get() = withAlpha(100, sniperRgb)
    val sniperActive get() = withAlpha(215, sniperRgb)
    val sharedIdle   get() = withAlpha(105, leftRgb)
    val sharedActive get() = withAlpha(215, leftRgb)

    /** 80% alpha ARGB for the ButtonLayoutSheet preview dot. */
    val previewDotArgb  get() = withAlpha(204, macroRgb)

    /** Macro button background tint (63% alpha). */
    val macroButtonTint get() = withAlpha(160, macroRgb)

    fun leftWithAlpha(a: Int)   = withAlpha(a, leftRgb)
    fun rightWithAlpha(a: Int)  = withAlpha(a, rightRgb)
    fun sniperWithAlpha(a: Int) = withAlpha(a, sniperRgb)

    companion object {
        val ENTRIES = listOf(
            UiPalette("Default",    0x1E8CFF, 0xFF7814, 0x00BE78, 0x5C6BC0),
            UiPalette("Neon",       0x00CFFF, 0xFF00FF, 0x00FF88, 0x00E5FF),
            UiPalette("Fire",       0xFF4444, 0xFF8C00, 0xFFD700, 0xFF6B35),
            UiPalette("Ice",        0x87CEEB, 0xB0E2FF, 0xE0FFFF, 0x4FC3F7),
            UiPalette("Monochrome", 0xFFFFFF, 0xAAAAAA, 0xCCCCCC, 0xFFFFFF),
        )
    }
}

private fun withAlpha(alpha: Int, rgb: Int): Int =
    Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb))

object UiPaletteStore {
    private const val PREFS = "tablet_hid_appearance"
    private const val KEY   = "ui_palette_index"

    fun getIndex(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0)

    fun setIndex(context: Context, index: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY, index).apply()
    }

    fun get(context: Context): UiPalette =
        UiPalette.ENTRIES.getOrElse(getIndex(context)) { UiPalette.ENTRIES[0] }
}
