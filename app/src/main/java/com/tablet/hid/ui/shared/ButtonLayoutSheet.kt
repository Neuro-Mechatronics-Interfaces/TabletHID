package com.tablet.hid.ui.shared

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import com.tablet.hid.databinding.SheetButtonLayoutBinding
import com.tablet.hid.util.UiPaletteStore
import kotlin.math.round

/**
 * Bottom sheet with four sliders (X offset, Y offset, width scale, height scale) for
 * precise layout adjustment of gamepad buttons and macro buttons, as an accessible
 * alternative to drag/pinch gestures.
 *
 * Usage:
 *   ButtonLayoutSheet().apply {
 *       elementTitle = "A"
 *       initialOffsetX = cfg.offsetX; ...
 *       onUpdate = { ox, oy, sx, sy -> /* live preview — update view */ }
 *       onCommit = { ox, oy, sx, sy -> /* called once on dismiss — save config */ }
 *   }.show(childFragmentManager, "layout")
 *
 * If the caller saves config inside onUpdate (e.g. for regular buttons), onCommit can be null.
 * For macro buttons, onUpdate should only move the view; onCommit should save to avoid
 * triggering a re-render that would interrupt the editing session.
 */
class ButtonLayoutSheet : BottomSheetDialogFragment() {

    var elementTitle: String = ""
    var initialOffsetX: Float = 0f
    var initialOffsetY: Float = 0f
    var initialScaleX: Float = 1f
    var initialScaleY: Float = 1f
    var onUpdate: ((offsetX: Float, offsetY: Float, scaleX: Float, scaleY: Float) -> Unit)? = null
    var onCommit: ((offsetX: Float, offsetY: Float, scaleX: Float, scaleY: Float) -> Unit)? = null

    private var _binding: SheetButtonLayoutBinding? = null
    private val binding get() = _binding!!
    private var ignoreChanges = false

    private var xPreviewScale = 0f
    private var yPreviewScale = 0f

    override fun onStart() {
        super.onStart()
        // Remove the scrim so the button being edited stays fully visible above the sheet.
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SheetButtonLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Rounded-rect marker that moves with the X/Y sliders. Color from active palette.
        binding.previewDot.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 6f * resources.displayMetrics.density
            setColor(UiPaletteStore.get(requireContext()).previewDotArgb)
        }
        // Compute how many pixels in the preview correspond to 1 dp of slider travel,
        // once the area is measured.
        binding.previewArea.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding.previewArea.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val hw = binding.previewArea.width  / 2f - binding.previewDot.width  / 2f
                    val hh = binding.previewArea.height / 2f - binding.previewDot.height / 2f
                    xPreviewScale = hw / 400f
                    yPreviewScale = hh / 400f
                    updatePreview()
                }
            }
        )

        binding.sheetTitle.text = elementTitle

        ignoreChanges = true
        applySnapStep(snap = true)
        binding.sliderOffsetX.value = roundToSnapStep(initialOffsetX.coerceIn(-400f, 400f))
        binding.sliderOffsetY.value = roundToSnapStep(initialOffsetY.coerceIn(-400f, 400f))
        binding.sliderScaleX.value  = roundToStep(initialScaleX.coerceIn(0.3f, 3.0f), 0.1f).coerceIn(0.3f, 3.0f)
        binding.sliderScaleY.value  = roundToStep(initialScaleY.coerceIn(0.3f, 3.0f), 0.1f).coerceIn(0.3f, 3.0f)
        ignoreChanges = false

        updateLabels()

        binding.chipSnapToGrid.setOnCheckedChangeListener { _, isChecked ->
            applySnapStep(isChecked)
            updateLabels()
            updatePreview()
            fireUpdate()
        }

        val listener = Slider.OnChangeListener { _, _, _ ->
            if (ignoreChanges) return@OnChangeListener
            updateLabels()
            updatePreview()
            fireUpdate()
        }
        binding.sliderOffsetX.addOnChangeListener(listener)
        binding.sliderOffsetY.addOnChangeListener(listener)
        binding.sliderScaleX.addOnChangeListener(listener)
        binding.sliderScaleY.addOnChangeListener(listener)

        binding.btnLayoutReset.setOnClickListener {
            ignoreChanges = true
            binding.sliderOffsetX.value = 0f
            binding.sliderOffsetY.value = 0f
            binding.sliderScaleX.value  = 1f
            binding.sliderScaleY.value  = 1f
            ignoreChanges = false
            updateLabels()
            updatePreview()
            fireUpdate()
        }
    }

    private fun applySnapStep(snap: Boolean) {
        val step = if (snap) 8f else 2f
        ignoreChanges = true
        val rx = roundToStep(binding.sliderOffsetX.value, step)
        val ry = roundToStep(binding.sliderOffsetY.value, step)
        binding.sliderOffsetX.stepSize = step
        binding.sliderOffsetY.stepSize = step
        binding.sliderOffsetX.value = rx
        binding.sliderOffsetY.value = ry
        ignoreChanges = false
    }

    private fun roundToSnapStep(v: Float) = roundToStep(v, 8f)

    private fun roundToStep(v: Float, step: Float): Float =
        (round(v / step) * step).coerceIn(-400f, 400f)

    private fun updatePreview() {
        if (xPreviewScale == 0f) return
        binding.previewDot.translationX = binding.sliderOffsetX.value * xPreviewScale
        binding.previewDot.translationY = binding.sliderOffsetY.value * yPreviewScale
        binding.previewDot.scaleX = binding.sliderScaleX.value.coerceIn(0.3f, 2.5f)
        binding.previewDot.scaleY = binding.sliderScaleY.value.coerceIn(0.3f, 2.5f)
    }

    private fun fireUpdate() {
        onUpdate?.invoke(
            binding.sliderOffsetX.value,
            binding.sliderOffsetY.value,
            binding.sliderScaleX.value,
            binding.sliderScaleY.value,
        )
    }

    private fun updateLabels() {
        val ox = binding.sliderOffsetX.value.toInt()
        val oy = binding.sliderOffsetY.value.toInt()
        val sx = binding.sliderScaleX.value
        val sy = binding.sliderScaleY.value
        binding.labelOffsetX.text = "X Position: ${if (ox >= 0) "+$ox" else "$ox"} dp"
        binding.labelOffsetY.text = "Y Position: ${if (oy >= 0) "+$oy" else "$oy"} dp"
        binding.labelScaleX.text  = "Width: ${"%.1f".format(sx)}×"
        binding.labelScaleY.text  = "Height: ${"%.1f".format(sy)}×"
    }

    override fun onDestroyView() {
        val b = _binding
        if (b != null && onCommit != null) {
            onCommit!!.invoke(
                b.sliderOffsetX.value,
                b.sliderOffsetY.value,
                b.sliderScaleX.value,
                b.sliderScaleY.value,
            )
        }
        super.onDestroyView()
        _binding = null
    }
}
