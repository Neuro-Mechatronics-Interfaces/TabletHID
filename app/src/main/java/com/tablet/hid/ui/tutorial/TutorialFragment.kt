package com.tablet.hid.ui.tutorial

import android.animation.ObjectAnimator
import android.annotation.SuppressLint

import android.os.Bundle
import android.text.Html
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tablet.hid.HidViewModel
import com.tablet.hid.R
import com.tablet.hid.bluetooth.BleHidManager
import com.tablet.hid.databinding.FragmentTutorialBinding
import com.tablet.hid.model.DeviceMode
import com.tablet.hid.model.HidHost
import com.tablet.hid.util.AppearanceStore
import com.tablet.hid.util.HidHostStore
import kotlinx.coroutines.launch

class TutorialFragment : Fragment() {

    private var _binding: FragmentTutorialBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HidViewModel by activityViewModels()
    private val args: TutorialFragmentArgs by navArgs()

    // ── Step row ─────────────────────────────────────────────────────────────

    private data class StepRow(
        val root: View,
        val highlight: View,
        val badge: TextView,
        val text: TextView
    )

    private enum class ActiveColumn { RECONNECT, PAIR }

    private var activeColumn = ActiveColumn.PAIR
    private var reconnectRows: List<StepRow> = emptyList()
    private var pairRows: List<StepRow> = emptyList()

    /** All cached hosts that are still present in the system's bonded-devices list. */
    private var bondedHosts: List<HidHost> = emptyList()

    /** The host currently targeted for reconnection (user-selected if multiple). */
    private var selectedHost: HidHost? = null

