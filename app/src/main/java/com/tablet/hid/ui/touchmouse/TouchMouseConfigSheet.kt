package com.tablet.hid.ui.touchmouse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import com.tablet.hid.HidViewModel
import com.tablet.hid.R
import com.tablet.hid.databinding.SheetTouchMouseConfigBinding
import com.tablet.hid.model.ClickBehavior
import com.tablet.hid.model.TouchMode
import com.tablet.hid.model.TouchMouseConfig
import com.tablet.hid.model.ZoneType

class TouchMouseConfigSheet : BottomSheetDialogFragment() {

    private var _binding: SheetTouchMouseConfigBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HidViewModel by activityViewModels()

    // Fragment sets this to handle zone-edit requests
    var onZoneEditRequested: ((isLeft: Boolean) -> Unit)? = null

    // True while we are programmatically initialising controls (avoids feedback loops)
    private var initialising = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SheetTouchMouseConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyConfig(viewModel.touchMouseConfig.value)
        setupListeners()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Initialise controls from config (called once on start and after zone edit)
    // ─────────────────────────────────────────────────────────────────────────

    private fun applyConfig(cfg: TouchMouseConfig) {
        initialising = true

        binding.toggleMode.check(
            if (cfg.mode == TouchMode.TOUCH) R.id.btnTouch else R.id.btnMouse
        )
        binding.groupMouseSettings.isVisible = cfg.mode == TouchMode.MOUSE
        binding.sliderSensitivity.value = cfg.sensitivity.toFloat()
        binding.labelSensValue.text = cfg.sensitivity.toString()

        applyButtonConfig(cfg.leftButton, isLeft = true)
        applyButtonConfig(cfg.rightButton, isLeft = false)

        initialising = false
    }

