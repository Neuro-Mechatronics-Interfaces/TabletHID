package com.tablet.hid.ui.gamepad

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import com.tablet.hid.BuildConfig
import com.tablet.hid.HidViewModel
import com.tablet.hid.R
import com.tablet.hid.model.ButtonConfig
import com.tablet.hid.model.ClickBehavior
import com.tablet.hid.model.GamepadConfig
import com.tablet.hid.model.JoystickConfig
import com.tablet.hid.model.TriggerDragAxis
import com.tablet.hid.databinding.SheetGamepadConfigBinding
import java.io.File

class GamepadConfigSheet : BottomSheetDialogFragment() {

    private var _b: SheetGamepadConfigBinding? = null
    private val b get() = _b!!

    private val viewModel: HidViewModel by activityViewModels()

    var onEditLayoutRequested: (() -> Unit)? = null

    // Prevents listener feedback loops during programmatic UI setup.
    private var initialising = false

    // Button labels in display order — index matches buttonKey list below.
    private val buttonLabels = listOf(
        "A", "B", "X", "Y",
        "LB", "RB", "LT", "RT",
        "Back", "Start",
        "D-Pad ↑", "D-Pad ↓", "D-Pad ←", "D-Pad →"
    )
    private var selectedIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = SheetGamepadConfigBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cfg = viewModel.gamepadConfig.value

