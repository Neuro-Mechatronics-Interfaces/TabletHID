package com.tablet.hid.ui.touchmouse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.tablet.hid.HidViewModel
import com.tablet.hid.R
import com.tablet.hid.databinding.SheetTouchMouseConfigBinding
import com.tablet.hid.model.ClickBehavior
import com.tablet.hid.model.KeyboardMacroButtonConfig
import com.tablet.hid.model.KeyboardMacroPresets
import com.tablet.hid.model.MacroHostDefaults
import com.tablet.hid.model.TouchMode
import com.tablet.hid.ui.macro.CustomMacroEditorDialog
import com.tablet.hid.model.TouchMouseConfig
import com.tablet.hid.model.ZoneType

class TouchMouseConfigSheet : BottomSheetDialogFragment() {

    private var _binding: SheetTouchMouseConfigBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HidViewModel by activityViewModels()

    // Fragment sets this to handle zone-edit requests
    var onZoneEditRequested: ((isLeft: Boolean) -> Unit)? = null
    var onSubRegionEditRequested: ((isLeft: Boolean, keyboardModifiers: Int) -> Unit)? = null
    var onCalibrationRequested: (() -> Unit)? = null
    var onSniperEditRequested: (() -> Unit)? = null
    var onMacroEditRequested: (() -> Unit)? = null

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
        rebuildMacroList(viewModel.touchMouseConfig.value.macroButtons)
        binding.btnAddCustomMacro.setOnClickListener {
            val dialog = CustomMacroEditorDialog()
            dialog.onMacroCreated = CustomMacroEditorDialog.OnMacroCreated { macro ->
                val updated = viewModel.touchMouseConfig.value.let {
                    it.copy(macroButtons = it.macroButtons + macro)
                }
                viewModel.updateTouchMouseConfig(updated)
                rebuildMacroList(updated.macroButtons)
            }
            dialog.show(parentFragmentManager, "custom_macro")
        }
        binding.btnEditMacroLayout.setOnClickListener {
            onMacroEditRequested?.invoke()
            dismiss()
        }
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
        binding.switchScrollEnabled.isChecked = cfg.scrollEnabled
        binding.groupScrollOptions.isVisible = cfg.scrollEnabled
        binding.switchInvertScroll.isChecked = cfg.invertScroll
        binding.toggleMacroDefaults.check(
            if (cfg.macroHostDefaults == MacroHostDefaults.MAC) R.id.btnMacroMac else R.id.btnMacroWindows
        )
        binding.switchSharedDynamic.isChecked = cfg.sharedDynamicZone
        binding.groupSharedDynamic.isVisible = cfg.sharedDynamicZone
        binding.sliderSharedOffsetX.value = cfg.sharedDynamicOffsetX.snapToStep(0.05f).coerceIn(-1f, 1f)
        binding.sliderSharedOffsetY.value = cfg.sharedDynamicOffsetY.snapToStep(0.05f).coerceIn(-1f, 1f)
        binding.sliderSharedRadius.value = cfg.sharedDynamicRadius.snapToStep(0.01f).coerceIn(0.03f, 0.20f)
        updateSharedDynamicLabels()

        applyButtonConfig(cfg.leftButton, isLeft = true)
        applyButtonConfig(cfg.rightButton, isLeft = false)

