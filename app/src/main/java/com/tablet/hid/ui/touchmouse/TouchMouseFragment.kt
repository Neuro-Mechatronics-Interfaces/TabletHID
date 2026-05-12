package com.tablet.hid.ui.touchmouse

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tablet.hid.HidViewModel
import com.tablet.hid.R
import com.tablet.hid.bluetooth.BleHidManager
import com.tablet.hid.databinding.FragmentTouchMouseBinding
import com.tablet.hid.model.KeyboardMacroButtonConfig
import com.tablet.hid.model.TouchMode
import com.tablet.hid.model.TouchMouseConfig
import com.tablet.hid.ui.shared.ButtonLayoutSheet
import com.tablet.hid.util.HidPrefs
import com.tablet.hid.util.OrientationStore
import com.tablet.hid.util.UiPaletteStore
import kotlin.math.abs
import kotlin.math.round
import kotlinx.coroutines.launch

class TouchMouseFragment : Fragment() {

    private var _binding: FragmentTouchMouseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HidViewModel by activityViewModels()

    // ── Zone-edit / macro-edit state ─────────────────────────────────────────
    private enum class EditMode { NONE, LEFT_ZONE, RIGHT_ZONE, LEFT_SUB_REGION, RIGHT_SUB_REGION, SNIPER, MACRO_LAYOUT }
    private var editMode = EditMode.NONE

    // Views created in renderMacroButtons; tracked for macro edit mode
    private val macroButtonViews = mutableListOf<Pair<MaterialButton, KeyboardMacroButtonConfig>>()
    // Natural (zero-offset) translationX/Y positions, computed post-layout in renderMacroButtons
    private val macroNaturalPositions = mutableListOf<android.graphics.PointF>()

    // ── Helpers ───────────────────────────────────────────────────────────────
    private lateinit var calibrationController: CalibrationController
    private lateinit var zoneEditController: ZoneEditController
    private lateinit var touchModeHandler: TouchModeHandler
    private lateinit var mouseModeHandler: MouseModeHandler