        // ── Button selector ──────────────────────────────────────────────
        val adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_dropdown_item_1line, buttonLabels)
        b.spinnerButton.setAdapter(adapter)
        b.spinnerButton.setText(buttonLabels[selectedIndex], false)
        b.spinnerButton.setOnItemClickListener { _, _, pos, _ ->
            selectedIndex = pos
            applyButtonUi(buttonConfig(viewModel.gamepadConfig.value, pos))
        }

        applyConfig(cfg)

        // ── Button enabled ───────────────────────────────────────────────
        b.switchButtonEnabled.setOnCheckedChangeListener { _, on ->
            if (initialising) return@setOnCheckedChangeListener
            pushButtonConfig { it.copy(enabled = on) }
        }

        // ── Joystick enabled ─────────────────────────────────────────────
        b.switchLeftEnabled.setOnCheckedChangeListener { _, on ->
            if (initialising) return@setOnCheckedChangeListener
            b.groupLeftJoystick.isVisible = on
            val c = viewModel.gamepadConfig.value
            viewModel.updateGamepadConfig(c.copy(leftJoystick = c.leftJoystick.copy(enabled = on)))
        }
        b.switchRightEnabled.setOnCheckedChangeListener { _, on ->
            if (initialising) return@setOnCheckedChangeListener
            b.groupRightJoystick.isVisible = on
            val c = viewModel.gamepadConfig.value
            viewModel.updateGamepadConfig(c.copy(rightJoystick = c.rightJoystick.copy(enabled = on)))
        }

        // ── Behavior toggle ──────────────────────────────────────────────
        b.toggleBehavior.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (initialising || !isChecked) return@addOnButtonCheckedListener
            pushButtonConfig { old ->
                old.copy(behavior = if (checkedId == R.id.btnMomentary)
                    ClickBehavior.MOMENTARY else ClickBehavior.LATCHING)
            }
        }

        // ── Turbo ────────────────────────────────────────────────────────
        b.switchTurbo.setOnCheckedChangeListener { _, on ->
            if (initialising) return@setOnCheckedChangeListener
            b.groupTurbo.isVisible = on
            pushButtonConfig { it.copy(turbo = on) }
        }

        sliderLabel(b.sliderTurboDuration, b.labelTurboDuration, "Press duration", "ms") { v ->
            pushButtonConfig { it.copy(turboDurationMs = v.toInt()) }
        }
        sliderLabel(b.sliderTurboInterval, b.labelTurboInterval, "Repeat interval", "ms") { v ->
            pushButtonConfig { it.copy(turboIntervalMs = v.toInt()) }
        }

        // ── Trigger sensitivity (LT/RT only) ─────────────────────────────
        sliderLabel(b.sliderTriggerTravel, b.labelTriggerTravel, "Trigger travel", "dp") { v ->
            if (!initialising) pushButtonConfig { it.copy(triggerTravelDp = v) }
        }
        b.toggleTriggerAxis.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (initialising || !isChecked) return@addOnButtonCheckedListener
            val axis = when (checkedId) {
                R.id.btnAxisUp    -> TriggerDragAxis.UP
                R.id.btnAxisDown  -> TriggerDragAxis.DOWN
                R.id.btnAxisLeft  -> TriggerDragAxis.LEFT
                else              -> TriggerDragAxis.RIGHT
            }
            pushButtonConfig { it.copy(triggerAxis = axis) }
        }

        // ── Joystick sliders ─────────────────────────────────────────────
        sliderLabel(b.sliderLeftDeadzone, b.labelLeftDeadzone, "Deadzone", "%") { v ->
            if (!initialising) {
                val c = viewModel.gamepadConfig.value
                viewModel.updateGamepadConfig(
                    c.copy(leftJoystick = c.leftJoystick.copy(deadzone = v / 100f))
                )
            }
        }
        sliderLabel(b.sliderLeftGain, b.labelLeftGain, "Gain", "×", divisor = 100f) { v ->
            if (!initialising) {
                val c = viewModel.gamepadConfig.value
                viewModel.updateGamepadConfig(
                    c.copy(leftJoystick = c.leftJoystick.copy(gain = v / 100f))
                )
            }
        }
        sliderLabel(b.sliderRightDeadzone, b.labelRightDeadzone, "Deadzone", "%") { v ->
            if (!initialising) {
                val c = viewModel.gamepadConfig.value
                viewModel.updateGamepadConfig(
                    c.copy(rightJoystick = c.rightJoystick.copy(deadzone = v / 100f))
                )
            }
        }
        sliderLabel(b.sliderRightGain, b.labelRightGain, "Gain", "×", divisor = 100f) { v ->
            if (!initialising) {
                val c = viewModel.gamepadConfig.value
                viewModel.updateGamepadConfig(
                    c.copy(rightJoystick = c.rightJoystick.copy(gain = v / 100f))
                )
            }
        }

        // ── Layout editing ───────────────────────────────────────────────
        b.btnEditLayout.setOnClickListener {
            dismiss()
            onEditLayoutRequested?.invoke()
        }
        b.btnResetLayout.setOnClickListener {
            val reset = viewModel.gamepadConfig.value.resetLayout()
            viewModel.updateGamepadConfig(reset)
            dismiss()
            onEditLayoutRequested?.invoke() // refresh positions in fragment
        }

        // ── DEV section ──────────────────────────────────────────────────
        b.groupDev.isVisible = BuildConfig.DEV_MODE
        if (BuildConfig.DEV_MODE) {
            b.btnExportConfig.setOnClickListener {
                val profile = viewModel.activeProfile.value
                val fname = com.tablet.hid.util.GamepadConfigStore.prefsName(profile) + ".xml"
                val file = File(requireContext().filesDir.parent, "shared_prefs/$fname")
                val text = if (file.exists()) file.readText()
                           else "Not found: ${file.absolutePath}\n\n(Configure and save settings first to create this file.)"
                shareText(fname, text)
            }
            b.btnExportAllConfigs.setOnClickListener {
                val dir = File(requireContext().filesDir.parent, "shared_prefs")
                val sb = StringBuilder()
                dir.listFiles { f -> f.extension == "xml" }
                    ?.sortedBy { it.name }
                    ?.forEach { f -> sb.append("=== ${f.name} ===\n").append(f.readText()).append("\n\n") }
                    ?: sb.append("Not found: ${dir.absolutePath}")
                shareText("TabletHID All Configs", sb.toString())
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun applyConfig(cfg: GamepadConfig) {
        initialising = true
        applyButtonUi(buttonConfig(cfg, selectedIndex))
        b.switchLeftEnabled.isChecked  = cfg.leftJoystick.enabled
        b.groupLeftJoystick.isVisible  = cfg.leftJoystick.enabled
        b.switchRightEnabled.isChecked = cfg.rightJoystick.enabled
        b.groupRightJoystick.isVisible = cfg.rightJoystick.enabled
        b.sliderLeftDeadzone.value  = (cfg.leftJoystick.deadzone  * 100f).coerceIn(0f, 30f)
        b.sliderLeftGain.value      = (cfg.leftJoystick.gain      * 100f).coerceIn(50f, 300f)
        b.sliderRightDeadzone.value = (cfg.rightJoystick.deadzone * 100f).coerceIn(0f, 30f)
        b.sliderRightGain.value     = (cfg.rightJoystick.gain     * 100f).coerceIn(50f, 300f)
        initialising = false
    }

    private fun applyButtonUi(cfg: ButtonConfig) {
        initialising = true
        b.switchButtonEnabled.isChecked = cfg.enabled
        b.toggleBehavior.check(
            if (cfg.behavior == ClickBehavior.MOMENTARY) R.id.btnMomentary else R.id.btnLatching
        )
        b.switchTurbo.isChecked = cfg.turbo
        b.groupTurbo.isVisible  = cfg.turbo
        b.sliderTurboDuration.value = cfg.turboDurationMs.toFloat().coerceIn(10f, 500f)
        b.sliderTurboInterval.value = cfg.turboIntervalMs.toFloat().coerceIn(50f, 1000f)
        b.labelTurboDuration.text = "Press duration: ${cfg.turboDurationMs} ms"
        b.labelTurboInterval.text = "Repeat interval: ${cfg.turboIntervalMs} ms"
        val isTrigger = selectedIndex == 6 || selectedIndex == 7
        b.groupTrigger.isVisible = isTrigger
        if (isTrigger) {
            b.sliderTriggerTravel.value = cfg.triggerTravelDp.coerceIn(30f, 300f)
            b.labelTriggerTravel.text = "Trigger travel: ${cfg.triggerTravelDp.toInt()} dp"
            b.toggleTriggerAxis.check(when (cfg.triggerAxis) {
                TriggerDragAxis.UP    -> R.id.btnAxisUp
                TriggerDragAxis.DOWN  -> R.id.btnAxisDown
                TriggerDragAxis.LEFT  -> R.id.btnAxisLeft
                TriggerDragAxis.RIGHT -> R.id.btnAxisRight
            })
        }
        initialising = false
    }

    /** Read current button config at [index] from the config data class. */
    private fun buttonConfig(cfg: GamepadConfig, index: Int): ButtonConfig = when (index) {
        0  -> cfg.btnA;    1  -> cfg.btnB;   2  -> cfg.btnX;   3  -> cfg.btnY
        4  -> cfg.btnLb;   5  -> cfg.btnRb;  6  -> cfg.btnLt;  7  -> cfg.btnRt
        8  -> cfg.btnBack; 9  -> cfg.btnStart
        10 -> cfg.dpadUp;  11 -> cfg.dpadDown; 12 -> cfg.dpadLeft; 13 -> cfg.dpadRight
        else -> ButtonConfig()
    }

    /** Apply [transform] to the selected button and push to ViewModel. */
    private fun pushButtonConfig(transform: (ButtonConfig) -> ButtonConfig) {
        val c = viewModel.gamepadConfig.value
        val updated = when (selectedIndex) {
            0  -> c.copy(btnA     = transform(c.btnA))
            1  -> c.copy(btnB     = transform(c.btnB))
            2  -> c.copy(btnX     = transform(c.btnX))
            3  -> c.copy(btnY     = transform(c.btnY))
            4  -> c.copy(btnLb    = transform(c.btnLb))
            5  -> c.copy(btnRb    = transform(c.btnRb))
            6  -> c.copy(btnLt    = transform(c.btnLt))
            7  -> c.copy(btnRt    = transform(c.btnRt))
            8  -> c.copy(btnBack  = transform(c.btnBack))
            9  -> c.copy(btnStart = transform(c.btnStart))
            10 -> c.copy(dpadUp    = transform(c.dpadUp))
            11 -> c.copy(dpadDown  = transform(c.dpadDown))
            12 -> c.copy(dpadLeft  = transform(c.dpadLeft))
            13 -> c.copy(dpadRight = transform(c.dpadRight))
            else -> c
        }
        viewModel.updateGamepadConfig(updated)
    }

    private fun sliderLabel(
        slider: com.google.android.material.slider.Slider,
        label: TextView,
        prefix: String,
        unit: String,
        divisor: Float = 1f,
        onChange: (Float) -> Unit,
    ) {
        slider.addOnChangeListener { _, value, fromUser ->
            val display = if (divisor == 1f) "${value.toInt()} $unit"
                          else "${"%.1f".format(value / divisor)} $unit"
            label.text = "$prefix: $display"
            if (fromUser) onChange(value)
        }
    }

    private fun shareText(subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        startActivity(Intent.createChooser(intent, "Export"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}

private fun ButtonConfig.resetPos()   = copy(offsetX = 0f, offsetY = 0f, scaleX = 1f, scaleY = 1f)
private fun JoystickConfig.resetPos() = copy(offsetX = 0f, offsetY = 0f, scaleX = 1f, scaleY = 1f)

private fun GamepadConfig.resetLayout(): GamepadConfig = copy(
    btnA = btnA.resetPos(), btnB = btnB.resetPos(),
    btnX = btnX.resetPos(), btnY = btnY.resetPos(),
    btnLb = btnLb.resetPos(), btnRb = btnRb.resetPos(),
    btnLt = btnLt.resetPos(), btnRt = btnRt.resetPos(),
    btnBack = btnBack.resetPos(), btnStart = btnStart.resetPos(),
    dpadUp = dpadUp.resetPos(), dpadDown = dpadDown.resetPos(),
    dpadLeft = dpadLeft.resetPos(), dpadRight = dpadRight.resetPos(),
    leftJoystick = leftJoystick.resetPos(), rightJoystick = rightJoystick.resetPos(),
)