    private var pulseAnimator: ObjectAnimator? = null
    private var currentPairStep = 0
    private var currentReconnectStep = 0

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTutorialBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bars.bottom)
            insets
        }

        val mode = args.mode
        binding.btnEnterMode.setText(
            if (mode == DeviceMode.TOUCH_MOUSE) R.string.btn_enter_mouse_mode
            else R.string.btn_enter_gamepad_mode
        )

        bondedHosts = findBondedHosts()
        selectedHost = bondedHosts.firstOrNull()
        activeColumn = if (selectedHost != null) ActiveColumn.RECONNECT else ActiveColumn.PAIR

        buildInstructions(mode, isWindows = true)

        binding.radioGroupOs.setOnCheckedChangeListener { _, checkedId ->
            buildInstructions(mode, isWindows = checkedId == R.id.radioWindows)
        }

        binding.btnReconnect.isVisible = false

        binding.btnMakeDiscoverable.setOnClickListener {
            activateColumn(ActiveColumn.PAIR)
            startPairFlow(mode)
        }

        binding.btnEnterMode.setOnClickListener { navigateToMode(mode) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    updateUi(state, mode)
                    // Refresh the host list in case a connection updated btName.
                    if (state is BleHidManager.State.Connected) {
                        bondedHosts = findBondedHosts()
                        buildDeviceChips(mode)
                    }
                }
            }
        }
    }

    // ── Instruction building ──────────────────────────────────────────────────

    private fun buildInstructions(mode: DeviceMode, isWindows: Boolean) {
        val twoColumns = selectedHost != null

        binding.columnReconnect.isVisible = twoColumns
        binding.columnDivider.isVisible   = twoColumns
        binding.labelReconnect.isVisible  = twoColumns
        binding.labelPair.isVisible       = twoColumns

        if (twoColumns) {
            buildDeviceChips(mode)
            buildReconnectSteps(mode)
        }

        val arrayId = when {
            mode == DeviceMode.TOUCH_MOUSE && isWindows  -> R.array.instructions_windows_mouse
            mode == DeviceMode.TOUCH_MOUSE && !isWindows -> R.array.instructions_macos_mouse
            mode == DeviceMode.GAMEPAD     && isWindows  -> R.array.instructions_windows_gamepad
            else                                          -> R.array.instructions_macos_gamepad
        }
        pairRows = inflateSteps(
            binding.instructionsList,
            resources.getStringArray(arrayId)
                .map { it.replace(AppearanceStore.DEFAULT_DEVICE_NAME, peripheralName()) }
        ) { i ->
            when {
                i == 0 -> {
                    activateColumn(ActiveColumn.PAIR)
                    startPairFlow(mode)
                }
                i == pairRows.size - 1 -> if (binding.btnEnterMode.isEnabled) navigateToMode(mode)
            }
        }

        if (twoColumns) {
            binding.columnReconnect.alpha = if (activeColumn == ActiveColumn.RECONNECT) 1f else 0.45f
            binding.columnPair.alpha      = if (activeColumn == ActiveColumn.PAIR)      1f else 0.45f
        }

        applyHighlightsImmediate()
    }

    /** Build or rebuild the chip group for device selection. */
    private fun buildDeviceChips(mode: DeviceMode) {
        val multiHost = bondedHosts.size > 1
        binding.devicePickerScroll.isVisible = multiHost

        if (!multiHost) return

        binding.deviceChipGroup.removeAllViews()
        bondedHosts.forEach { host ->
            val chip = Chip(requireContext()).apply {
                text        = host.displayName
                isCheckable = true
                isChecked   = host.address == selectedHost?.address
                setOnClickListener {
                    selectedHost = host
                    buildReconnectSteps(mode)
                }
                setOnLongClickListener {
                    showHostOptionsDialog(host, mode)
                    true
                }
            }
            binding.deviceChipGroup.addView(chip)
        }
    }

    /** Rebuild only the reconnect-column step rows for [selectedHost]. */
    private fun buildReconnectSteps(mode: DeviceMode) {
        val host = selectedHost ?: return
        val enterLabel = getString(
            if (mode == DeviceMode.TOUCH_MOUSE) R.string.btn_enter_mouse_mode
            else R.string.btn_enter_gamepad_mode
        )
        reconnectRows = inflateSteps(
            binding.instructionsListReconnect,
            listOf(
                getString(R.string.step_reconnect_tap, host.displayName),
                getString(R.string.step_reconnect_enter, enterLabel)
            )
        ) { i ->
            when (i) {
                0 -> {
                    activateColumn(ActiveColumn.RECONNECT)
                    viewModel.reconnect(mode, host)
                }
                1 -> if (binding.btnEnterMode.isEnabled) navigateToMode(mode)
            }
        }
        applyHighlightsImmediate()
    }

    // ── Host options dialog (rename + forget) ─────────────────────────────────

    private fun showHostOptionsDialog(host: HidHost, mode: DeviceMode) {
        val editText = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            hint      = getString(R.string.hint_device_label)
            setText(host.alias ?: "")
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val px16 = (16 * resources.displayMetrics.density).toInt()
            setPadding(px16, 0, px16, 0)
            addView(editText)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(host.btName.ifBlank { host.address })
            .setMessage(R.string.dialog_device_options_message)
            .setView(container)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                viewModel.renameHost(host.address, editText.text.toString())
                bondedHosts = findBondedHosts()
                buildDeviceChips(mode)
                if (selectedHost?.address == host.address) {
                    selectedHost = bondedHosts.firstOrNull { it.address == host.address }
                    buildReconnectSteps(mode)
                }
            }
            .setNeutralButton(R.string.btn_forget_device) { _, _ ->
                viewModel.forgetHost(host)
                bondedHosts = findBondedHosts()
                if (selectedHost?.address == host.address) selectedHost = bondedHosts.firstOrNull()
                if (bondedHosts.isEmpty()) activeColumn = ActiveColumn.PAIR
                buildInstructions(
                    mode,
                    isWindows = binding.radioGroupOs.checkedRadioButtonId == R.id.radioWindows
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun inflateSteps(
        container: LinearLayout,
        htmlSteps: List<String>,
        onTap: (Int) -> Unit
    ): List<StepRow> {
        container.removeAllViews()
        return htmlSteps.mapIndexed { i, html ->
            val row = layoutInflater.inflate(R.layout.item_step, container, false)
            val sr = StepRow(
                root      = row,
                highlight = row.findViewById(R.id.stepHighlight),
                badge     = row.findViewById<TextView>(R.id.stepBadge).also { it.text = (i + 1).toString() },
                text      = row.findViewById<TextView>(R.id.stepText).also {
                    it.text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
                }
            )
            row.setOnClickListener { onTap(i) }
            container.addView(row)
            sr
        }
    }

    // ── Column switching ──────────────────────────────────────────────────────

    private fun activateColumn(column: ActiveColumn) {
        if (activeColumn == column) return
        activeColumn = column
        if (selectedHost != null) {
            binding.columnReconnect.animate()
                .alpha(if (column == ActiveColumn.RECONNECT) 1f else 0.45f).setDuration(250).start()
            binding.columnPair.animate()
                .alpha(if (column == ActiveColumn.PAIR) 1f else 0.45f).setDuration(250).start()
        }
        stopPulse()
        val rows = if (column == ActiveColumn.RECONNECT) reconnectRows else pairRows
        val idx  = if (column == ActiveColumn.RECONNECT) currentReconnectStep else currentPairStep
        rows.forEachIndexed { i, r -> r.highlight.alpha = if (i == idx) 0.85f else 0f }
        startPulse(rows.getOrNull(idx)?.highlight)
    }

    // ── State → UI ───────────────────────────────────────────────────────────

    private fun updateUi(state: BleHidManager.State, mode: DeviceMode) {
        val (statusText, enterEnabled) = when (state) {
            is BleHidManager.State.Idle ->
                getString(R.string.tutorial_status_idle) to false
            is BleHidManager.State.Registering ->
                getString(R.string.tutorial_status_registering) to false
            is BleHidManager.State.Reconnecting ->
                getString(R.string.tutorial_status_reconnecting, state.deviceName) to false
            is BleHidManager.State.WaitingForConnection ->
                getString(R.string.tutorial_status_waiting, peripheralName()) to false
            is BleHidManager.State.Connected ->
                getString(R.string.tutorial_status_connected,
                    state.device.name ?: state.device.address) to true
            is BleHidManager.State.Error ->
                getString(R.string.tutorial_status_error, state.message) to false
        }
        binding.chipStatus.text = statusText
        binding.btnEnterMode.isEnabled = enterEnabled

        val newPairStep = when (state) {
            is BleHidManager.State.Idle, is BleHidManager.State.Error -> 0
            is BleHidManager.State.Registering                      -> 1
            is BleHidManager.State.WaitingForConnection             -> 2
            is BleHidManager.State.Connected -> (pairRows.size - 1).coerceAtLeast(0)
            is BleHidManager.State.Reconnecting                     -> currentPairStep
        }
        val newReconnectStep = when (state) {
            is BleHidManager.State.Connected -> (reconnectRows.size - 1).coerceAtLeast(0)
            else                           -> 0
        }

        if (newPairStep != currentPairStep) {
            val old = currentPairStep
            currentPairStep = newPairStep
            if (activeColumn == ActiveColumn.PAIR) {
                transitionStep(pairRows, old, newPairStep)
            } else {
                pairRows.forEachIndexed { i, r -> r.highlight.alpha = if (i == newPairStep) 0.6f else 0f }
            }
        }
        if (newReconnectStep != currentReconnectStep) {
            val old = currentReconnectStep
            currentReconnectStep = newReconnectStep
            if (activeColumn == ActiveColumn.RECONNECT) {
                transitionStep(reconnectRows, old, newReconnectStep)
            } else {
                reconnectRows.forEachIndexed { i, r -> r.highlight.alpha = if (i == newReconnectStep) 0.6f else 0f }
            }
        }
    }

    private fun peripheralName(): String = AppearanceStore.getDeviceName(requireContext())

    // ── Highlight animation ───────────────────────────────────────────────────

    private fun applyHighlightsImmediate() {
        stopPulse()
        val reconnectActive = activeColumn == ActiveColumn.RECONNECT
        reconnectRows.forEachIndexed { i, r ->
            r.highlight.alpha = if (i == currentReconnectStep) (if (reconnectActive) 0.85f else 0.6f) else 0f
        }
        pairRows.forEachIndexed { i, r ->
            r.highlight.alpha = if (i == currentPairStep) (if (!reconnectActive) 0.85f else 0.6f) else 0f
        }
        val activeRows = if (activeColumn == ActiveColumn.RECONNECT) reconnectRows else pairRows
        val activeIdx  = if (activeColumn == ActiveColumn.RECONNECT) currentReconnectStep else currentPairStep
        startPulse(activeRows.getOrNull(activeIdx)?.highlight)
    }

    private fun transitionStep(rows: List<StepRow>, oldIdx: Int, newIdx: Int) {
        stopPulse()
        if (oldIdx in rows.indices) {
            rows[oldIdx].highlight.animate().alpha(0f).setDuration(200).start()
        }
        if (newIdx in rows.indices) {
            val h = rows[newIdx].highlight
            h.translationY = -resources.displayMetrics.density * 10f
            h.animate()
                .alpha(0.85f)
                .translationY(0f)
                .setDuration(300)
                .withEndAction { startPulse(h) }
                .start()
        }
    }

    private fun startPulse(view: View?) {
        view ?: return
        pulseAnimator = ObjectAnimator.ofFloat(view, "alpha", 0.7f, 1f).apply {
            duration    = 1000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode  = ObjectAnimator.REVERSE
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun navigateToMode(mode: DeviceMode) {
        when (mode) {
            DeviceMode.TOUCH_MOUSE -> findNavController().navigate(R.id.action_tutorial_to_touchMouse)
            DeviceMode.GAMEPAD     -> findNavController().navigate(R.id.action_tutorial_to_gamepad)
        }
    }

    /** Return all previously connected hosts, sorted most-recently-seen first. */
    private fun findBondedHosts(): List<HidHost> =
        HidHostStore.getAll(requireContext())

    private fun startPairFlow(mode: DeviceMode) {
        if (viewModel.state.value !is BleHidManager.State.WaitingForConnection) {
            viewModel.initialize(mode)
        }
    }

    override fun onDestroyView() {
        stopPulse()
        super.onDestroyView()
        _binding = null
    }
}
