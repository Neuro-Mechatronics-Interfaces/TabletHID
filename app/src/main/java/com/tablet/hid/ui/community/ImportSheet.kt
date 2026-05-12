package com.tablet.hid.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tablet.hid.HidViewModel
import com.tablet.hid.R
import com.tablet.hid.databinding.SheetImportBinding
import com.tablet.hid.model.CommunityConfigRecord
import com.tablet.hid.model.GamepadConfig
import com.tablet.hid.model.OrientationPreference
import com.tablet.hid.model.Profile
import com.tablet.hid.util.ConfigApiClient
import com.tablet.hid.util.ConfigMerger
import com.tablet.hid.util.GamepadConfigSerializer
import com.tablet.hid.util.GamepadConfigStore
import com.tablet.hid.util.LayoutRescaler
import com.tablet.hid.util.ProfileStore
import com.tablet.hid.util.TouchMouseConfigSerializer
import com.tablet.hid.util.TouchMouseConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ImportSheet : BottomSheetDialogFragment() {

    companion object {
        const val ARG_RECORD_JSON = "record_json"
        const val REQUEST_APPLY = "import_apply"
        const val KEY_PROFILE   = "profile"
        const val KEY_REFRESH   = "refresh"
    }

    private var _binding: SheetImportBinding? = null
    private val binding get() = _binding!!

    private val hidViewModel: HidViewModel by activityViewModels()

    private lateinit var record: CommunityConfigRecord
    private var targetProfile: Profile = Profile.DEFAULT

    // Guards preset-chip listener from reacting to programmatic checkbox changes.
    private var applyingPreset = false

    private var thumbnailLandscape = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = SheetImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── Parse record from bundle ─────────────────────────────────────────
        val json = requireArguments().getString(ARG_RECORD_JSON) ?: run { dismiss(); return }
        record = parseRecord(JSONObject(json))

        // ── Initialize target profile ────────────────────────────────────────
        targetProfile = ProfileStore.getActiveProfile(requireContext())

        // ── Populate metadata ────────────────────────────────────────────────
        binding.importProfileName.text = record.profileName
        binding.importModeLabel.text = if (record.mode == "gamepad") {
            getString(R.string.community_mode_note_gamepad)
        } else {
            getString(R.string.community_mode_note_touch_mouse)
        }

        val deviceName = record.deviceName ?: ""
        val osVersion = record.deviceOsVersion ?: ""
        val diagIn = record.deviceScreenDiagonalIn
        val osLine = buildString {
            if (record.platform.isNotBlank()) append(record.platform.replaceFirstChar { it.uppercase() })
            if (osVersion.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(osVersion)
            }
            if (diagIn != null && diagIn > 0f) {
                if (isNotEmpty()) append(" · ")
                append("${"%.1f".format(diagIn)}\"")
            }
        }
        binding.importDeviceLine.text = if (deviceName.isNotBlank()) "$deviceName — $osLine" else osLine
        binding.importDownloads.text = getString(R.string.community_downloads, record.downloadCount)

        val desc = record.description
        if (!desc.isNullOrBlank()) {
            binding.importDescription.text = desc
            binding.importDescription.isVisible = true
        }

        // ── Gamepad layout preview ───────────────────────────────────────────
        if (record.mode == "gamepad" && record.configJson.isNotBlank()) {
            // Init orientation from embedded preference; default to landscape when SYSTEM
            thumbnailLandscape = try {
                JSONObject(record.configJson).optString("orientationPreference", "SYSTEM") != "PORTRAIT"
            } catch (_: Exception) { true }

            // Rescale layout to this device's canvas if source device info is present.
            // All canvas dims are derived in the config's intended orientation so the
            // layout resolver sees the correct axis sizes for the rescaling math.
            val metrics = resources.displayMetrics
            val (tgtLong, tgtShort) = LayoutRescaler.canvasDimsFromMetrics(metrics)
            val tgtW = if (thumbnailLandscape) tgtLong else tgtShort
            val tgtH = if (thumbnailLandscape) tgtShort else tgtLong
            val srcWpx = record.deviceScreenWidthPx
            val srcHpx = record.deviceScreenHeightPx
            val srcDpi = record.deviceScreenDensityDpi
            val needsRescale = srcWpx != null && srcHpx != null && srcDpi != null &&
                run {
                    val (srcLong, srcShort) = LayoutRescaler.canvasDimsFromScreenPx(srcWpx, srcHpx, srcDpi)
                    val srcW = if (thumbnailLandscape) srcLong else srcShort
                    val srcH = if (thumbnailLandscape) srcShort else srcLong
                    kotlin.math.abs(srcW - tgtW) > 1f || kotlin.math.abs(srcH - tgtH) > 1f
                }
            if (needsRescale) {
                val (srcLong, srcShort) = LayoutRescaler.canvasDimsFromScreenPx(srcWpx!!, srcHpx!!, srcDpi!!)
                val srcW = if (thumbnailLandscape) srcLong else srcShort
                val srcH = if (thumbnailLandscape) srcShort else srcLong
                val srcConfig = GamepadConfigSerializer.fromCanonicalJson(JSONObject(record.configJson))
                val rescaled = LayoutRescaler.rescaleGamepad(requireContext(), srcConfig, srcW, srcH, tgtW, tgtH)
                // Store canonical landscape dims; refW/H in the view will orient them via isLandscape.
                binding.gamepadThumbnail.setCanvasDims(tgtLong, tgtShort)
                binding.gamepadThumbnail.setLandscape(thumbnailLandscape)
                binding.gamepadThumbnail.setConfig(rescaled)
            } else {
                binding.gamepadThumbnail.setLandscape(thumbnailLandscape)
                binding.gamepadThumbnail.setConfigJson(record.configJson)
            }
            binding.gamepadThumbnail.isVisible = true
            binding.thumbnailOrientRow.isVisible = true
            updateOrientToggleLabel()
            binding.btnThumbnailOrient.setOnClickListener {
                thumbnailLandscape = !thumbnailLandscape
                binding.gamepadThumbnail.setLandscape(thumbnailLandscape)
                updateOrientToggleLabel()
            }
        }

        // ── Profile target chip ──────────────────────────────────────────────
        binding.chipImportProfile.text = targetProfile.name
        binding.chipImportProfile.setOnClickListener { showProfilePicker() }

        // ── Show correct subset group ────────────────────────────────────────
        val isGamepad = record.mode == "gamepad"
        binding.groupGamepadSubsets.isVisible = isGamepad
        binding.groupTouchMouseSubsets.isVisible = !isGamepad

        // ── Preset chip listeners ────────────────────────────────────────────
        binding.chipGroupPresets.setOnCheckedStateChangeListener { _, checkedIds ->
            if (applyingPreset) return@setOnCheckedStateChangeListener
            when (checkedIds.firstOrNull()) {
                R.id.chipPresetEverything -> applyPreset(PresetKind.EVERYTHING)
                R.id.chipPresetLayout    -> applyPreset(PresetKind.LAYOUT)
                R.id.chipPresetMacros    -> applyPreset(PresetKind.MACROS)
                R.id.chipPresetBehaviors -> applyPreset(PresetKind.BEHAVIORS)
                // Custom: no auto-change of checkboxes
            }
        }

        // Default to "Everything" preset
        applyPreset(PresetKind.EVERYTHING)
        binding.chipGroupPresets.check(R.id.chipPresetEverything)

        // ── Manual checkbox listeners — switch to Custom if preset no longer matches ──
        val checkboxes = if (isGamepad) gamepadCheckboxes() else touchMouseCheckboxes()
        checkboxes.forEach { cb ->
            cb.setOnCheckedChangeListener { _, _ ->
                if (!applyingPreset) syncPresetChip()
            }
        }

        // ── Apply button ─────────────────────────────────────────────────────
        binding.btnApplyImport.setOnClickListener { performApply() }
    }

    private fun updateOrientToggleLabel() {
        binding.btnThumbnailOrient.text = if (thumbnailLandscape) "⟷ Landscape" else "↕ Portrait"
    }

    private fun applyPreset(kind: PresetKind) {
        applyingPreset = true
        val isGamepad = record.mode == "gamepad"
        if (isGamepad) {
            binding.checkControlLayout.isChecked   = kind.gamepadSubsets.contains(ConfigMerger.GamepadSubset.GAMEPAD_CONTROL_LAYOUT)
            binding.checkButtonBehavior.isChecked  = kind.gamepadSubsets.contains(ConfigMerger.GamepadSubset.GAMEPAD_BUTTON_BEHAVIOR)
            binding.checkJoystickSettings.isChecked = kind.gamepadSubsets.contains(ConfigMerger.GamepadSubset.GAMEPAD_JOYSTICK_SETTINGS)
            binding.checkGamepadMacros.isChecked   = kind.gamepadSubsets.contains(ConfigMerger.GamepadSubset.GAMEPAD_MACROS)
            binding.checkButtonLabels.isChecked    = kind.gamepadSubsets.contains(ConfigMerger.GamepadSubset.GAMEPAD_LABELS)
            binding.checkVibration.isChecked       = kind.gamepadSubsets.contains(ConfigMerger.GamepadSubset.GAMEPAD_VIBRATION)
        } else {
            binding.checkZonePositions.isChecked      = kind.touchMouseSubsets.contains(ConfigMerger.TouchMouseSubset.TOUCH_ZONE_POSITIONS)
            binding.checkSensitivity.isChecked        = kind.touchMouseSubsets.contains(ConfigMerger.TouchMouseSubset.TOUCH_SENSITIVITY)
            binding.checkTouchButtonBehavior.isChecked = kind.touchMouseSubsets.contains(ConfigMerger.TouchMouseSubset.TOUCH_BUTTON_BEHAVIOR)
            binding.checkTouchMacros.isChecked        = kind.touchMouseSubsets.contains(ConfigMerger.TouchMouseSubset.TOUCH_MACROS)
        }
        applyingPreset = false
    }

    private fun syncPresetChip() {
        val isGamepad = record.mode == "gamepad"
        val matchedPreset = PresetKind.values().firstOrNull { kind ->
            if (isGamepad) {
                binding.checkControlLayout.isChecked   == kind.gamepadSubsets.contains(ConfigMerger.GamepadSubset.GAMEPAD_CONTROL_LAYOUT) &&
                binding.checkButtonBehavior.isChecked  == kind.gamepadSubsets.contains(ConfigMerger.GamepadSubset.GAMEPAD_BUTTON_BEHAVIOR) &&
                binding.checkJoystickSettings.isChecked == kind.gamepadSubsets.contains(ConfigMerger.GamepadSubset.GAMEPAD_JOYSTICK_SETTINGS) &&
                binding.checkGamepadMacros.isChecked   == kind.gamepadSubsets.contains(ConfigMerger.GamepadSubset.GAMEPAD_MACROS) &&
                binding.checkButtonLabels.isChecked    == kind.gamepadSubsets.contains(ConfigMerger.GamepadSubset.GAMEPAD_LABELS) &&
                binding.checkVibration.isChecked       == kind.gamepadSubsets.contains(ConfigMerger.GamepadSubset.GAMEPAD_VIBRATION)
            } else {
                binding.checkZonePositions.isChecked      == kind.touchMouseSubsets.contains(ConfigMerger.TouchMouseSubset.TOUCH_ZONE_POSITIONS) &&
                binding.checkSensitivity.isChecked        == kind.touchMouseSubsets.contains(ConfigMerger.TouchMouseSubset.TOUCH_SENSITIVITY) &&
                binding.checkTouchButtonBehavior.isChecked == kind.touchMouseSubsets.contains(ConfigMerger.TouchMouseSubset.TOUCH_BUTTON_BEHAVIOR) &&
                binding.checkTouchMacros.isChecked        == kind.touchMouseSubsets.contains(ConfigMerger.TouchMouseSubset.TOUCH_MACROS)
            }
        }
        applyingPreset = true
        if (matchedPreset != null) {
            val chipId = when (matchedPreset) {
                PresetKind.EVERYTHING -> R.id.chipPresetEverything
                PresetKind.LAYOUT     -> R.id.chipPresetLayout
                PresetKind.MACROS     -> R.id.chipPresetMacros
                PresetKind.BEHAVIORS  -> R.id.chipPresetBehaviors
                PresetKind.CUSTOM     -> R.id.chipPresetCustom
            }
            binding.chipGroupPresets.check(chipId)
        } else {
            binding.chipGroupPresets.check(R.id.chipPresetCustom)
        }
        applyingPreset = false
    }

    private fun rescaleIfNeeded(ctx: android.content.Context, config: GamepadConfig, rec: CommunityConfigRecord): GamepadConfig {
        val srcWpx = rec.deviceScreenWidthPx ?: return config
        val srcHpx = rec.deviceScreenHeightPx ?: return config
        val srcDpi = rec.deviceScreenDensityDpi ?: return config
        val isLandscape = config.orientationPreference != OrientationPreference.PORTRAIT
        val (srcLong, srcShort) = LayoutRescaler.canvasDimsFromScreenPx(srcWpx, srcHpx, srcDpi)
        val srcW = if (isLandscape) srcLong else srcShort
        val srcH = if (isLandscape) srcShort else srcLong
        val (tgtLong, tgtShort) = LayoutRescaler.canvasDimsFromMetrics(ctx.resources.displayMetrics)
        val tgtW = if (isLandscape) tgtLong else tgtShort
        val tgtH = if (isLandscape) tgtShort else tgtLong
        return LayoutRescaler.rescaleGamepad(ctx, config, srcW, srcH, tgtW, tgtH)
    }

    private fun performApply() {
        val ctx = requireContext()
        val profile = targetProfile

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    var shouldRefresh = false
                    val importRecord = ConfigApiClient.fetchConfig(record.id).fold(
                        onSuccess = {
                            shouldRefresh = true
                            it
                        },
                        onFailure = { record },
                    )
                    val configJsonObj = JSONObject(importRecord.configJson)
                    if (importRecord.mode == "gamepad") {
                        val source = rescaleIfNeeded(
                            ctx,
                            GamepadConfigSerializer.fromCanonicalJson(configJsonObj),
                            importRecord,
                        )
                        val target = GamepadConfigStore.load(ctx, profile)
                        val subsets = buildSet {
                            if (binding.checkControlLayout.isChecked)   add(ConfigMerger.GamepadSubset.GAMEPAD_CONTROL_LAYOUT)
                            if (binding.checkButtonBehavior.isChecked)  add(ConfigMerger.GamepadSubset.GAMEPAD_BUTTON_BEHAVIOR)
                            if (binding.checkJoystickSettings.isChecked) add(ConfigMerger.GamepadSubset.GAMEPAD_JOYSTICK_SETTINGS)
                            if (binding.checkGamepadMacros.isChecked)   add(ConfigMerger.GamepadSubset.GAMEPAD_MACROS)
                            if (binding.checkButtonLabels.isChecked)    add(ConfigMerger.GamepadSubset.GAMEPAD_LABELS)
                            if (binding.checkVibration.isChecked)       add(ConfigMerger.GamepadSubset.GAMEPAD_VIBRATION)
                        }
                        val merged = ConfigMerger.mergeGamepad(target, source, subsets)
                        GamepadConfigStore.save(ctx, merged, profile)
                    } else {
                        val source = TouchMouseConfigSerializer.fromCanonicalJson(configJsonObj)
                        val target = TouchMouseConfigStore.load(ctx, profile)
                        val subsets = buildSet {
                            if (binding.checkZonePositions.isChecked)      add(ConfigMerger.TouchMouseSubset.TOUCH_ZONE_POSITIONS)
                            if (binding.checkSensitivity.isChecked)        add(ConfigMerger.TouchMouseSubset.TOUCH_SENSITIVITY)
                            if (binding.checkTouchButtonBehavior.isChecked) add(ConfigMerger.TouchMouseSubset.TOUCH_BUTTON_BEHAVIOR)
                            if (binding.checkTouchMacros.isChecked)        add(ConfigMerger.TouchMouseSubset.TOUCH_MACROS)
                        }
                        val merged = ConfigMerger.mergeTouchMouse(target, source, subsets)
                        TouchMouseConfigStore.save(ctx, merged, profile)
                    }
                    shouldRefresh
                }
            }

            if (result.isSuccess) {
                // If the import targeted the currently active profile, push the saved config
                // into the ViewModel so that GamepadFragment / TouchMouseFragment reflect the
                // new layout immediately without requiring a navigate-away-and-back.
                if (profile.key == hidViewModel.activeProfile.value.key) {
                    hidViewModel.reloadActiveConfigs()
                }
                // Signal the parent fragment to show feedback on its own view, which
                // stays visible after this sheet dismisses.
                parentFragmentManager.setFragmentResult(
                    REQUEST_APPLY,
                    bundleOf(
                        KEY_PROFILE to profile.name,
                        KEY_REFRESH to (result.getOrNull() == true),
                    ),
                )
                dismiss()
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showProfilePicker() {
        val ctx = requireContext()
        val allProfiles = Profile.BUILT_INS + ProfileStore.getCustomProfiles(ctx)
        val names = allProfiles.map { it.name }.toTypedArray()
        val currentIndex = allProfiles.indexOfFirst { it.key == targetProfile.key }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.community_profile_label))
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                targetProfile = allProfiles[which]
                binding.chipImportProfile.text = targetProfile.name
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun gamepadCheckboxes(): List<CheckBox> = listOf(
        binding.checkControlLayout,
        binding.checkButtonBehavior,
        binding.checkJoystickSettings,
        binding.checkGamepadMacros,
        binding.checkButtonLabels,
        binding.checkVibration,
    )

    private fun touchMouseCheckboxes(): List<CheckBox> = listOf(
        binding.checkZonePositions,
        binding.checkSensitivity,
        binding.checkTouchButtonBehavior,
        binding.checkTouchMacros,
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Preset definitions ───────────────────────────────────────────────────

    private enum class PresetKind(
        val gamepadSubsets: Set<ConfigMerger.GamepadSubset>,
        val touchMouseSubsets: Set<ConfigMerger.TouchMouseSubset>,
    ) {
        EVERYTHING(
            gamepadSubsets = ConfigMerger.GamepadSubset.values().toSet(),
            touchMouseSubsets = ConfigMerger.TouchMouseSubset.values().toSet(),
        ),
        LAYOUT(
            gamepadSubsets = setOf(ConfigMerger.GamepadSubset.GAMEPAD_CONTROL_LAYOUT),
            touchMouseSubsets = setOf(ConfigMerger.TouchMouseSubset.TOUCH_ZONE_POSITIONS),
        ),
        MACROS(
            gamepadSubsets = setOf(ConfigMerger.GamepadSubset.GAMEPAD_MACROS),
            touchMouseSubsets = setOf(ConfigMerger.TouchMouseSubset.TOUCH_MACROS),
        ),
        BEHAVIORS(
            gamepadSubsets = setOf(
                ConfigMerger.GamepadSubset.GAMEPAD_BUTTON_BEHAVIOR,
                ConfigMerger.GamepadSubset.GAMEPAD_JOYSTICK_SETTINGS,
            ),
            touchMouseSubsets = setOf(
                ConfigMerger.TouchMouseSubset.TOUCH_BUTTON_BEHAVIOR,
                ConfigMerger.TouchMouseSubset.TOUCH_SENSITIVITY,
            ),
        ),
        CUSTOM(
            gamepadSubsets = emptySet(),
            touchMouseSubsets = emptySet(),
        ),
    }

    // ── Record deserialization from JSON string ───────────────────────────────

    private fun parseRecord(j: JSONObject): CommunityConfigRecord {
        val tags = j.optJSONArray("tags")
        val tagList = if (tags != null) List(tags.length()) { i -> tags.getString(i) } else emptyList()
        return CommunityConfigRecord(
            id = j.optString("id"),
            schemaVersion = j.optInt("schemaVersion", 1),
            platform = j.optString("platform"),
            mode = j.optString("mode"),
            profileName = j.optString("profileName"),
            description = j.optString("description").takeIf { it.isNotEmpty() },
            tags = tagList,
            category = j.optString("category").takeIf { it.isNotEmpty() },
            appVersion = j.optString("appVersion").takeIf { it.isNotEmpty() },
            configJson = j.optString("configJson"),
            uploadedAt = j.optString("uploadedAt"),
            downloadCount = j.optInt("downloadCount", 0),
            deviceName = j.optString("deviceName").takeIf { it.isNotEmpty() },
            deviceHwId = j.optString("deviceHwId").takeIf { it.isNotEmpty() },
            deviceOsVersion = j.optString("deviceOsVersion").takeIf { it.isNotEmpty() },
            deviceOsApiLevel = j.optInt("deviceOsApiLevel", 0).takeIf { it > 0 },
            deviceScreenWidthPx = j.optInt("deviceScreenWidthPx", 0).takeIf { it > 0 },
            deviceScreenHeightPx = j.optInt("deviceScreenHeightPx", 0).takeIf { it > 0 },
            deviceScreenDensityDpi = j.optInt("deviceScreenDensityDpi", 0).takeIf { it > 0 },
            deviceScreenDiagonalIn = j.optDouble("deviceScreenDiagonalIn", 0.0).toFloat().takeIf { it > 0f },
        )
    }
}
