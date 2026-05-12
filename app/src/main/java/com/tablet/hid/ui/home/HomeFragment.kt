package com.tablet.hid.ui.home

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ContextThemeWrapper
import com.google.android.material.button.MaterialButton
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
import com.tablet.hid.model.HidHost
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

        // ── Community card ───────────────────────────────────────────────────
        binding.cardCommunity.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_community)
        }

        // ── Connection card ──────────────────────────────────────────────────
        updateDeviceNameChip()
        binding.chipDeviceName.setOnClickListener { showEditNameDialog() }

        binding.btnHomeDiscoverable.setOnClickListener {
            viewModel.startServiceForMode(requireContext(), DeviceMode.TOUCH_MOUSE)
        }
        binding.btnPendingAllow.setOnClickListener { viewModel.approvePendingConnection() }
        binding.btnPendingIgnore.setOnClickListener { viewModel.rejectPendingConnection() }

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

        // ── Observe known hosts ──────────────────────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.knownHosts.collect { hosts -> rebuildKnownHostRows(hosts) }
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
        val pending = state is BleHidManager.State.PendingApproval

        binding.homeLedStatus.backgroundTintList = ColorStateList.valueOf(
            requireContext().getColor(if (connected) R.color.led_connected else R.color.led_disconnected)
        )
        binding.homeConnStatus.text = when (state) {
            is BleHidManager.State.Idle               -> getString(R.string.status_disconnected)
            is BleHidManager.State.Registering        -> getString(R.string.home_status_connecting)
            is BleHidManager.State.WaitingForConnection -> getString(R.string.home_status_waiting)
            is BleHidManager.State.Reconnecting       -> getString(R.string.tutorial_status_reconnecting, state.deviceName)
            is BleHidManager.State.PendingApproval    -> getString(R.string.home_status_pending_approval)
            is BleHidManager.State.Connected          -> getString(R.string.status_connected)
            is BleHidManager.State.Error              -> getString(R.string.tutorial_status_error, state.message)
        }

        // Pending approval card
        binding.pendingApprovalCard.isVisible = pending
        if (pending) {
            binding.pendingDeviceName.text = (state as BleHidManager.State.PendingApproval).deviceName
        }

        // Discoverable button: visible when idle, label changes based on known hosts
        binding.btnHomeDiscoverable.isVisible = idle
        if (idle) {
            val hasHosts = viewModel.knownHosts.value.isNotEmpty()
            binding.btnHomeDiscoverable.text = getString(
                if (hasHosts) R.string.btn_new_pair else R.string.btn_make_discoverable
            )
        }
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

    // ── Known hosts list ─────────────────────────────────────────────────────────

    private fun rebuildKnownHostRows(hosts: List<HidHost>) {
        val container = binding.knownHostsContainer
        val label = binding.knownHostsLabel
        container.removeAllViews()
        val hasHosts = hosts.isNotEmpty()
        container.isVisible = hasHosts
        label.isVisible = hasHosts
        hosts.forEach { host -> container.addView(buildHostRow(host)) }
        // Refresh button label if currently idle
        val state = viewModel.state.value
        if (state is BleHidManager.State.Idle || state is BleHidManager.State.Error) {
            binding.btnHomeDiscoverable.text = getString(
                if (hasHosts) R.string.btn_new_pair else R.string.btn_make_discoverable
            )
        }
    }

    private fun buildHostRow(host: HidHost): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Weight=3 keeps the name dominant even when it's long.
        val nameView = TextView(ctx).apply {
            text = host.displayName
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        row.addView(nameView)

        fun textBtn(label: String, cd: String, onClick: () -> Unit): MaterialButton =
            MaterialButton(ctx, null, android.R.attr.borderlessButtonStyle).apply {
                text = label
                contentDescription = cd
                insetTop = 0
                insetBottom = 0
                setOnClickListener { onClick() }
            }

        row.addView(textBtn(getString(R.string.btn_reconnect_short), getString(R.string.home_cd_reconnect)) {
            viewModel.startServiceForMode(ctx, DeviceMode.TOUCH_MOUSE, host.address)
        })
        row.addView(textBtn(getString(R.string.home_btn_rename), getString(R.string.home_cd_rename)) {
            showRenameHostDialog(host)
        })

        val forgetBtn = MaterialButton(ctx, null, R.attr.forgetButtonStyle).apply {
            text = getString(R.string.home_btn_forget)
            contentDescription = getString(R.string.home_cd_forget)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { viewModel.forgetHost(host) }
        }
        row.addView(forgetBtn)

        return row
    }

    private fun showRenameHostDialog(host: HidHost) {
        val input = EditText(requireContext()).apply {
            setText(host.alias ?: "")
            hint = getString(R.string.hint_device_label)
            setSingleLine()
            setPadding(48, 24, 48, 8)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(host.displayName)
            .setMessage(getString(R.string.dialog_device_options_message))
            .setView(input)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val alias = input.text.toString().trim().ifEmpty { null }
                viewModel.renameHost(host.address, alias)
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
                    viewModel.startServiceForMode(requireContext(), mode, lastAddr)
                    findNavController().navigate(directAction)
                } else if (viewModel.knownHosts.value.isNotEmpty()) {
                    // At least one known host — reconnect to the most recently seen
                    val host = viewModel.knownHosts.value.maxByOrNull { it.lastSeenMs }!!
                    viewModel.startServiceForMode(requireContext(), mode, host.address)
                    findNavController().navigate(directAction)
                } else {
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
