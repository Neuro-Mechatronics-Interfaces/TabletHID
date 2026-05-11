package com.tablet.hid.ui.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.view.ContextThemeWrapper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import com.google.android.material.chip.Chip
import com.tablet.hid.HidViewModel
import com.tablet.hid.R
import com.tablet.hid.bluetooth.BleHidManager
import com.tablet.hid.databinding.FragmentHomeBinding
import com.tablet.hid.model.DeviceMode
import com.tablet.hid.model.Profile
import com.tablet.hid.util.AppearanceStore
import com.tablet.hid.util.HidPrefs
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HidViewModel by activityViewModels()

    // Guards against listener loops when programmatically changing chip selection.
    private var settingChips = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null &&
            !AppearanceStore.hasCompletedOnboarding(requireContext())) {
            findNavController().navigate(R.id.onboardingFragment)
            return
        }

        // Handle home-screen shortcut — bypass mode card selection
        viewModel.pendingStartMode?.let { mode ->
            viewModel.pendingStartMode = null
            val deviceMode = when (mode) {
                "touch_mouse" -> DeviceMode.TOUCH_MOUSE
                "gamepad"     -> DeviceMode.GAMEPAD
                else          -> null
            }
            if (deviceMode != null) {
                navigateToTutorial(deviceMode)
                return
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bars.bottom)
            insets
        }

        // ── Mode cards ──────────────────────────────────────────────────────
        binding.cardTouchMouse.setOnClickListener { onModeTapped(DeviceMode.TOUCH_MOUSE) }
        binding.cardGamepad.setOnClickListener    { onModeTapped(DeviceMode.GAMEPAD) }

        // ── Connection card ──────────────────────────────────────────────────
        updateDeviceNameChip()
        binding.chipDeviceName.setOnClickListener { showEditNameDialog() }

        binding.btnHomeDiscoverable.setOnClickListener {
            viewModel.startServiceForMode(requireContext(), DeviceMode.TOUCH_MOUSE)
        }
        binding.btnHomeReconnect.setOnClickListener {
            val addr = HidPrefs.getLastDeviceAddress(requireContext()) ?: return@setOnClickListener
            viewModel.startServiceForMode(requireContext(), DeviceMode.TOUCH_MOUSE, addr)
        }

        // ── Add profile button ───────────────────────────────────────────────
        binding.btnAddProfile.setOnClickListener { showAddProfileDialog() }

        // ── Profile chip selection listener ─────────────────────────────────
        binding.chipGroupProfile.setOnCheckedStateChangeListener { _, checkedIds ->
            if (settingChips) return@setOnCheckedStateChangeListener
            val chipId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val profile = profileForChipId(chipId) ?: return@setOnCheckedStateChangeListener
            if (profile != viewModel.activeProfile.value) viewModel.setProfile(profile)
        }

        // ── Observe connection state ─────────────────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> updateConnectionUi(state) }
            }
        }

        // ── Observe profile state and rebuild chip row ───────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.customProfiles.collect { customs ->
                    rebuildCustomChips(customs)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activeProfile.collect { profile ->
                    setCheckedChip(profile)
                }
            }
        }
    }

    // ── Connection card ──────────────────────────────────────────────────────────

    private fun updateConnectionUi(state: BleHidManager.State) {
        val connected = state is BleHidManager.State.Connected
        val idle = state is BleHidManager.State.Idle || state is BleHidManager.State.Error
        val lastAddr = HidPrefs.getLastDeviceAddress(requireContext())

        binding.homeLedStatus.backgroundTintList = ColorStateList.valueOf(
            if (connected) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        )
        binding.homeConnStatus.text = when (state) {
            is BleHidManager.State.Idle            -> getString(R.string.status_disconnected)
            is BleHidManager.State.Registering     -> getString(R.string.home_status_connecting)
            is BleHidManager.State.WaitingForConnection -> getString(R.string.home_status_waiting)
            is BleHidManager.State.Reconnecting    -> getString(R.string.tutorial_status_reconnecting, state.deviceName)
            is BleHidManager.State.Connected       -> getString(R.string.status_connected)
            is BleHidManager.State.Error           -> getString(R.string.tutorial_status_error, state.message)
        }

        // Show Discoverable when idle; hide when active
        binding.btnHomeDiscoverable.isVisible = idle
        // Show Reconnect only when idle and there is a last device
        binding.btnHomeReconnect.isVisible = idle && lastAddr != null
    }

    private fun updateDeviceNameChip() {
        binding.chipDeviceName.text = AppearanceStore.getDeviceName(requireContext())
    }

    private fun showEditNameDialog() {
        val input = EditText(requireContext()).apply {
            setText(AppearanceStore.getDeviceName(requireContext()))
            hint = getString(R.string.onboarding_p4_hint)
            setSingleLine()
            setPadding(48, 24, 48, 8)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Device name")
            .setView(input)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    AppearanceStore.setDeviceName(requireContext(), name)
                    updateDeviceNameChip()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── Mode card navigation ─────────────────────────────────────────────────────

    private fun onModeTapped(mode: DeviceMode) {
        val state = viewModel.state.value
        val directAction = when (mode) {
            DeviceMode.TOUCH_MOUSE -> R.id.action_home_to_touchMouse
            DeviceMode.GAMEPAD     -> R.id.action_home_to_gamepad
        }
        when (state) {
            is BleHidManager.State.Connected -> {
                findNavController().navigate(directAction)
            }
            is BleHidManager.State.Idle, is BleHidManager.State.Error -> {
                val lastAddr = HidPrefs.getLastDeviceAddress(requireContext())
                if (lastAddr != null) {
                    // Reconnect to known device and enter the mode directly
                    viewModel.startServiceForMode(requireContext(), mode, lastAddr)
                    findNavController().navigate(directAction)
                } else {
                    // No prior pairing — guide through Tutorial
                    navigateToTutorial(mode)
                }
            }
            else -> {
                // Already connecting/reconnecting — enter the mode directly
                findNavController().navigate(directAction)
            }
        }
    }

    // ── Chip management ──────────────────────────────────────────────────────────

    /** Remove old custom chips and re-add one per custom profile. */
    private fun rebuildCustomChips(customs: List<Profile>) {
        val group = binding.chipGroupProfile
        // Remove all chips beyond the 3 static built-ins
        while (group.childCount > 3) group.removeViewAt(3)
        customs.forEach { profile ->
            val chip = Chip(
                ContextThemeWrapper(requireContext(),
                    com.google.android.material.R.style.Widget_Material3_Chip_Filter)
            ).apply {
                id = View.generateViewId()
                tag = profile.key
                text = profile.name
                isCheckable = true
                isCheckedIconVisible = true
                setChipIconResource(R.drawable.ic_person)
            }
            group.addView(chip)
        }
        // Re-apply current selection after rebuilding
        setCheckedChip(viewModel.activeProfile.value)
    }

    private fun setCheckedChip(profile: Profile) {
        settingChips = true
        val id = chipIdForProfile(profile)
        if (id != View.NO_ID && binding.chipGroupProfile.checkedChipId != id) {
            binding.chipGroupProfile.check(id)
        }
        settingChips = false
    }

    private fun chipIdForProfile(profile: Profile): Int = when (profile.key) {
        Profile.DEFAULT.key       -> R.id.chipProfileDefault
        Profile.ACCESS_BASIC.key  -> R.id.chipProfileBasic
        Profile.ACCESS_ADVANCED.key -> R.id.chipProfileAdvanced
        else -> {
            val group = binding.chipGroupProfile
            (3 until group.childCount)
                .map { group.getChildAt(it) as? Chip }
                .firstOrNull { it?.tag == profile.key }
                ?.id ?: View.NO_ID
        }
    }

    private fun profileForChipId(chipId: Int): Profile? {
        return when (chipId) {
            R.id.chipProfileDefault  -> Profile.DEFAULT
            R.id.chipProfileBasic    -> Profile.ACCESS_BASIC
            R.id.chipProfileAdvanced -> Profile.ACCESS_ADVANCED
            else -> {
                val chip = binding.chipGroupProfile.findViewById<Chip>(chipId)
                val key = chip?.tag as? String ?: return null
                viewModel.customProfiles.value.find { it.key == key }
            }
        }
    }

    // ── Add profile dialog ───────────────────────────────────────────────────────

    private fun showAddProfileDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Profile name"
            setSingleLine()
            setPadding(48, 24, 48, 8)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("New Profile")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val profile = viewModel.addCustomProfile(name)
                    viewModel.setProfile(profile)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Navigation ───────────────────────────────────────────────────────────────

    private fun navigateToTutorial(mode: DeviceMode) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToTutorialFragment(mode)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
