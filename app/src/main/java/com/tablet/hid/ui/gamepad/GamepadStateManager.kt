package com.tablet.hid.ui.gamepad

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.tablet.hid.HidViewModel
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_A
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_B
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_BACK
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_LB
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_RB
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_START
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_X
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_Y
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_E
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_N
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_NE
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_NW
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_NONE
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_S
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_SE
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_SW
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_W
import com.tablet.hid.model.ButtonConfig
import com.tablet.hid.model.GamepadConfig
import com.tablet.hid.model.JoystickSide
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GamepadStateManager(
    private val viewModel: HidViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val config: () -> GamepadConfig,
    private val buttonViews: () -> Map<Int, View>,
) {

    var leftX = 0; var leftY = 0
    var rightX = 0; var rightY = 0
    var leftTrigger = 0; var rightTrigger = 0
    var hat = HAT_NONE

    var dUp = false; var dDown = false
    var dLeft = false; var dRight = false

    val latchedBits = mutableSetOf<Int>()
    val momentaryBits = mutableSetOf<Int>()

    val turboJobs = mutableMapOf<Int, Job>()

    private val baseColor = mapOf(
        BTN_A to 0xFF22C55E.toInt(), BTN_B to 0xFFEF4444.toInt(),
        BTN_X to 0xFF3B82F6.toInt(), BTN_Y to 0xFFF5C518.toInt(),
    )
    private val activeColor = mapOf(
        BTN_A to 0xFF86EFAC.toInt(), BTN_B to 0xFFFCA5A5.toInt(),
        BTN_X to 0xFF93C5FD.toInt(), BTN_Y to 0xFFFFE082.toInt(),
    )

    fun sendReport() {
        var bits = 0
        momentaryBits.forEach { bits = bits or (1 shl it) }
        latchedBits.forEach   { bits = bits or (1 shl it) }
        val cfg = config()
        bits = bits and enabledButtonMask(cfg)

        val reportHat = enabledHat(cfg)
        val reportLeftX: Int
        val reportLeftY: Int
        val reportRightX: Int
        val reportRightY: Int
        if (cfg.singleJoystickMode) {
            val activeX = if (cfg.leftJoystick.enabled) leftX else 0
            val activeY = if (cfg.leftJoystick.enabled) leftY else 0
            if (cfg.singleJoystickOutputSide == JoystickSide.LEFT) {
                reportLeftX = activeX
                reportLeftY = activeY
                reportRightX = 0
                reportRightY = 0
            } else {
                reportLeftX = 0
                reportLeftY = 0
                reportRightX = activeX
                reportRightY = activeY
            }
        } else {
            reportLeftX = if (cfg.leftJoystick.enabled) leftX else 0
            reportLeftY = if (cfg.leftJoystick.enabled) leftY else 0
            reportRightX = if (cfg.rightJoystick.enabled) rightX else 0
            reportRightY = if (cfg.rightJoystick.enabled) rightY else 0
        }
        viewModel.sendGamepadReport(
            leftX = reportLeftX, leftY = reportLeftY,
            rightX = reportRightX, rightY = reportRightY,
            leftTrigger = if (cfg.btnLt.enabled) leftTrigger else 0,
            rightTrigger = if (cfg.btnRt.enabled) rightTrigger else 0,
            buttons = bits, hat = reportHat
        )
    }

    fun enabledButtonMask(cfg: GamepadConfig): Int {
        var mask = 0
        if (cfg.btnA.enabled) mask = mask or (1 shl BTN_A)
        if (cfg.btnB.enabled) mask = mask or (1 shl BTN_B)
        if (cfg.btnX.enabled) mask = mask or (1 shl BTN_X)
        if (cfg.btnY.enabled) mask = mask or (1 shl BTN_Y)
        if (cfg.btnLb.enabled) mask = mask or (1 shl BTN_LB)
        if (cfg.btnRb.enabled) mask = mask or (1 shl BTN_RB)
        if (cfg.btnBack.enabled) mask = mask or (1 shl BTN_BACK)
        if (cfg.btnStart.enabled) mask = mask or (1 shl BTN_START)
        return mask
    }

    fun enabledHat(cfg: GamepadConfig): Int {
        val up = dUp && cfg.dpadUp.enabled
        val down = dDown && cfg.dpadDown.enabled
        val left = dLeft && cfg.dpadLeft.enabled
        val right = dRight && cfg.dpadRight.enabled
        return when {
            up && right -> HAT_NE
            down && right -> HAT_SE
            down && left -> HAT_SW
            up && left -> HAT_NW
            up -> HAT_N
            right -> HAT_E
            down -> HAT_S
            left -> HAT_W
            else -> HAT_NONE
        }
    }

    fun setVisualActive(v: View, bit: Int, active: Boolean) {
        val color = if (active) activeColor[bit] ?: Color.argb(0xFF, 255, 255, 255)
                    else        baseColor[bit]   ?: Color.argb(0x33, 255, 255, 255)
        v.backgroundTintList = ColorStateList.valueOf(color)
    }

    fun setTriggerLevel(v: View, level: Float) {
        val alpha = (0x33 + ((0xFF - 0x33) * level)).toInt().coerceIn(0x33, 0xFF)
        v.backgroundTintList = ColorStateList.valueOf(Color.argb(alpha, 255, 255, 255))
    }

    fun startTurbo(bit: Int, cfg: ButtonConfig) {
        turboJobs[bit]?.cancel()
        turboJobs[bit] = lifecycleOwner.lifecycleScope.launch {
            val indicator = buttonViews()[bit] ?: return@launch
            while (true) {
                momentaryBits.add(bit)
                setVisualActive(indicator, bit, true)
                sendReport()
                delay(cfg.turboDurationMs.toLong())
                momentaryBits.remove(bit)
                setVisualActive(indicator, bit, false)
                sendReport()
                delay(cfg.turboIntervalMs.toLong())
            }
        }
    }

    fun updateHat() {
        hat = when {
            dUp && dRight  -> HAT_NE; dDown && dRight -> HAT_SE
            dDown && dLeft -> HAT_SW; dUp && dLeft    -> HAT_NW
            dUp            -> HAT_N;  dRight          -> HAT_E
            dDown          -> HAT_S;  dLeft           -> HAT_W
            else           -> HAT_NONE
        }
        sendReport()
    }

    fun reset() {
        turboJobs.values.forEach { it.cancel() }
        turboJobs.clear()
        momentaryBits.clear()
        latchedBits.clear()
    }
}