    // ────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ────────────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTouchMouseBinding.inflate(inflater, container, false)
        return binding.root
    }

    // ── Immersive-mode helpers ───────────────────────────────────────────────

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)
        val ctrl = WindowCompat.getInsetsController(requireActivity().window, requireView())
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun exitImmersiveMode() {
        WindowCompat.getInsetsController(requireActivity().window, requireView())
            .show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, true)
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.hide()
        enterImmersiveMode()
        if (HidPrefs.isScreenPinningEnabled(requireContext())) {
            activity?.startLockTask()
        }
        // Refresh palette in case user changed it in Settings.
        binding.touchZoneOverlay.palette = UiPaletteStore.get(requireContext())
        renderMacroButtons(viewModel.touchMouseConfig.value)
    }

    override fun onPause() {
        super.onPause()
        exitImmersiveMode()
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.show()
        try { activity?.stopLockTask() } catch (_: Exception) {}
    }

    // ── Back-press guard ─────────────────────────────────────────────────────

    private val backPressCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (editMode == EditMode.MACRO_LAYOUT) { exitMacroLayoutEdit(); return }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.exit_mode_title)
                .setPositiveButton(R.string.exit_mode_confirm) { _, _ ->
                    isEnabled = false
                    findNavController().navigate(R.id.action_touchMouse_to_home)
                }
                .setNegativeButton(R.string.exit_mode_stay, null)
                .show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calibrationController = CalibrationController(binding, viewModel)
        zoneEditController = ZoneEditController(binding, viewModel)
        touchModeHandler = TouchModeHandler(
            context = requireContext(),
            overlay = binding.touchZoneOverlay,
            viewModel = viewModel,
            scrollEnabled = { viewModel.touchMouseConfig.value.scrollEnabled },
        )
        mouseModeHandler = MouseModeHandler(
            overlay = binding.touchZoneOverlay,
            viewModel = viewModel,
            scrollEnabled = { viewModel.touchMouseConfig.value.scrollEnabled },
        )

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressCallback)

        requireActivity().requestedOrientation =
            OrientationStore.toActivityOrientation(OrientationStore.get(requireContext()))

        binding.btnOrientationLock.setOnClickListener { cycleOrientationLock() }
        updateOrientationIcon()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bars.bottom)
            insets
        }

        binding.btnSettings.setOnClickListener { showConfigSheet() }
        binding.btnShortcutPanel.setOnClickListener { toggleShortcutPanel() }
        binding.btnCancelEdit.setOnClickListener { cancelZoneEdit() }
        binding.btnCancelCalibration.setOnClickListener { calibrationController.cancelCalibration() }
        binding.btnMacroEditDone.setOnClickListener { exitMacroLayoutEdit() }

        binding.touchZoneOverlay.setOnTouchListener { _, event -> handleTouch(event) }

        observeState()
        observeConfig()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        touchModeHandler.destroy()
        _binding = null
    }

    // ────────────────────────────────────────────────────────────────────────
    // Config sheet + zone-edit flow
    // ────────────────────────────────────────────────────────────────────────

    private fun showConfigSheet() {
        val sheet = TouchMouseConfigSheet().apply {
            onZoneEditRequested = { isLeft ->
                editMode = if (isLeft) EditMode.LEFT_ZONE else EditMode.RIGHT_ZONE
                zoneEditController.startZoneEdit(isLeft)
            }
            onSubRegionEditRequested = { isLeft, modifiers ->
                editMode = if (isLeft) EditMode.LEFT_SUB_REGION else EditMode.RIGHT_SUB_REGION
                zoneEditController.startSubRegionEdit(isLeft, modifiers)
            }
            onCalibrationRequested = { calibrationController.startCalibration() }
            onSniperEditRequested = {
                editMode = EditMode.SNIPER
                zoneEditController.startSniperEdit()
            }
            onMacroEditRequested = { startMacroLayoutEdit() }
        }
        sheet.show(childFragmentManager, "tmConfig")
    }

    private fun cancelZoneEdit() {
        editMode = EditMode.NONE
        zoneEditController.cancelZoneEdit()
    }

    // ── Orientation lock ─────────────────────────────────────────────────────

    private fun cycleOrientationLock() {
        val next = (OrientationStore.get(requireContext()) + 1) % 3
        OrientationStore.set(requireContext(), next)
        requireActivity().requestedOrientation = OrientationStore.toActivityOrientation(next)
        updateOrientationIcon()
    }

    private fun updateOrientationIcon() {
        val icon = when (OrientationStore.get(requireContext())) {
            OrientationStore.PORTRAIT  -> R.drawable.ic_orientation_portrait
            OrientationStore.LANDSCAPE -> R.drawable.ic_orientation_landscape
            else                       -> R.drawable.ic_orientation_auto
        }
        binding.btnOrientationLock.icon = ContextCompat.getDrawable(requireContext(), icon)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Master touch dispatcher
    // ────────────────────────────────────────────────────────────────────────

    private fun handleTouch(event: MotionEvent): Boolean {
        if (calibrationController.isActive) return calibrationController.handleCalibrationTouch(event)
        if (editMode == EditMode.MACRO_LAYOUT) return true
        if (zoneEditController.isActive()) {
            val result = zoneEditController.handleTouch(event)
            if (!zoneEditController.isActive()) editMode = EditMode.NONE
            return result
        }

        return when (viewModel.touchMouseConfig.value.mode) {
            TouchMode.TOUCH -> touchModeHandler.handle(event)
            TouchMode.MOUSE -> mouseModeHandler.handle(event, viewModel.touchMouseConfig.value)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Shortcut launcher panel
    // ────────────────────────────────────────────────────────────────────────

    private fun toggleShortcutPanel() {
        val panel = binding.shortcutPanel
        panel.isVisible = !panel.isVisible
        val tint = requireContext().getColor(if (panel.isVisible) R.color.panel_active_tint else R.color.white)
        binding.btnShortcutPanel.iconTint = android.content.res.ColorStateList.valueOf(tint)
    }

    private fun renderShortcutPanel(config: TouchMouseConfig) {
        binding.shortcutPanelList.removeAllViews()
        val hasMacros = config.macroButtons.isNotEmpty()
        binding.btnShortcutPanel.isVisible = hasMacros
        if (!hasMacros) {
            binding.shortcutPanel.isVisible = false
            return
        }
        val dp = resources.displayMetrics.density
        config.macroButtons.forEach { macro ->
            val button = MaterialButton(requireContext()).apply {
                text = macro.label
                textSize = 14f
                setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            viewModel.sendKeyboardReport(macro.modifiers, macro.keyUsages)
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            viewModel.sendKeyboardReport()
                            true
                        }
                        else -> false
                    }
                }
            }
            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = (4 * dp).toInt() }
            binding.shortcutPanelList.addView(button, params)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // State & config observation
    // ────────────────────────────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val ledColor = requireContext().getColor(when (state) {
                        is BleHidManager.State.Connected -> R.color.led_connected
                        is BleHidManager.State.Idle,
                        is BleHidManager.State.Error     -> R.color.led_disconnected
                        else                             -> R.color.led_connecting
                    })
                    binding.ledStatus.backgroundTintList = ColorStateList.valueOf(ledColor)
                    binding.textConnStatus.text = when (state) {
                        is BleHidManager.State.Connected          -> getString(R.string.status_connected)
                        is BleHidManager.State.Registering        -> getString(R.string.home_status_connecting)
                        is BleHidManager.State.WaitingForConnection -> getString(R.string.home_status_waiting)
                        is BleHidManager.State.Reconnecting       -> getString(R.string.tutorial_status_reconnecting, state.deviceName)
                        is BleHidManager.State.PendingApproval   -> getString(R.string.home_status_pending_approval)
                        is BleHidManager.State.Error              -> getString(R.string.status_disconnected)
                        is BleHidManager.State.Idle               -> getString(R.string.status_disconnected)
                    }
                }
            }
        }
    }

    private fun observeConfig() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.touchMouseConfig.collect { config ->
                    binding.touchZoneOverlay.config = config
                    binding.touchZoneOverlay.palette = UiPaletteStore.get(requireContext())
                    renderMacroButtons(config)
                    renderShortcutPanel(config)

                    // Reset latching state when mode or button config changes.
                    if (config.mode == TouchMode.TOUCH) {
                        mouseModeHandler.resetLatchState()
                        binding.touchZoneOverlay.leftActive  = false
                        binding.touchZoneOverlay.rightActive = false
                    }

                    binding.textHint.setText(
                        if (config.mode == TouchMode.MOUSE)
                            R.string.touch_mouse_hint_mouse
                        else
                            R.string.touch_mouse_hint
                    )
                }
            }
        }
    }

    private fun renderMacroButtons(config: TouchMouseConfig) {
        macroButtonViews.clear()
        macroNaturalPositions.clear()
        binding.macroOverlay.removeAllViews()
        if (config.macroButtons.isEmpty()) return
        val dp = resources.displayMetrics.density
        config.macroButtons.forEach { macro ->
            val macroTint = UiPaletteStore.get(requireContext()).macroButtonTint
            val button = MaterialButton(requireContext()).apply {
                text = macro.label
                minHeight = resources.getDimensionPixelSize(R.dimen.macro_button_height)
                minWidth = 0
                setPadding(18, 0, 18, 0)
                scaleX = macro.layoutScaleX
                scaleY = macro.layoutScaleY
                backgroundTintList = android.content.res.ColorStateList.valueOf(macroTint)
                setMacroNormalListener(macro)
            }
            binding.macroOverlay.addView(button, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            ))
            macroButtonViews += button to macro
        }
        binding.macroOverlay.post {
            if (_binding == null) return@post
            val overlayH = binding.macroOverlay.height
            val bottomPx = (12 * dp).toInt()
            var xCursor = (12 * dp).toInt()
            macroButtonViews.forEachIndexed { i, (button, macro) ->
                val naturalTx = xCursor.toFloat()
                val naturalTy = (overlayH - button.height - bottomPx).toFloat()
                if (i >= macroNaturalPositions.size) {
                    macroNaturalPositions.add(android.graphics.PointF(naturalTx, naturalTy))
                } else {
                    macroNaturalPositions[i].set(naturalTx, naturalTy)
                }
                button.translationX = naturalTx + macro.layoutOffsetX * dp
                button.translationY = naturalTy + macro.layoutOffsetY * dp
                xCursor += button.width + (8 * dp).toInt()
            }
            if (editMode == EditMode.MACRO_LAYOUT) {
                macroButtonViews.forEachIndexed { index, (button, _) ->
                    button.alpha = 0.7f
                    button.setOnTouchListener(macroEditTouchListener(button, index))
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun MaterialButton.setMacroNormalListener(macro: KeyboardMacroButtonConfig) {
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { viewModel.sendKeyboardReport(macro.modifiers, macro.keyUsages); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { viewModel.sendKeyboardReport(); true }
                else -> false
            }
        }
    }

    // ── Macro layout edit mode ───────────────────────────────────────────────

    private fun startMacroLayoutEdit() {
        editMode = EditMode.MACRO_LAYOUT
        binding.macroEditBanner.isVisible = true
        macroButtonViews.forEachIndexed { index, (button, _) ->
            button.alpha = 0.7f
            button.setOnTouchListener(macroEditTouchListener(button, index))
        }
    }

    private fun exitMacroLayoutEdit() {
        saveAllMacroLayouts()
        editMode = EditMode.NONE
        binding.macroEditBanner.isVisible = false
        macroButtonViews.forEach { (button, macro) ->
            button.alpha = 1f
            button.setMacroNormalListener(macro)
        }
    }

    private fun saveAllMacroLayouts() {
        val dp = resources.displayMetrics.density
        val config = viewModel.touchMouseConfig.value
        val macros = config.macroButtons.toMutableList()
        macroButtonViews.forEachIndexed { index, (button, _) ->
            if (index >= macros.size) return@forEachIndexed
            val natural = macroNaturalPositions.getOrNull(index)
            macros[index] = macros[index].copy(
                layoutOffsetX = if (natural != null) (button.translationX - natural.x) / dp else button.translationX / dp,
                layoutOffsetY = if (natural != null) (button.translationY - natural.y) / dp else button.translationY / dp,
                layoutScaleX  = button.scaleX,
                layoutScaleY  = button.scaleY,
            )
        }
        viewModel.updateTouchMouseConfig(config.copy(macroButtons = macros))
    }

    private fun showLayoutSheetForMacro(view: View, index: Int) {
        if (index >= macroNaturalPositions.size || index >= macroButtonViews.size) return
        (childFragmentManager.findFragmentByTag("macroLayout") as? ButtonLayoutSheet)
            ?.dismissAllowingStateLoss()
        val density = resources.displayMetrics.density
        val natural = macroNaturalPositions[index]
        val (_, macro) = macroButtonViews[index]
        ButtonLayoutSheet().apply {
            elementTitle = macro.label
            initialOffsetX = macro.layoutOffsetX.coerceIn(-400f, 400f)
            initialOffsetY = macro.layoutOffsetY.coerceIn(-400f, 400f)
            initialScaleX  = macro.layoutScaleX.coerceIn(0.3f, 3.0f)
            initialScaleY  = macro.layoutScaleY.coerceIn(0.3f, 3.0f)
            onUpdate = { ox, oy, sx, sy ->
                view.translationX = natural.x + ox * density
                view.translationY = natural.y + oy * density
                view.scaleX = sx; view.scaleY = sy
            }
            onCommit = { ox, oy, sx, sy ->
                val config = viewModel.touchMouseConfig.value
                val macros = config.macroButtons.toMutableList()
                if (index < macros.size) {
                    macros[index] = macros[index].copy(
                        layoutOffsetX = ox, layoutOffsetY = oy,
                        layoutScaleX = sx, layoutScaleY = sy,
                    )
                    viewModel.updateTouchMouseConfig(config.copy(macroButtons = macros))
                }
            }
        }.show(childFragmentManager, "macroLayout")
    }

    @Suppress("ClickableViewAccessibility")
    private fun macroEditTouchListener(view: View, index: Int): View.OnTouchListener {
        val tapThresholdPx = 8f * resources.displayMetrics.density
        var lastSpanX = 1f; var lastSpanY = 1f
        val scaleDetector = ScaleGestureDetector(requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                    lastSpanX = d.currentSpanX.coerceAtLeast(1f)
                    lastSpanY = d.currentSpanY.coerceAtLeast(1f)
                    return true
                }
                override fun onScale(d: ScaleGestureDetector): Boolean {
                    val fx = d.currentSpanX.coerceAtLeast(1f) / lastSpanX
                    val fy = d.currentSpanY.coerceAtLeast(1f) / lastSpanY
                    view.scaleX = (view.scaleX * fx).coerceAtLeast(0.3f)
                    view.scaleY = (view.scaleY * fy).coerceAtLeast(0.3f)
                    lastSpanX = d.currentSpanX.coerceAtLeast(1f)
                    lastSpanY = d.currentSpanY.coerceAtLeast(1f)
                    return true
                }
            })
        var downRawX = 0f; var downRawY = 0f
        var lastRawX = 0f; var lastRawY = 0f
        var rawTx = 0f; var rawTy = 0f
        var wasDragged = false
        return View.OnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (scaleDetector.isInProgress) return@OnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX; downRawY = event.rawY
                    lastRawX = event.rawX; lastRawY = event.rawY
                    rawTx = view.translationX; rawTy = view.translationY
                    wasDragged = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val gridPx = 8f * resources.displayMetrics.density
                    rawTx += event.rawX - lastRawX
                    rawTy += event.rawY - lastRawY
                    view.translationX = round(rawTx / gridPx) * gridPx
                    view.translationY = round(rawTy / gridPx) * gridPx
                    rawTx = view.translationX; rawTy = view.translationY
                    lastRawX = event.rawX; lastRawY = event.rawY
                    if (abs(event.rawX - downRawX) > tapThresholdPx ||
                        abs(event.rawY - downRawY) > tapThresholdPx) wasDragged = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!wasDragged) showLayoutSheetForMacro(view, index)
                }
            }
            true
        }
    }
}
