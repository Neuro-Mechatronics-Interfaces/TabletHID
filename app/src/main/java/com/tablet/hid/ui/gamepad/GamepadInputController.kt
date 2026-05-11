package com.tablet.hid.ui.gamepad

import android.content.res.Resources
import android.view.MotionEvent
import android.view.View
import com.tablet.hid.HidViewModel
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_A
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_B
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_BACK
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_LB
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_RB
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_START
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_X
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_Y
import com.tablet.hid.databinding.FragmentGamepadBinding
import com.tablet.hid.model.ButtonConfig
import com.tablet.hid.model.ClickBehavior
import com.tablet.hid.model.JoystickSide
import com.tablet.hid.model.TriggerDragAxis
import com.tablet.hid.util.HapticFeedback

class GamepadInputController(
    private val binding: FragmentGamepadBinding,
    private val viewModel: HidViewModel,
    private val resources: Resources,
    private val state: GamepadStateManager,
) {

    fun setupJoysticks() {
        binding.leftJoystick.listener = JoystickView.JoystickListener { nx, ny ->
            state.leftX = (nx * 32767).toInt(); state.leftY = (ny * 32767).toInt(); state.sendReport()
        }
        binding.rightJoystick.listener = JoystickView.JoystickListener { nx, ny ->
            state.rightX = (nx * 32767).toInt(); state.rightY = (ny * 32767).toInt(); state.sendReport()
        }
    }

    fun toggleSingleJoystickOutputSide() {
        val cfg = viewModel.gamepadConfig.value
        if (!cfg.singleJoystickMode) return
        val next = if (cfg.singleJoystickOutputSide == JoystickSide.LEFT) JoystickSide.RIGHT else JoystickSide.LEFT
        viewModel.updateGamepadConfig(cfg.copy(singleJoystickOutputSide = next))
        state.sendReport()
    }

    fun setupButtons() {
        val cfg = viewModel.gamepadConfig.value
        configuredButton(binding.btnA,    BTN_A,    cfg.btnA,    binding.btnA)
        configuredButton(binding.btnB,    BTN_B,    cfg.btnB,    binding.btnB)
        configuredButton(binding.btnX,    BTN_X,    cfg.btnX,    binding.btnX)
        configuredButton(binding.btnY,    BTN_Y,    cfg.btnY,    binding.btnY)
        configuredButton(binding.btnLb,   BTN_LB,   cfg.btnLb,   binding.btnLb)
        configuredButton(binding.btnRb,   BTN_RB,   cfg.btnRb,   binding.btnRb)
        configuredButton(binding.btnBack, BTN_BACK, cfg.btnBack, binding.btnBack)
        configuredButton(binding.btnStart,BTN_START,cfg.btnStart,binding.btnStart)

        triggerButton(binding.btnLt, cfg.btnLt, isLeft = true)
        triggerButton(binding.btnRt, cfg.btnRt, isLeft = false)
    }

    fun setupDpad() {
        dpadButton(binding.dpadUp,    { d -> state.dUp    = d; state.updateHat() })
        dpadButton(binding.dpadDown,  { d -> state.dDown  = d; state.updateHat() })
        dpadButton(binding.dpadLeft,  { d -> state.dLeft  = d; state.updateHat() })
        dpadButton(binding.dpadRight, { d -> state.dRight = d; state.updateHat() })
    }

    @Suppress("ClickableViewAccessibility")
    private fun configuredButton(v: View, bit: Int, cfg: ButtonConfig, indicator: View) {
        v.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    HapticFeedback.vibrate(v.context,
                        viewModel.gamepadConfig.value.vibrationIntensity)
                    when (cfg.behavior) {
                        ClickBehavior.MOMENTARY -> {
                            if (cfg.turbo) state.startTurbo(bit, cfg)
                            else { state.momentaryBits.add(bit); state.setVisualActive(indicator, bit, true) }
                        }
                        ClickBehavior.LATCHING -> {
                            if (state.latchedBits.contains(bit)) {
                                state.latchedBits.remove(bit)
                                state.turboJobs.remove(bit)?.cancel()
                                state.setVisualActive(indicator, bit, false)
                            } else {
                                state.latchedBits.add(bit)
                                if (cfg.turbo) state.startTurbo(bit, cfg)
                                else state.setVisualActive(indicator, bit, true)
                            }
                        }
                    }
                    state.sendReport(); true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (cfg.behavior == ClickBehavior.MOMENTARY) {
                        state.turboJobs.remove(bit)?.cancel()
                        state.momentaryBits.remove(bit)
                        state.setVisualActive(indicator, bit, false)
                        state.sendReport()
                    }
                    true
                }
                else -> false
            }
        }
    }

    @Suppress("ClickableViewAccessibility")
    private fun triggerButton(v: View, cfg: ButtonConfig, isLeft: Boolean) {
        var startX = 0f; var startY = 0f
        v.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY
                    if (cfg.behavior == ClickBehavior.LATCHING) {
                        val on = if (isLeft) state.leftTrigger == 0 else state.rightTrigger == 0
                        val trigVal = if (on) 255 else 0
                        if (isLeft) state.leftTrigger = trigVal else state.rightTrigger = trigVal
                        state.setTriggerLevel(v, trigVal / 255f)
                    } else {
                        if (isLeft) state.leftTrigger = 255 else state.rightTrigger = 255
                        state.setTriggerLevel(v, 1f)
                    }
                    state.sendReport(); true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (cfg.behavior == ClickBehavior.MOMENTARY) {
                        val travelPx = cfg.triggerTravelDp * resources.displayMetrics.density
                        val delta = when (cfg.triggerAxis) {
                            TriggerDragAxis.UP    -> startY - event.rawY
                            TriggerDragAxis.DOWN  -> event.rawY - startY
                            TriggerDragAxis.LEFT  -> startX - event.rawX
                            TriggerDragAxis.RIGHT -> event.rawX - startX
                        }
                        val ratio = (delta.coerceAtLeast(0f) / travelPx).coerceIn(0f, 1f)
                        val trigVal = (255 * (1f - ratio)).toInt()
                        if (isLeft) state.leftTrigger = trigVal else state.rightTrigger = trigVal
                        state.setTriggerLevel(v, trigVal / 255f)
                        state.sendReport()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (cfg.behavior == ClickBehavior.MOMENTARY) {
                        if (isLeft) state.leftTrigger = 0 else state.rightTrigger = 0
                        state.setTriggerLevel(v, 0f)
                        state.sendReport()
                    }
                    true
                }
                else -> false
            }
        }
    }

    @Suppress("ClickableViewAccessibility")
    private fun dpadButton(v: View, onChanged: (Boolean) -> Unit) {
        v.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    HapticFeedback.vibrate(v.context,
                        viewModel.gamepadConfig.value.vibrationIntensity)
                    state.setTriggerLevel(v, 1f); onChanged(true); true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    state.setTriggerLevel(v, 0f); onChanged(false); true
                }
                else -> false
            }
        }
    }
}
