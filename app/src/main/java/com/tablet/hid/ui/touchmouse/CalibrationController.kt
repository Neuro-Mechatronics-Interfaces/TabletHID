package com.tablet.hid.ui.touchmouse

import android.view.MotionEvent
import androidx.core.view.isVisible
import com.tablet.hid.HidViewModel
import com.tablet.hid.R
import com.tablet.hid.databinding.FragmentTouchMouseBinding
import com.tablet.hid.model.ZoneType
import kotlin.math.sqrt

class CalibrationController(
    private val binding: FragmentTouchMouseBinding,
    private val viewModel: HidViewModel,
) {

    private enum class CalibrationPhase { NONE, WAITING_PRIMARY, WAITING_LEFT, WAITING_RIGHT }

    private var calibrationPhase = CalibrationPhase.NONE
    private var calPrimaryX = 0f
    private var calPrimaryY = 0f
    private var calLeftX = 0f
    private var calLeftY = 0f

    val isActive: Boolean get() = calibrationPhase != CalibrationPhase.NONE

    fun startCalibration() {
        calibrationPhase = CalibrationPhase.WAITING_PRIMARY
        binding.calibrationOverlay.isVisible = true
        binding.labelCalibrationHint.setText(R.string.calibrate_hint_primary)
    }

    fun cancelCalibration() {
        calibrationPhase = CalibrationPhase.NONE
        calPrimaryX = 0f; calPrimaryY = 0f
        calLeftX = 0f; calLeftY = 0f
        binding.calibrationOverlay.isVisible = false
    }

    fun handleCalibrationTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (calibrationPhase == CalibrationPhase.WAITING_PRIMARY) {
                    calPrimaryX = event.x; calPrimaryY = event.y
                    calibrationPhase = CalibrationPhase.WAITING_LEFT
                    binding.labelCalibrationHint.setText(R.string.calibrate_hint_left)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val x = event.getX(idx); val y = event.getY(idx)
                when (calibrationPhase) {
                    CalibrationPhase.WAITING_LEFT -> {
                        calLeftX = x; calLeftY = y
                        calibrationPhase = CalibrationPhase.WAITING_RIGHT
                        binding.labelCalibrationHint.setText(R.string.calibrate_hint_right)
                    }
                    CalibrationPhase.WAITING_RIGHT -> completeCalibration(x, y)
                    else -> {}
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (calibrationPhase == CalibrationPhase.WAITING_LEFT ||
                    calibrationPhase == CalibrationPhase.WAITING_RIGHT) {
                    calibrationPhase = CalibrationPhase.WAITING_PRIMARY
                    calPrimaryX = 0f; calPrimaryY = 0f
                    calLeftX = 0f; calLeftY = 0f
                    binding.labelCalibrationHint.setText(R.string.calibrate_hint_primary)
                }
            }
        }
        return true
    }

    private fun completeCalibration(rightX: Float, rightY: Float) {
        val overlay = binding.touchZoneOverlay
        val minDim = minOf(overlay.width, overlay.height).toFloat()
        if (minDim <= 0f) { cancelCalibration(); return }

        fun Float.roundToStep(step: Float) = (kotlin.math.round(this / step) * step)

        fun derive(clickX: Float, clickY: Float): Triple<Float, Float, Float> {
            val ox = ((clickX - calPrimaryX) / minDim).coerceIn(-1f, 1f)
            val oy = ((clickY - calPrimaryY) / minDim).coerceIn(-1f, 1f)
            val dx = clickX - calPrimaryX; val dy = clickY - calPrimaryY
            val radius = (sqrt((dx * dx + dy * dy).toDouble()).toFloat() * 0.45f / minDim)
                .coerceIn(0.04f, 0.15f)
            return Triple(
                ox.roundToStep(0.05f).coerceIn(-1f, 1f),
                oy.roundToStep(0.05f).coerceIn(-1f, 1f),
                radius.roundToStep(0.01f).coerceIn(0.03f, 0.20f)
            )
        }

        val (lox, loy, lr) = derive(calLeftX, calLeftY)
        val (rox, roy, rr) = derive(rightX, rightY)

        val prev = viewModel.touchMouseConfig.value
        viewModel.updateTouchMouseConfig(prev.copy(
            leftButton = prev.leftButton.copy(
                enabled = true,
                zoneType = ZoneType.DYNAMIC,
                dynamicOffsetX = lox,
                dynamicOffsetY = loy,
                dynamicRadius = lr
            ),
            rightButton = prev.rightButton.copy(
                enabled = true,
                zoneType = ZoneType.DYNAMIC,
                dynamicOffsetX = rox,
                dynamicOffsetY = roy,
                dynamicRadius = rr
            )
        ))
        cancelCalibration()
    }
}
