package com.tablet.hid.ui.tutorial

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

        updateInstructions(mode, isWindows = true)

        binding.radioGroupOs.setOnCheckedChangeListener { _, checkedId ->
            updateInstructions(mode, isWindows = checkedId == R.id.radioWindows)
        }

        // ── Reconnect button — shown only when a matching bond already exists ──
        val bondedMatch = findBondedDevice()
        if (bondedMatch != null) {
            val deviceLabel = bondedMatch.name ?: bondedMatch.address
            binding.btnReconnect.text = getString(R.string.btn_reconnect, deviceLabel)
            binding.btnReconnect.isVisible = true
            binding.btnReconnect.setOnClickListener {
                viewModel.reconnect(mode, bondedMatch)
            }
        }

        // ── Make Discoverable — fresh pair flow ──
        binding.btnMakeDiscoverable.setOnClickListener {
            viewModel.initialize(mode)
            requestDiscoverable()
        }

        binding.btnEnterMode.setOnClickListener {
            when (mode) {
                DeviceMode.TOUCH_MOUSE ->
                    findNavController().navigate(R.id.action_tutorial_to_touchMouse)
                DeviceMode.GAMEPAD ->
                    findNavController().navigate(R.id.action_tutorial_to_gamepad)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> updateUi(state, mode) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

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

        // Once connected, hide the reconnect button so the UI is uncluttered.
        if (state is HidManager.State.Connected) {
            binding.btnReconnect.isVisible = false
        }
    }

    private fun updateInstructions(mode: DeviceMode, isWindows: Boolean) {
        val resId = when {
            mode == DeviceMode.TOUCH_MOUSE && isWindows  -> R.string.instructions_windows_mouse
            mode == DeviceMode.TOUCH_MOUSE && !isWindows -> R.string.instructions_macos_mouse
            mode == DeviceMode.GAMEPAD     && isWindows  -> R.string.instructions_windows_gamepad
            else                                         -> R.string.instructions_macos_gamepad
        }
        binding.textInstructions.text = Html.fromHtml(getString(resId), Html.FROM_HTML_MODE_LEGACY)
    }

    /**
     * Returns the bonded [BluetoothDevice] whose address was cached when we last connected,
     * or null if no cached address or the device is no longer bonded.
     */
    @SuppressLint("MissingPermission")
    private fun findBondedDevice(): BluetoothDevice? {
        val address = HidPrefs.getLastDeviceAddress(requireContext()) ?: return null
        val adapter: BluetoothAdapter =
            requireContext().getSystemService(BluetoothManager::class.java)?.adapter ?: return null
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
        super.onDestroyView()
        _binding = null
    }
}
