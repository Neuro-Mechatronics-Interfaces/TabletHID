package com.tablet.hid.ui.tutorial

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.tablet.hid.HidViewModel
import com.tablet.hid.R
import com.tablet.hid.bluetooth.HidManager
import com.tablet.hid.databinding.FragmentTutorialBinding
import com.tablet.hid.model.DeviceMode
import com.tablet.hid.util.HidPrefs
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
    private var bondedMatch: BluetoothDevice? = null
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

        bondedMatch = findBondedDevice()
        activeColumn = if (bondedMatch != null) ActiveColumn.RECONNECT else ActiveColumn.PAIR

        buildInstructions(mode, isWindows = true)

        binding.radioGroupOs.setOnCheckedChangeListener { _, checkedId ->
            buildInstructions(mode, isWindows = checkedId == R.id.radioWindows)
        }

        // Reconnect button replaced by the reconnect column step; always hide it.
        binding.btnReconnect.isVisible = false

        binding.btnMakeDiscoverable.setOnClickListener {
            activateColumn(ActiveColumn.PAIR)
            viewModel.initialize(mode)
            requestDiscoverable()
        }

        binding.btnEnterMode.setOnClickListener { navigateToMode(mode) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> updateUi(state, mode) }
            }
        }
    }

    // ── Instruction building ──────────────────────────────────────────────────

    private fun buildInstructions(mode: DeviceMode, isWindows: Boolean) {
        val bonded = bondedMatch
        val twoColumns = bonded != null

        binding.columnReconnect.isVisible = twoColumns
        binding.columnDivider.isVisible = twoColumns
        binding.labelReconnect.isVisible = twoColumns
        binding.labelPair.isVisible = twoColumns

        if (twoColumns) {
            val deviceLabel = bonded!!.name ?: bonded.address
            val enterLabel = getString(
                if (mode == DeviceMode.TOUCH_MOUSE) R.string.btn_enter_mouse_mode
                else R.string.btn_enter_gamepad_mode
            )
            reconnectRows = inflateSteps(
                binding.instructionsListReconnect,
                listOf(
                    getString(R.string.step_reconnect_tap, deviceLabel),
                    getString(R.string.step_reconnect_enter, enterLabel)
                )
            ) { i ->
                when (i) {
                    0 -> { activateColumn(ActiveColumn.RECONNECT); viewModel.reconnect(mode, bonded) }
                    1 -> if (binding.btnEnterMode.isEnabled) navigateToMode(mode)
                }
            }
        }

        val arrayId = when {
            mode == DeviceMode.TOUCH_MOUSE && isWindows  -> R.array.instructions_windows_mouse
            mode == DeviceMode.TOUCH_MOUSE && !isWindows -> R.array.instructions_macos_mouse
            mode == DeviceMode.GAMEPAD     && isWindows  -> R.array.instructions_windows_gamepad
            else                                          -> R.array.instructions_macos_gamepad
        }
        pairRows = inflateSteps(
            binding.instructionsList,
            resources.getStringArray(arrayId).toList()
        ) { i ->
            when {
                i == 0 -> {
                    activateColumn(ActiveColumn.PAIR)
                    viewModel.initialize(mode)
                    requestDiscoverable()
                }
                i == pairRows.size - 1 -> if (binding.btnEnterMode.isEnabled) navigateToMode(mode)
            }
        }

        // Column transparency
        if (twoColumns) {
            binding.columnReconnect.alpha = if (activeColumn == ActiveColumn.RECONNECT) 1f else 0.45f
            binding.columnPair.alpha      = if (activeColumn == ActiveColumn.PAIR)      1f else 0.45f
        }

        // Apply current highlights without animation (fresh build)
        applyHighlightsImmediate()
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
        if (bondedMatch != null) {
            binding.columnReconnect.animate()
                .alpha(if (column == ActiveColumn.RECONNECT) 1f else 0.45f).setDuration(250).start()
            binding.columnPair.animate()
                .alpha(if (column == ActiveColumn.PAIR) 1f else 0.45f).setDuration(250).start()
        }
        // Move pulse to the newly active column
        stopPulse()
        val rows = if (column == ActiveColumn.RECONNECT) reconnectRows else pairRows
        val idx  = if (column == ActiveColumn.RECONNECT) currentReconnectStep else currentPairStep
        rows.forEachIndexed { i, r -> r.highlight.alpha = if (i == idx) 0.85f else 0f }
        startPulse(rows.getOrNull(idx)?.highlight)
    }

    // ── State → UI ───────────────────────────────────────────────────────────

    private fun updateUi(state: HidManager.State, mode: DeviceMode) {
        val (statusText, enterEnabled) = when (state) {
            is HidManager.State.Idle ->
                getString(R.string.tutorial_status_idle) to false
            is HidManager.State.Registering ->
                getString(R.string.tutorial_status_registering) to false
            is HidManager.State.Reconnecting ->
                getString(R.string.tutorial_status_reconnecting, state.deviceName) to false
            is HidManager.State.WaitingForConnection ->
                getString(R.string.tutorial_status_waiting, mode.deviceName) to false
            is HidManager.State.Connected ->
                getString(R.string.tutorial_status_connected,
                    state.device.name ?: state.device.address) to true
            is HidManager.State.Error ->
                getString(R.string.tutorial_status_error, state.message) to false
        }
        binding.chipStatus.text = statusText
        binding.btnEnterMode.isEnabled = enterEnabled

        val newPairStep = when (state) {
            is HidManager.State.Idle, is HidManager.State.Error -> 0
            is HidManager.State.Registering                      -> 1
            is HidManager.State.WaitingForConnection             -> 2
            is HidManager.State.Connected -> (pairRows.size - 1).coerceAtLeast(0)
            is HidManager.State.Reconnecting                     -> currentPairStep
        }
        val newReconnectStep = when (state) {
            is HidManager.State.Connected -> (reconnectRows.size - 1).coerceAtLeast(0)
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
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
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

    @SuppressLint("MissingPermission")
    private fun findBondedDevice(): BluetoothDevice? {
        val address = HidPrefs.getLastDeviceAddress(requireContext()) ?: return null
        val adapter = requireContext().getSystemService(BluetoothManager::class.java)?.adapter
            ?: return null
        return try {
            adapter.bondedDevices?.firstOrNull { it.address == address }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun requestDiscoverable() {
        @Suppress("DEPRECATION")
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        stopPulse()
        super.onDestroyView()
        _binding = null
    }
}
