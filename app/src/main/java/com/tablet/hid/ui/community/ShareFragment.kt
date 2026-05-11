package com.tablet.hid.ui.community

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tablet.hid.R
import com.tablet.hid.databinding.FragmentShareBinding
import com.tablet.hid.model.Profile
import com.tablet.hid.util.GamepadConfigStore
import com.tablet.hid.util.ProfileStore
import com.tablet.hid.util.TouchMouseConfigStore

class ShareFragment : Fragment() {

    private var _binding: FragmentShareBinding? = null
    private val binding get() = _binding!!

    private var activeProfile: Profile = Profile.DEFAULT

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentShareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activeProfile = ProfileStore.getActiveProfile(requireContext())
        updateProfileChip()
        loadAndDisplayConfigs()

        binding.chipShareProfile.setOnClickListener { showProfilePicker() }

        binding.btnShareGamepad.setOnClickListener {
            showUploadSheet("gamepad")
        }

        binding.btnShareTouchMouse.setOnClickListener {
            showUploadSheet("touch_mouse")
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh in case active profile changed elsewhere
        activeProfile = ProfileStore.getActiveProfile(requireContext())
        updateProfileChip()
        loadAndDisplayConfigs()
    }

    private fun updateProfileChip() {
        binding.chipShareProfile.text = activeProfile.name
    }

    private fun loadAndDisplayConfigs() {
        val ctx = requireContext()

        // ── Gamepad summary ───────────────────────────────────────────────────
        val gamepadConfig = GamepadConfigStore.load(ctx, activeProfile)
        val enabledButtons = listOf(
            gamepadConfig.btnA, gamepadConfig.btnB, gamepadConfig.btnX, gamepadConfig.btnY,
            gamepadConfig.btnLb, gamepadConfig.btnRb, gamepadConfig.btnLt, gamepadConfig.btnRt,
            gamepadConfig.btnBack, gamepadConfig.btnStart,
            gamepadConfig.dpadUp, gamepadConfig.dpadDown, gamepadConfig.dpadLeft, gamepadConfig.dpadRight,
        ).count { it.enabled }
        binding.textGamepadButtons.text = getString(R.string.community_gamepad_buttons, enabledButtons)
        binding.textGamepadMacros.text = getString(R.string.community_gamepad_macros, gamepadConfig.macroButtons.size)
        binding.textGamepadSingleJoystick.text = if (gamepadConfig.singleJoystickMode) {
            getString(R.string.community_gamepad_single_joystick_on)
        } else {
            getString(R.string.community_gamepad_single_joystick_off)
        }

        // ── Touch Mouse summary ───────────────────────────────────────────────
        val touchConfig = TouchMouseConfigStore.load(ctx, activeProfile)
        binding.textTouchMouseMode.text = getString(
            R.string.community_touch_mouse_mode_sensitivity,
            touchConfig.mode.name.lowercase().replaceFirstChar { it.uppercase() },
            touchConfig.sensitivity,
        )
        binding.textTouchMouseZones.text = getString(
            R.string.community_touch_mouse_zones,
            touchConfig.leftButton.zoneType.name.lowercase().replaceFirstChar { it.uppercase() },
            touchConfig.rightButton.zoneType.name.lowercase().replaceFirstChar { it.uppercase() },
        )
        binding.textTouchMouseMacros.text = getString(
            R.string.community_touch_mouse_macros,
            touchConfig.macroButtons.size,
        )
    }

    private fun showProfilePicker() {
        val ctx = requireContext()
        val allProfiles = Profile.BUILT_INS + ProfileStore.getCustomProfiles(ctx)
        val names = allProfiles.map { it.name }.toTypedArray()
        val currentIndex = allProfiles.indexOfFirst { it.key == activeProfile.key }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.community_profile_label))
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                activeProfile = allProfiles[which]
                updateProfileChip()
                loadAndDisplayConfigs()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showUploadSheet(mode: String) {
        val sheet = UploadSheet()
        sheet.arguments = bundleOf(
            UploadSheet.ARG_MODE to mode,
            UploadSheet.ARG_PROFILE_KEY to activeProfile.key,
        )
        sheet.show(childFragmentManager, "upload_sheet")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