    private fun applyButtonConfig(btn: com.tablet.hid.model.ButtonZoneConfig, isLeft: Boolean) {
        if (isLeft) {
            binding.switchLeft.isChecked = btn.enabled
            binding.configLeft.isVisible = btn.enabled
            binding.toggleLeftZone.check(
                if (btn.zoneType == ZoneType.STATIC) R.id.btnLeftStatic else R.id.btnLeftDynamic
            )
            binding.toggleLeftBehavior.check(
                if (btn.behavior == ClickBehavior.MOMENTARY) R.id.btnLeftMomentary else R.id.btnLeftLatching
            )
            setLeftZoneTypeVisibility(btn.zoneType)
            binding.sliderLeftOffsetX.value = btn.dynamicOffsetX.coerceIn(-1f, 1f)
            binding.sliderLeftOffsetY.value = btn.dynamicOffsetY.coerceIn(-1f, 1f)
            binding.sliderLeftRadius.value = btn.dynamicRadius.coerceIn(0.03f, 0.20f)
            updateLeftDynamicLabels()
        } else {
            binding.switchRight.isChecked = btn.enabled
            binding.configRight.isVisible = btn.enabled
            binding.toggleRightZone.check(
                if (btn.zoneType == ZoneType.STATIC) R.id.btnRightStatic else R.id.btnRightDynamic
            )
            binding.toggleRightBehavior.check(
                if (btn.behavior == ClickBehavior.MOMENTARY) R.id.btnRightMomentary else R.id.btnRightLatching
            )
            setRightZoneTypeVisibility(btn.zoneType)
            binding.sliderRightOffsetX.value = btn.dynamicOffsetX.coerceIn(-1f, 1f)
            binding.sliderRightOffsetY.value = btn.dynamicOffsetY.coerceIn(-1f, 1f)
            binding.sliderRightRadius.value = btn.dynamicRadius.coerceIn(0.03f, 0.20f)
            updateRightDynamicLabels()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Listener wiring
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupListeners() {
        // Mode toggle
        binding.toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || initialising) return@addOnButtonCheckedListener
            binding.groupMouseSettings.isVisible = checkedId == R.id.btnMouse
            pushConfig()
        }

        // Sensitivity
        binding.sliderSensitivity.addOnChangeListener { _, value, _ ->
            binding.labelSensValue.text = value.toInt().toString()
            if (!initialising) pushConfig()
        }

        // ── Left button ──────────────────────────────────────────────────
        binding.switchLeft.setOnCheckedChangeListener { _, checked ->
            binding.configLeft.isVisible = checked
            if (!initialising) pushConfig()
        }

        binding.toggleLeftZone.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || initialising) return@addOnButtonCheckedListener
            val type = if (checkedId == R.id.btnLeftStatic) ZoneType.STATIC else ZoneType.DYNAMIC
            setLeftZoneTypeVisibility(type)
            pushConfig()
        }

        binding.toggleLeftBehavior.addOnButtonCheckedListener { _, _, isChecked ->
            if (!isChecked || initialising) return@addOnButtonCheckedListener
            pushConfig()
        }

        binding.btnSetLeftZone.setOnClickListener {
            dismiss()
            onZoneEditRequested?.invoke(true)
        }

        val leftSliderListener = Slider.OnChangeListener { _, _, _ ->
            if (!initialising) { updateLeftDynamicLabels(); pushConfig() }
        }
        binding.sliderLeftOffsetX.addOnChangeListener(leftSliderListener)
        binding.sliderLeftOffsetY.addOnChangeListener(leftSliderListener)
        binding.sliderLeftRadius.addOnChangeListener(leftSliderListener)

        // ── Right button ─────────────────────────────────────────────────
        binding.switchRight.setOnCheckedChangeListener { _, checked ->
            binding.configRight.isVisible = checked
            if (!initialising) pushConfig()
        }

        binding.toggleRightZone.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || initialising) return@addOnButtonCheckedListener
            val type = if (checkedId == R.id.btnRightStatic) ZoneType.STATIC else ZoneType.DYNAMIC
            setRightZoneTypeVisibility(type)
            pushConfig()
        }

        binding.toggleRightBehavior.addOnButtonCheckedListener { _, _, isChecked ->
            if (!isChecked || initialising) return@addOnButtonCheckedListener
            pushConfig()
        }

        binding.btnSetRightZone.setOnClickListener {
            dismiss()
            onZoneEditRequested?.invoke(false)
        }

        val rightSliderListener = Slider.OnChangeListener { _, _, _ ->
            if (!initialising) { updateRightDynamicLabels(); pushConfig() }
        }
        binding.sliderRightOffsetX.addOnChangeListener(rightSliderListener)
        binding.sliderRightOffsetY.addOnChangeListener(rightSliderListener)
        binding.sliderRightRadius.addOnChangeListener(rightSliderListener)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun setLeftZoneTypeVisibility(type: ZoneType) {
        binding.btnSetLeftZone.isVisible = type == ZoneType.STATIC
        binding.groupDynamicLeft.isVisible = type == ZoneType.DYNAMIC
    }

    private fun setRightZoneTypeVisibility(type: ZoneType) {
        binding.btnSetRightZone.isVisible = type == ZoneType.STATIC
        binding.groupDynamicRight.isVisible = type == ZoneType.DYNAMIC
    }

    private fun updateLeftDynamicLabels() {
        binding.labelLeftOffsetX.text = formatOffset(binding.sliderLeftOffsetX.value)
        binding.labelLeftOffsetY.text = formatOffset(binding.sliderLeftOffsetY.value)
        binding.labelLeftRadius.text = "%.2f".format(binding.sliderLeftRadius.value)
    }

    private fun updateRightDynamicLabels() {
        binding.labelRightOffsetX.text = formatOffset(binding.sliderRightOffsetX.value)
        binding.labelRightOffsetY.text = formatOffset(binding.sliderRightOffsetY.value)
        binding.labelRightRadius.text = "%.2f".format(binding.sliderRightRadius.value)
    }

    private fun formatOffset(v: Float) =
        if (v >= 0) "+%.2f".format(v) else "%.2f".format(v)

    // ─────────────────────────────────────────────────────────────────────────
    // Build config from current UI state and push to ViewModel
    // ─────────────────────────────────────────────────────────────────────────

    private fun pushConfig() {
        val prev = viewModel.touchMouseConfig.value

        val mode = if (binding.toggleMode.checkedButtonId == R.id.btnTouch)
            TouchMode.TOUCH else TouchMode.MOUSE

        val leftZoneType = if (binding.toggleLeftZone.checkedButtonId == R.id.btnLeftStatic)
            ZoneType.STATIC else ZoneType.DYNAMIC
        val leftBehavior = if (binding.toggleLeftBehavior.checkedButtonId == R.id.btnLeftMomentary)
            ClickBehavior.MOMENTARY else ClickBehavior.LATCHING

        val rightZoneType = if (binding.toggleRightZone.checkedButtonId == R.id.btnRightStatic)
            ZoneType.STATIC else ZoneType.DYNAMIC
        val rightBehavior = if (binding.toggleRightBehavior.checkedButtonId == R.id.btnRightMomentary)
            ClickBehavior.MOMENTARY else ClickBehavior.LATCHING

        val newConfig = TouchMouseConfig(
            mode = mode,
            sensitivity = binding.sliderSensitivity.value.toInt(),
            leftButton = prev.leftButton.copy(
                enabled = binding.switchLeft.isChecked,
                zoneType = leftZoneType,
                behavior = leftBehavior,
                dynamicOffsetX = binding.sliderLeftOffsetX.value,
                dynamicOffsetY = binding.sliderLeftOffsetY.value,
                dynamicRadius = binding.sliderLeftRadius.value
            ),
            rightButton = prev.rightButton.copy(
                enabled = binding.switchRight.isChecked,
                zoneType = rightZoneType,
                behavior = rightBehavior,
                dynamicOffsetX = binding.sliderRightOffsetX.value,
                dynamicOffsetY = binding.sliderRightOffsetY.value,
                dynamicRadius = binding.sliderRightRadius.value
            )
        )
        viewModel.updateTouchMouseConfig(newConfig)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
