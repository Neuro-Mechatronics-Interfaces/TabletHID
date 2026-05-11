package com.tablet.hid.ui.community

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.tablet.hid.R
import com.tablet.hid.databinding.SheetUploadBinding
import com.tablet.hid.model.CommunityUploadBody
import com.tablet.hid.model.Profile
import com.tablet.hid.util.GamepadConfigSerializer
import com.tablet.hid.util.GamepadConfigStore
import com.tablet.hid.util.ProfileStore
import com.tablet.hid.util.TouchMouseConfigSerializer
import com.tablet.hid.util.TouchMouseConfigStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class UploadSheet : BottomSheetDialogFragment() {

    companion object {
        const val ARG_MODE = "mode"
        const val ARG_PROFILE_KEY = "profile_key"
    }

    private var _binding: SheetUploadBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CommunityViewModel by viewModels(ownerProducer = { requireParentFragment().requireParentFragment() })

    private lateinit var mode: String
    private lateinit var profile: Profile

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = SheetUploadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mode = requireArguments().getString(ARG_MODE, "gamepad")
        val profileKey = requireArguments().getString(ARG_PROFILE_KEY, Profile.DEFAULT.key)
        val customProfiles = ProfileStore.getCustomProfiles(requireContext())
        profile = Profile.fromKey(profileKey, customProfiles)

        // ── Title ────────────────────────────────────────────────────────────
        val modeLabel = if (mode == "gamepad") "Gamepad" else "Touch Mouse"
        binding.uploadTitle.text = getString(R.string.community_upload_title, profile.name, modeLabel)

        // ── Pre-fill profile name ────────────────────────────────────────────
        binding.etUploadProfileName.setText(profile.name)

        // ── Device info (auto-filled, read-only display) ─────────────────────
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val osVersion = Build.VERSION.RELEASE
        binding.textUploadDevice.text = getString(R.string.community_upload_device, deviceModel)
        binding.textUploadOs.text = getString(R.string.community_upload_os, osVersion)

        // ── Buttons ──────────────────────────────────────────────────────────
        binding.btnUploadCancel.setOnClickListener { dismiss() }
        binding.btnUploadSubmit.setOnClickListener { performUpload() }
    }

    private fun performUpload() {
        val ctx = requireContext()
        val profileName = binding.etUploadProfileName.text?.toString()?.trim()
            ?.takeIf { it.isNotBlank() } ?: profile.name
        val description = binding.etUploadDescription.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val tagsRaw = binding.etUploadTags.text?.toString()?.trim() ?: ""
        val tags = if (tagsRaw.isBlank()) emptyList()
                   else tagsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val category = binding.etUploadCategory.text?.toString()?.trim()?.takeIf { it.isNotBlank() }

        // Serialize config to canonical JSON
        val configJson: Any = try {
            if (mode == "gamepad") {
                GamepadConfigSerializer.toCanonicalJson(GamepadConfigStore.load(ctx, profile))
            } else {
                TouchMouseConfigSerializer.toCanonicalJson(TouchMouseConfigStore.load(ctx, profile))
            }
        } catch (e: Exception) {
            Snackbar.make(binding.root, e.message ?: "Serialization error", Snackbar.LENGTH_LONG).show()
            return
        }

        // Compute screen diagonal
        val dm = resources.displayMetrics
        val widthPx = dm.widthPixels
        val heightPx = dm.heightPixels
        val xdpi = dm.xdpi
        val ydpi = dm.ydpi
        // diagonalIn is available if the server schema is extended with that field
        @Suppress("UNUSED_VARIABLE")
        val diagonalIn = sqrt((widthPx / xdpi) * (widthPx / xdpi) + (heightPx / ydpi) * (heightPx / ydpi))

        val body = CommunityUploadBody(
            platform = "android",
            mode = mode,
            profileName = profileName,
            configJson = configJson,
            description = description,
            tags = tags,
            category = category,
            appVersion = runCatching {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            }.getOrNull(),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            deviceHwId = Build.MODEL,
            deviceOsVersion = "Android ${Build.VERSION.RELEASE}",
            deviceOsApiLevel = Build.VERSION.SDK_INT,
            deviceScreenWidthPx = widthPx,
            deviceScreenHeightPx = heightPx,
            deviceScreenDensityDpi = dm.densityDpi,
        )

        // Show progress, disable buttons
        binding.uploadProgress.isVisible = true
        binding.btnUploadSubmit.isEnabled = false
        binding.btnUploadCancel.isEnabled = false

        lifecycleScope.launch {
            viewModel.uploadConfig(body)
                .catch { e ->
                    binding.uploadProgress.isVisible = false
                    binding.btnUploadSubmit.isEnabled = true
                    binding.btnUploadCancel.isEnabled = true
                    Snackbar.make(binding.root, e.message ?: "Upload failed", Snackbar.LENGTH_LONG).show()
                }
                .collect { result ->
                    binding.uploadProgress.isVisible = false
                    binding.btnUploadSubmit.isEnabled = true
                    binding.btnUploadCancel.isEnabled = true
                    result.fold(
                        onSuccess = {
                            // Show snackbar on parent fragment's view since this sheet is being dismissed
                            parentFragment?.view?.let { parentView ->
                                Snackbar.make(parentView, getString(R.string.community_upload_success), Snackbar.LENGTH_LONG).show()
                            }
                            dismiss()
                        },
                        onFailure = { e ->
                            Snackbar.make(binding.root, e.message ?: "Upload failed", Snackbar.LENGTH_LONG).show()
                        },
                    )
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
