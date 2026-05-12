package com.tablet.hid.ui.settings

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.tablet.hid.HidViewModel
import com.tablet.hid.databinding.FragmentSettingsBinding
import com.tablet.hid.util.AppearanceStore
import com.tablet.hid.util.HidPrefs
import com.tablet.hid.util.LoggingStore
import com.tablet.hid.util.OrientationStore
import com.tablet.hid.util.UiPaletteStore

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HidViewModel by activityViewModels()

    // Capture originals before any edits so we know whether recreate() is needed.
    private var originalLargeText    = false
    private var originalHighContrast = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()

        originalLargeText    = AppearanceStore.isLargeText(ctx)
        originalHighContrast = AppearanceStore.isHighContrast(ctx)

        // ── Bluetooth ────────────────────────────────────────────────────────────
        binding.editDeviceName.inputType =
            InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_CAP_WORDS or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        binding.editDeviceName.setText(AppearanceStore.getDeviceName(ctx))
        binding.switchAutoReconnect.isChecked = HidPrefs.isAutoReconnectEnabled(ctx)

        // ── Appearance ───────────────────────────────────────────────────────────
        when (AppearanceStore.get(ctx)) {
            AppearanceStore.INDEX_LIGHT  -> binding.radioAppearanceLight.isChecked  = true
            AppearanceStore.INDEX_DARK   -> binding.radioAppearanceDark.isChecked   = true
            else                          -> binding.radioAppearanceSystem.isChecked = true
        }
        binding.switchLargeText.isChecked    = originalLargeText
        binding.switchHighContrast.isChecked = originalHighContrast

        // ── Session Logging ──────────────────────────────────────────────────────
        binding.switchSessionLogging.isChecked = LoggingStore.isEnabled(ctx)
        binding.textLoggingPath.text =
            "On each connection a .config snapshot and a timestamped .log of all HID events are " +
            "written to:\n${LoggingStore.sessionDirDisplayPath(ctx)}"

        // ── Orientation Lock ─────────────────────────────────────────────────────
        when (OrientationStore.get(ctx)) {
            OrientationStore.PORTRAIT  -> binding.radioOrientationPortrait.isChecked  = true
            OrientationStore.LANDSCAPE -> binding.radioOrientationLandscape.isChecked = true
            else                        -> binding.radioOrientationSystem.isChecked   = true
        }

        // ── Gaming Colors ────────────────────────────────────────────────────────
        when (UiPaletteStore.getIndex(ctx)) {
            1    -> binding.radioPaletteNeon.isChecked       = true
            2    -> binding.radioPaletteFire.isChecked       = true
            3    -> binding.radioPaletteIce.isChecked        = true
            4    -> binding.radioPaletteMonochrome.isChecked = true
            else -> binding.radioPaletteDefault.isChecked    = true
        }

        // ── Screen Pinning ───────────────────────────────────────────────────────
        binding.switchScreenPinning.isChecked = HidPrefs.isScreenPinningEnabled(ctx)

        // ── Save ─────────────────────────────────────────────────────────────────
        binding.btnSaveSettings.setOnClickListener { applyAndExit() }
    }

    private fun applyAndExit() {
        val ctx = requireContext()

        AppearanceStore.setDeviceName(ctx, binding.editDeviceName.text.toString())
        HidPrefs.setAutoReconnectEnabled(ctx, binding.switchAutoReconnect.isChecked)

        val newAppearance = when {
            binding.radioAppearanceLight.isChecked -> AppearanceStore.INDEX_LIGHT
            binding.radioAppearanceDark.isChecked  -> AppearanceStore.INDEX_DARK
            else                                    -> AppearanceStore.INDEX_SYSTEM
        }
        AppearanceStore.set(ctx, newAppearance)
        AppCompatDelegate.setDefaultNightMode(AppearanceStore.toNightMode(newAppearance))

        val newLargeText    = binding.switchLargeText.isChecked
        val newHighContrast = binding.switchHighContrast.isChecked
        AppearanceStore.setLargeText(ctx, newLargeText)
        AppearanceStore.setHighContrast(ctx, newHighContrast)

        val loggingEnabled = binding.switchSessionLogging.isChecked
        LoggingStore.setEnabled(ctx, loggingEnabled)
        viewModel.setLoggingEnabled(loggingEnabled)

        val newOrientation = when {
            binding.radioOrientationPortrait.isChecked  -> OrientationStore.PORTRAIT
            binding.radioOrientationLandscape.isChecked -> OrientationStore.LANDSCAPE
            else                                         -> OrientationStore.SYSTEM
        }
        OrientationStore.set(ctx, newOrientation)
        requireActivity().requestedOrientation = OrientationStore.toActivityOrientation(newOrientation)

        val paletteIndex = when {
            binding.radioPaletteNeon.isChecked       -> 1
            binding.radioPaletteFire.isChecked       -> 2
            binding.radioPaletteIce.isChecked        -> 3
            binding.radioPaletteMonochrome.isChecked -> 4
            else                                     -> 0
        }
        UiPaletteStore.setIndex(ctx, paletteIndex)

        HidPrefs.setScreenPinningEnabled(ctx, binding.switchScreenPinning.isChecked)

        if (newLargeText != originalLargeText || newHighContrast != originalHighContrast) {
            requireActivity().recreate()
        } else {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