        binding.switchSniperZone.isChecked = cfg.sniperEnabled
        binding.groupSniperConfig.isVisible = cfg.sniperEnabled
        binding.toggleSniperDivisor.check(
            when (cfg.sniperDivisor) {
                2f   -> R.id.btnSniper2x
                8f   -> R.id.btnSniper8x
                else -> R.id.btnSniper4x
            }
        )

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
            binding.sliderLeftOffsetX.value = btn.dynamicOffsetX.snapToStep(0.05f).coerceIn(-1f, 1f)
            binding.sliderLeftOffsetY.value = btn.dynamicOffsetY.snapToStep(0.05f).coerceIn(-1f, 1f)
            binding.sliderLeftRadius.value  = btn.dynamicRadius.snapToStep(0.01f).coerceIn(0.03f, 0.20f)
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
            binding.sliderRightOffsetX.value = btn.dynamicOffsetX.snapToStep(0.05f).coerceIn(-1f, 1f)
            binding.sliderRightOffsetY.value = btn.dynamicOffsetY.snapToStep(0.05f).coerceIn(-1f, 1f)
            binding.sliderRightRadius.value  = btn.dynamicRadius.snapToStep(0.01f).coerceIn(0.03f, 0.20f)
            updateRightDynamicLabels()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Listener wiring
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupListeners() {
        binding.switchScrollEnabled.setOnCheckedChangeListener { _, checked ->
            binding.groupScrollOptions.isVisible = checked
            if (!initialising) pushConfig()
        }

        binding.switchInvertScroll.setOnCheckedChangeListener { _, _ ->
            if (!initialising) pushConfig()
        }

        binding.toggleMacroDefaults.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || initialising) return@addOnButtonCheckedListener
            val host = if (checkedId == R.id.btnMacroMac) MacroHostDefaults.MAC else MacroHostDefaults.WINDOWS
            val prev = viewModel.touchMouseConfig.value
            viewModel.updateTouchMouseConfig(prev.copy(macroHostDefaults = host))
        }

        binding.btnAddMacroDefaults.setOnClickListener {
            val prev = viewModel.touchMouseConfig.value
            val host = if (binding.toggleMacroDefaults.checkedButtonId == R.id.btnMacroMac) {
                MacroHostDefaults.MAC
            } else {
                MacroHostDefaults.WINDOWS
            }
            val merged = (prev.macroButtons + KeyboardMacroPresets.defaultsFor(host)).distinctBy {
                "${it.modifiers}:${it.keyUsages.joinToString(",")}"
            }
            viewModel.updateTouchMouseConfig(prev.copy(macroHostDefaults = host, macroButtons = merged))
            rebuildMacroList(merged)
        }

        binding.btnClearMacros.setOnClickListener {
            val prev = viewModel.touchMouseConfig.value
            viewModel.updateTouchMouseConfig(prev.copy(macroButtons = emptyList()))
            rebuildMacroList(emptyList())
        }

        binding.switchSharedDynamic.setOnCheckedChangeListener { _, checked ->
            binding.groupSharedDynamic.isVisible = checked
            refreshDynamicGroupVisibility()
            if (!initialising) pushConfig()
        }

        val sharedSliderListener = Slider.OnChangeListener { _, _, _ ->
            if (!initialising) { updateSharedDynamicLabels(); pushConfig() }
        }
        binding.sliderSharedOffsetX.addOnChangeListener(sharedSliderListener)
        binding.sliderSharedOffsetY.addOnChangeListener(sharedSliderListener)
        binding.sliderSharedRadius.addOnChangeListener(sharedSliderListener)

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

        binding.btnAutoCalibrate.setOnClickListener {
            dismiss()
            onCalibrationRequested?.invoke()
        }

        binding.btnAddLeftSubRegion.setOnClickListener {
            dismiss()
            onSubRegionEditRequested?.invoke(true, 0)
        }
        binding.btnAddRightSubRegion.setOnClickListener {
            dismiss()
            onSubRegionEditRequested?.invoke(false, 0)
        }
        binding.btnAddLeftCtrlSubRegion.setOnClickListener {
            dismiss()
            onSubRegionEditRequested?.invoke(true, KeyboardMacroPresets.MOD_LEFT_CONTROL)
        }
        binding.btnAddRightCtrlSubRegion.setOnClickListener {
            dismiss()
            onSubRegionEditRequested?.invoke(false, KeyboardMacroPresets.MOD_LEFT_CONTROL)
        }
        binding.btnClearSubRegions.setOnClickListener {
            val prev = viewModel.touchMouseConfig.value
            viewModel.updateTouchMouseConfig(prev.copy(
                leftButton = prev.leftButton.copy(subRegions = emptyList()),
                rightButton = prev.rightButton.copy(subRegions = emptyList()),
            ))
        }

        val rightSliderListener = Slider.OnChangeListener { _, _, _ ->
            if (!initialising) { updateRightDynamicLabels(); pushConfig() }
        }
        binding.sliderRightOffsetX.addOnChangeListener(rightSliderListener)
        binding.sliderRightOffsetY.addOnChangeListener(rightSliderListener)
        binding.sliderRightRadius.addOnChangeListener(rightSliderListener)

        // ── Sniper zone ──────────────────────────────────────────────────
        binding.switchSniperZone.setOnCheckedChangeListener { _, checked ->
            binding.groupSniperConfig.isVisible = checked
            if (!initialising) pushConfig()
        }

        binding.toggleSniperDivisor.addOnButtonCheckedListener { _, _, isChecked ->
            if (!isChecked || initialising) return@addOnButtonCheckedListener
            pushConfig()
        }

        binding.btnSetSniperZone.setOnClickListener {
            dismiss()
            onSniperEditRequested?.invoke()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun Float.snapToStep(step: Float) = (kotlin.math.round(this / step) * step)

    private fun setLeftZoneTypeVisibility(type: ZoneType) {
        binding.btnSetLeftZone.isVisible = type == ZoneType.STATIC
        binding.groupDynamicLeft.isVisible = type == ZoneType.DYNAMIC && !binding.switchSharedDynamic.isChecked
    }

    private fun setRightZoneTypeVisibility(type: ZoneType) {
        binding.btnSetRightZone.isVisible = type == ZoneType.STATIC
        binding.groupDynamicRight.isVisible = type == ZoneType.DYNAMIC && !binding.switchSharedDynamic.isChecked
    }

    private fun refreshDynamicGroupVisibility() {
        val leftType = if (binding.toggleLeftZone.checkedButtonId == R.id.btnLeftStatic)
            ZoneType.STATIC else ZoneType.DYNAMIC
        val rightType = if (binding.toggleRightZone.checkedButtonId == R.id.btnRightStatic)
            ZoneType.STATIC else ZoneType.DYNAMIC
        setLeftZoneTypeVisibility(leftType)
        setRightZoneTypeVisibility(rightType)
    }

    private fun updateSharedDynamicLabels() {
        binding.labelSharedOffsetX.text = formatOffset(binding.sliderSharedOffsetX.value)
        binding.labelSharedOffsetY.text = formatOffset(binding.sliderSharedOffsetY.value)
        binding.labelSharedRadius.text = "%.2f".format(binding.sliderSharedRadius.value)
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

    private fun rebuildMacroList(macros: List<KeyboardMacroButtonConfig>) {
        binding.macroList.removeAllViews()
        binding.btnEditMacroLayout.isVisible = macros.isNotEmpty()
        val dp = resources.displayMetrics.density
        macros.forEachIndexed { index, macro ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
            }
            val label = TextView(requireContext()).apply {
                text = macro.label
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val removeBtn = MaterialButton(requireContext(),
                null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "×"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    val updated = viewModel.touchMouseConfig.value.let {
                        it.copy(macroButtons = it.macroButtons.toMutableList().also { list -> list.removeAt(index) })
                    }
                    viewModel.updateTouchMouseConfig(updated)
                    rebuildMacroList(updated.macroButtons)
                }
            }
            row.addView(label)
            row.addView(removeBtn)
            binding.macroList.addView(row)
        }
    }

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

        val sniperDivisor = when (binding.toggleSniperDivisor.checkedButtonId) {
            R.id.btnSniper2x -> 2f
            R.id.btnSniper8x -> 8f
            else             -> 4f
        }
        val newConfig = TouchMouseConfig(
            mode = mode,
            sensitivity = binding.sliderSensitivity.value.toInt(),
            scrollEnabled = binding.switchScrollEnabled.isChecked,
            invertScroll  = binding.switchInvertScroll.isChecked,
            sharedDynamicZone = binding.switchSharedDynamic.isChecked,
            sharedDynamicOffsetX = binding.sliderSharedOffsetX.value,
            sharedDynamicOffsetY = binding.sliderSharedOffsetY.value,
            sharedDynamicRadius = binding.sliderSharedRadius.value,
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
            ),
            sniperEnabled = binding.switchSniperZone.isChecked,
            sniperLeft    = prev.sniperLeft,
            sniperTop     = prev.sniperTop,
            sniperRight   = prev.sniperRight,
            sniperBottom  = prev.sniperBottom,
            sniperDivisor = sniperDivisor,
            macroHostDefaults = if (binding.toggleMacroDefaults.checkedButtonId == R.id.btnMacroMac) {
                MacroHostDefaults.MAC
            } else {
                MacroHostDefaults.WINDOWS
            },
            macroButtons = prev.macroButtons,
        )
        viewModel.updateTouchMouseConfig(newConfig)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
