package com.tablet.hid.ui.onboarding

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.shape.MaterialShapeDrawable
import com.tablet.hid.R
import com.tablet.hid.databinding.FragmentOnboardingBinding
import com.tablet.hid.util.AppearanceStore

class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private val totalPages = 4
    private var currentPage = 0

    private data class PageData(
        val iconRes: Int,
        val titleRes: Int,
        val bodyRes: Int,
        val showNameInput: Boolean = false
    )

    private val pages = listOf(
        PageData(R.drawable.ic_hand,     R.string.onboarding_p1_title, R.string.onboarding_p1_body),
        PageData(R.drawable.ic_settings, R.string.onboarding_p2_title, R.string.onboarding_p2_body),
        PageData(R.drawable.ic_mouse,    R.string.onboarding_p3_title, R.string.onboarding_p3_body),
        PageData(R.drawable.ic_person,   R.string.onboarding_p4_title, R.string.onboarding_p4_body, showNameInput = true)
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buildDots()
        showPage(0)

        binding.btnSkip.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener {
            if (currentPage > 0) showPage(currentPage - 1)
        }
        binding.btnNext.setOnClickListener {
            if (currentPage < totalPages - 1) {
                showPage(currentPage + 1)
            } else {
                finish()
            }
        }

        binding.editDeviceName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { finish(); true } else false
        }

        // Consume back press — don't let users bypass onboarding accidentally
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentPage > 0) showPage(currentPage - 1)
                    // else: swallow — can't go back past page 0
                }
            }
        )
    }

    private fun showPage(index: Int) {
        currentPage = index
        val page = pages[index]

        binding.imgPage.setImageResource(page.iconRes)
        binding.txtTitle.text = getString(page.titleRes)

        val bodyText = getString(page.bodyRes)
        binding.txtBody.text = Html.fromHtml(bodyText, Html.FROM_HTML_MODE_COMPACT)

        binding.layoutDeviceName.isVisible = page.showNameInput
        if (page.showNameInput) {
            val current = AppearanceStore.getDeviceName(requireContext())
            if (binding.editDeviceName.text.isNullOrEmpty()) {
                binding.editDeviceName.setText(current)
            }
        }

        binding.btnSkip.isVisible = index < totalPages - 1
        binding.btnBack.isVisible = index > 0
        binding.btnNext.text = getString(
            if (index == totalPages - 1) R.string.onboarding_get_started else R.string.onboarding_next
        )

        updateDots(index)
    }

    private fun buildDots() {
        val size = resources.getDimensionPixelSize(R.dimen.onboarding_dot_size)
        val gap  = resources.getDimensionPixelSize(R.dimen.onboarding_dot_gap)
        repeat(totalPages) {
            val dot = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).also { lp ->
                    lp.marginEnd = gap
                }
                background = MaterialShapeDrawable(
                    ShapeAppearanceModel.builder().setAllCornerSizes(size / 2f).build()
                ).also { d -> d.fillColor = dotColor(false) }
            }
            binding.dotContainer.addView(dot)
        }
    }

    private fun updateDots(active: Int) {
        for (i in 0 until binding.dotContainer.childCount) {
            val dot = binding.dotContainer.getChildAt(i) as? ImageView ?: continue
            (dot.background as? MaterialShapeDrawable)?.fillColor = dotColor(i == active)
        }
    }

    private fun dotColor(active: Boolean): android.content.res.ColorStateList {
        val ctx = requireContext()
        val color = if (active) {
            com.google.android.material.R.attr.colorPrimary
        } else {
            com.google.android.material.R.attr.colorOutlineVariant
        }
        val resolvedColor = com.google.android.material.color.MaterialColors.getColor(ctx, color, 0)
        return android.content.res.ColorStateList.valueOf(resolvedColor)
    }

    private fun finish() {
        val nameInput = binding.editDeviceName.text?.toString()?.trim()
        if (!nameInput.isNullOrEmpty()) {
            AppearanceStore.setDeviceName(requireContext(), nameInput)
        }
        AppearanceStore.setOnboardingComplete(requireContext())
        findNavController().navigate(R.id.action_onboarding_to_home)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
