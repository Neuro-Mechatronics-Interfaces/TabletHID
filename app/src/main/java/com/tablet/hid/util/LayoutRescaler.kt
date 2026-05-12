package com.tablet.hid.util

import android.content.Context
import android.util.DisplayMetrics
import com.tablet.hid.model.ButtonConfig
import com.tablet.hid.model.GamepadConfig
import com.tablet.hid.model.JoystickConfig
import com.tablet.hid.model.normalizeLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object LayoutRescaler {

    /** Converts physical screen dimensions to landscape canvas size in dp: (canvasW, canvasH). */
    fun canvasDimsFromScreenPx(widthPx: Int, heightPx: Int, densityDpi: Int): Pair<Float, Float> {
        val density = densityDpi / 160f
        val wDp = widthPx / density
        val hDp = heightPx / density
        return max(wDp, hDp) to min(wDp, hDp)
    }

    fun canvasDimsFromMetrics(metrics: DisplayMetrics): Pair<Float, Float> =
        canvasDimsFromScreenPx(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)

    /**
     * Returns a rescaled copy of [config] whose layout offsets fit the [tgtW × tgtH] canvas.
     * Returns [config] unchanged when the canvases are within 1 dp of each other.
     * scaleX/Y are not touched — they are device-independent multipliers on natural element size.
     * Macro button offsets are scaled proportionally to the canvas ratio.
     */
    fun rescaleGamepad(
        context: Context,
        config: GamepadConfig,
        srcW: Float, srcH: Float,
        tgtW: Float, tgtH: Float,
    ): GamepadConfig {
        if (abs(srcW - tgtW) < 1f && abs(srcH - tgtH) < 1f) return config

        val srcLayout = GamepadLayoutResolver.resolveLayout(context, srcW, srcH)
        val tgtLayout = GamepadLayoutResolver.resolveLayout(context, tgtW, tgtH)
        val xRatio = tgtW / srcW
        val yRatio = tgtH / srcH

        fun rescaleButton(key: String, btn: ButtonConfig): ButtonConfig {
            val src = srcLayout[key] ?: return btn
            val tgt = tgtLayout[key] ?: return btn
            val cx = (src.left + src.w / 2f + btn.offsetX) * xRatio
            val cy = (src.top + src.h / 2f + btn.offsetY) * yRatio
            return btn.copy(
                offsetX = cx - (tgt.left + tgt.w / 2f),
                offsetY = cy - (tgt.top + tgt.h / 2f),
            )
        }

        fun rescaleJoystick(key: String, joy: JoystickConfig): JoystickConfig {
            val src = srcLayout[key] ?: return joy
            val tgt = tgtLayout[key] ?: return joy
            val cx = (src.left + src.w / 2f + joy.offsetX) * xRatio
            val cy = (src.top + src.h / 2f + joy.offsetY) * yRatio
            return joy.copy(
                offsetX = cx - (tgt.left + tgt.w / 2f),
                offsetY = cy - (tgt.top + tgt.h / 2f),
            )
        }

        return config.copy(
            btnA      = rescaleButton("a",           config.btnA),
            btnB      = rescaleButton("b",           config.btnB),
            btnX      = rescaleButton("x",           config.btnX),
            btnY      = rescaleButton("y",           config.btnY),
            btnLb     = rescaleButton("lb",          config.btnLb),
            btnRb     = rescaleButton("rb",          config.btnRb),
            btnLt     = rescaleButton("lt",          config.btnLt),
            btnRt     = rescaleButton("rt",          config.btnRt),
            btnBack   = rescaleButton("back",        config.btnBack),
            btnStart  = rescaleButton("start",       config.btnStart),
            dpadUp    = rescaleButton("dpadUp",      config.dpadUp),
            dpadDown  = rescaleButton("dpadDown",    config.dpadDown),
            dpadLeft  = rescaleButton("dpadLeft",    config.dpadLeft),
            dpadRight = rescaleButton("dpadRight",   config.dpadRight),
            leftJoystick  = rescaleJoystick("leftJoystick",  config.leftJoystick),
            rightJoystick = rescaleJoystick("rightJoystick", config.rightJoystick),
            macroButtons = config.macroButtons.map { macro ->
                macro.copy(
                    layoutOffsetX = macro.layoutOffsetX * xRatio,
                    layoutOffsetY = macro.layoutOffsetY * yRatio,
                )
            },
        ).normalizeLayout()
    }
}
