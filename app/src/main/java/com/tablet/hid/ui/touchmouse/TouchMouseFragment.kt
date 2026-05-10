package com.tablet.hid.ui.touchmouse

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PointF
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
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
import com.tablet.hid.model.ClickBehavior
import com.tablet.hid.model.KeyboardMacroPresets
import com.tablet.hid.model.MouseButton
import com.tablet.hid.model.TouchMode
import com.tablet.hid.model.TouchMouseConfig
import com.tablet.hid.model.TouchMouseSubRegionConfig
import com.tablet.hid.model.ZoneType
import com.tablet.hid.util.HidPrefs
import com.tablet.hid.util.OrientationStore
import kotlin.math.sqrt
import kotlinx.coroutines.launch

class TouchMouseFragment : Fragment() {

    private var _binding: FragmentTouchMouseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HidViewModel by activityViewModels()

    // ── Zone-edit state ──────────────────────────────────────────────────────
    private enum class EditMode { NONE, LEFT_ZONE, RIGHT_ZONE, LEFT_SUB_REGION, RIGHT_SUB_REGION }
    private var editMode = EditMode.NONE
    private var pendingSubRegionKeyboardModifiers = 0

    // ── Calibration state ────────────────────────────────────────────────────
    private enum class CalibrationPhase { NONE, WAITING_PRIMARY, WAITING_LEFT, WAITING_RIGHT }
    private var calibrationPhase = CalibrationPhase.NONE
    private var calPrimaryX = 0f
    private var calPrimaryY = 0f
    private var calLeftX = 0f
    private var calLeftY = 0f

    // ── Touch-mode state (TOUCH profile) ────────────────────────────────────
    private var touchPrimaryId = -1
    private var touchLastX = 0f
    private var touchLastY = 0f
    private var touchRightClickActive = false

    // ── Mouse-mode state (MOUSE profile) ────────────────────────────────────
    private var mousePrimaryId = -1
    private var mouseLastX = 0f
    private var mouseLastY = 0f
    // pointerZone[id] = mouse button bit mask for every zone matched on pointer down.
    private val pointerZone = mutableMapOf<Int, Int>()
    private val pointerKeyboardModifiers = mutableMapOf<Int, Int>()
    // Momentary: track which pointers are currently pressing each button
    private val leftPressPointers = mutableSetOf<Int>()
    private val rightPressPointers = mutableSetOf<Int>()
    private val middlePressPointers = mutableSetOf<Int>()
    // Latching state
    private var leftLatched = false
    private var rightLatched = false

    // ── Three-finger scroll state ────────────────────────────────────────────
    private var threeFingerScrolling = false
    private var scrollCarryV = 0f
    private var scrollCarryH = 0f
    private var scrollLastX = 0f
    private var scrollLastY = 0f

    companion object {
        private const val BTN_LEFT = 1
        private const val BTN_RIGHT = 2
        private const val BTN_MIDDLE = 4
        private const val TOUCH_SENSITIVITY = 1.5f
        private const val RIGHT_CLICK_ZONE_FRAC = 0.82f
        private const val SCROLL_PIXELS_PER_TICK = 50f
    }

    // GestureDetector used only in Touch mode for double-tap → double-click.
    private val touchGesture by lazy {
        GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (threeFingerScrolling) return false
                val btn = if (touchRightClickActive) BTN_RIGHT else BTN_LEFT
                repeat(2) {
                    viewModel.sendMouseReport(buttons = btn)
                    viewModel.sendMouseReport(buttons = 0)
                }
                return true
            }
        })
    }

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
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.exit_mode_title)
                .setMessage(R.string.exit_mode_message)
                .setPositiveButton(R.string.exit_mode_confirm) { _, _ ->
                    isEnabled = false
                    viewModel.disconnect()
                    findNavController().navigate(R.id.action_touchMouse_to_home)
                }
                .setNegativeButton(R.string.exit_mode_stay, null)
                .show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressCallback)

        requireActivity().requestedOrientation =
            OrientationStore.toActivityOrientation(OrientationStore.get(requireContext()))

        binding.btnOrientationLock.setOnClickListener { cycleOrientationLock() }
        updateOrientationIcon()

        // Insets are applied before immersive mode kicks in (onResume);
        // once immersive hides bars the padding returns to 0 automatically.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bars.bottom)
            insets
        }

        binding.btnSettings.setOnClickListener { showConfigSheet() }
        binding.btnCancelEdit.setOnClickListener { cancelZoneEdit() }
        binding.btnCancelCalibration.setOnClickListener { cancelCalibration() }

        binding.touchZoneOverlay.setOnTouchListener { _, event -> handleTouch(event) }

        observeState()
        observeConfig()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ────────────────────────────────────────────────────────────────────────
    // Config sheet + zone-edit flow
    // ────────────────────────────────────────────────────────────────────────

    private fun showConfigSheet() {
        val sheet = TouchMouseConfigSheet().apply {
            onZoneEditRequested = { isLeft -> startZoneEdit(isLeft) }
            onSubRegionEditRequested = { isLeft, modifiers -> startSubRegionEdit(isLeft, modifiers) }
            onCalibrationRequested = { startCalibration() }
        }
        sheet.show(childFragmentManager, "tmConfig")
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

    private fun startZoneEdit(isLeft: Boolean) {
        editMode = if (isLeft) EditMode.LEFT_ZONE else EditMode.RIGHT_ZONE
        binding.zoneEditOverlay.isVisible = true
        binding.labelZoneEditHint.setText(
            if (isLeft) R.string.zone_edit_hint_left else R.string.zone_edit_hint_right
        )
        binding.touchZoneOverlay.editingLeft = isLeft
        binding.touchZoneOverlay.editDragStart = null
        binding.touchZoneOverlay.editDragEnd = null
    }

    private fun startSubRegionEdit(isLeft: Boolean, keyboardModifiers: Int) {
        editMode = if (isLeft) EditMode.LEFT_SUB_REGION else EditMode.RIGHT_SUB_REGION
        pendingSubRegionKeyboardModifiers = keyboardModifiers
        binding.zoneEditOverlay.isVisible = true
        val action = if (keyboardModifiers == KeyboardMacroPresets.MOD_LEFT_CONTROL) {
            "hold Ctrl"
        } else {
            "send middle-click"
        }
        binding.labelZoneEditHint.text = if (isLeft) {
            "Drag a Left-button sub-region. It will $action."
        } else {
            "Drag a Right-button sub-region. It will $action."
        }
        binding.touchZoneOverlay.editingLeft = isLeft
        binding.touchZoneOverlay.editDragStart = null
        binding.touchZoneOverlay.editDragEnd = null
    }

    // ────────────────────────────────────────────────────────────────────────
    // Dynamic zone auto-calibration
    // ────────────────────────────────────────────────────────────────────────

    private fun startCalibration() {
        calibrationPhase = CalibrationPhase.WAITING_PRIMARY
        binding.calibrationOverlay.isVisible = true
        binding.labelCalibrationHint.setText(R.string.calibrate_hint_primary)
    }

    private fun cancelCalibration() {
        calibrationPhase = CalibrationPhase.NONE
        calPrimaryX = 0f; calPrimaryY = 0f
        calLeftX = 0f; calLeftY = 0f
        binding.calibrationOverlay.isVisible = false
    }

    private fun handleCalibrationTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (calibrationPhase == CalibrationPhase.WAITING_PRIMARY) {
                    calPrimaryX = event.x; calPrimaryY = event.y
                    calibrationPhase = CalibrationPhase.WAITING_LEFT
                    binding.labelCalibrationHint.setText(R.string.calibrate_hint_left)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val x = event.getX(idx); val y = event.getY(idx)
                when (calibrationPhase) {
                    CalibrationPhase.WAITING_LEFT -> {
                        calLeftX = x; calLeftY = y
                        calibrationPhase = CalibrationPhase.WAITING_RIGHT
                        binding.labelCalibrationHint.setText(R.string.calibrate_hint_right)
                    }
                    CalibrationPhase.WAITING_RIGHT -> completeCalibration(x, y)
                    else -> {}
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                // Any finger lifting before completion resets to step 1.
                if (calibrationPhase == CalibrationPhase.WAITING_LEFT ||
                    calibrationPhase == CalibrationPhase.WAITING_RIGHT) {
                    calibrationPhase = CalibrationPhase.WAITING_PRIMARY
                    calPrimaryX = 0f; calPrimaryY = 0f
                    calLeftX = 0f; calLeftY = 0f
                    binding.labelCalibrationHint.setText(R.string.calibrate_hint_primary)
                }
            }
        }
        return true
    }

    private fun completeCalibration(rightX: Float, rightY: Float) {
        val overlay = binding.touchZoneOverlay
        val minDim = minOf(overlay.width, overlay.height).toFloat()
        if (minDim <= 0f) { cancelCalibration(); return }

        fun Float.roundToStep(step: Float) = (kotlin.math.round(this / step) * step)

        fun derive(clickX: Float, clickY: Float): Triple<Float, Float, Float> {
            val ox = ((clickX - calPrimaryX) / minDim).coerceIn(-1f, 1f)
            val oy = ((clickY - calPrimaryY) / minDim).coerceIn(-1f, 1f)
            val dx = clickX - calPrimaryX; val dy = clickY - calPrimaryY
            val radius = (sqrt((dx * dx + dy * dy).toDouble()).toFloat() * 0.45f / minDim)
                .coerceIn(0.04f, 0.15f)
            return Triple(
                ox.roundToStep(0.05f).coerceIn(-1f, 1f),
                oy.roundToStep(0.05f).coerceIn(-1f, 1f),
                radius.roundToStep(0.01f).coerceIn(0.03f, 0.20f)
            )
        }

        val (lox, loy, lr) = derive(calLeftX, calLeftY)
        val (rox, roy, rr) = derive(rightX, rightY)

        val prev = viewModel.touchMouseConfig.value
        viewModel.updateTouchMouseConfig(prev.copy(
            leftButton = prev.leftButton.copy(
                enabled = true,
                zoneType = com.tablet.hid.model.ZoneType.DYNAMIC,
                dynamicOffsetX = lox,
                dynamicOffsetY = loy,
                dynamicRadius = lr
            ),
            rightButton = prev.rightButton.copy(
                enabled = true,
                zoneType = com.tablet.hid.model.ZoneType.DYNAMIC,
                dynamicOffsetX = rox,
                dynamicOffsetY = roy,
                dynamicRadius = rr
            )
        ))
        cancelCalibration()
    }

    private fun cancelZoneEdit() {
        editMode = EditMode.NONE
        pendingSubRegionKeyboardModifiers = 0
        binding.zoneEditOverlay.isVisible = false
        binding.touchZoneOverlay.editingLeft = null
        binding.touchZoneOverlay.editDragStart = null
        binding.touchZoneOverlay.editDragEnd = null
    }

    private fun confirmZoneEdit(endX: Float, endY: Float) {
        val start = binding.touchZoneOverlay.editDragStart ?: return cancelZoneEdit()
        val w = binding.touchZoneOverlay.width.toFloat().takeIf { it > 0 } ?: return cancelZoneEdit()
        val h = binding.touchZoneOverlay.height.toFloat().takeIf { it > 0 } ?: return cancelZoneEdit()

        val left   = minOf(start.x, endX) / w
        val top    = minOf(start.y, endY) / h
        val right  = maxOf(start.x, endX) / w
        val bottom = maxOf(start.y, endY) / h

        // Require the zone to be at least 5% in each dimension.
        if (right - left < 0.05f || bottom - top < 0.05f) return cancelZoneEdit()

        val prev = viewModel.touchMouseConfig.value
        val newConfig = when (editMode) {
            EditMode.LEFT_ZONE -> prev.copy(leftButton = prev.leftButton.copy(
                staticLeft = left, staticTop = top, staticRight = right, staticBottom = bottom
            ))
            EditMode.RIGHT_ZONE -> prev.copy(rightButton = prev.rightButton.copy(
                staticLeft = left, staticTop = top, staticRight = right, staticBottom = bottom
            ))
            EditMode.LEFT_SUB_REGION -> {
                val subRegion = newSubRegion(left, top, right, bottom)
                prev.copy(leftButton = prev.leftButton.copy(
                    enabled = true,
                    subRegions = prev.leftButton.subRegions + subRegion,
                ))
            }
            EditMode.RIGHT_SUB_REGION -> {
                val subRegion = newSubRegion(left, top, right, bottom)
                prev.copy(rightButton = prev.rightButton.copy(
                    enabled = true,
                    subRegions = prev.rightButton.subRegions + subRegion,
                ))
            }
            EditMode.NONE -> prev
        }
        viewModel.updateTouchMouseConfig(newConfig)
        cancelZoneEdit()
    }

    private fun newSubRegion(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) = TouchMouseSubRegionConfig(
        enabled = true,
        zoneType = ZoneType.STATIC,
        staticLeft = left,
        staticTop = top,
        staticRight = right,
        staticBottom = bottom,
        keyboardModifiers = pendingSubRegionKeyboardModifiers,
        alternateMouseButton = if (pendingSubRegionKeyboardModifiers == 0) MouseButton.MIDDLE else null,
    )

    // ────────────────────────────────────────────────────────────────────────
    // Master touch dispatcher
    // ────────────────────────────────────────────────────────────────────────

    private fun handleTouch(event: MotionEvent): Boolean {
        if (calibrationPhase != CalibrationPhase.NONE) return handleCalibrationTouch(event)
        if (editMode != EditMode.NONE) return handleZoneEditTouch(event)

        return when (viewModel.touchMouseConfig.value.mode) {
            TouchMode.TOUCH -> handleTouchModeEvent(event)
            TouchMode.MOUSE -> handleMouseModeEvent(event, viewModel.touchMouseConfig.value)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Zone-edit touch: rubber-band rectangle drawing
    // ────────────────────────────────────────────────────────────────────────

    private fun handleZoneEditTouch(event: MotionEvent): Boolean {
        val overlay = binding.touchZoneOverlay
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                overlay.editDragStart = PointF(event.x, event.y)
                overlay.editDragEnd   = PointF(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                overlay.editDragEnd = PointF(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                confirmZoneEdit(event.x, event.y)
            }
            MotionEvent.ACTION_CANCEL -> cancelZoneEdit()
        }
        return true
    }

    // ────────────────────────────────────────────────────────────────────────
    // Touch mode: original trackpad-with-tap-click behaviour
    //   Bottom RIGHT_CLICK_ZONE_FRAC of the surface = right-click modifier zone
    //   Main area: touch down = left/right click + movement
    // ────────────────────────────────────────────────────────────────────────

    private fun handleTouchModeEvent(event: MotionEvent): Boolean {
        touchGesture.onTouchEvent(event)

        val surfaceH = binding.touchZoneOverlay.height.toFloat()
        val threshold = surfaceH * RIGHT_CLICK_ZONE_FRAC

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val y = event.y
                if (y >= threshold) {
                    touchRightClickActive = true
                    binding.touchZoneOverlay.rightActive = true
                    viewModel.sendMouseReport(buttons = BTN_RIGHT)
                } else {
                    touchPrimaryId = event.getPointerId(0)
                    touchLastX = event.x; touchLastY = event.y
                    val btn = if (touchRightClickActive) BTN_RIGHT else BTN_LEFT
                    viewModel.sendMouseReport(buttons = btn)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val y = event.getY(idx)
                if (y >= threshold && !touchRightClickActive) {
                    touchRightClickActive = true
                    binding.touchZoneOverlay.rightActive = true
                }
                if (event.pointerCount == 3 && !threeFingerScrolling &&
                        viewModel.touchMouseConfig.value.scrollEnabled) {
                    threeFingerScrolling = true
                    scrollCarryV = 0f; scrollCarryH = 0f
                    scrollLastX = event.getX(0); scrollLastY = event.getY(0)
                    touchPrimaryId = -1
                    viewModel.sendMouseReport(buttons = 0)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (threeFingerScrolling) {
                    handleScrollMove(event)
                } else {
                    val idx = event.findPointerIndex(touchPrimaryId)
                    if (idx >= 0) {
                        val btn = if (touchRightClickActive) BTN_RIGHT else BTN_LEFT
                        var totalDx = 0f; var totalDy = 0f
                        for (h in 0 until event.historySize) {
                            val hx = event.getHistoricalX(idx, h)
                            val hy = event.getHistoricalY(idx, h)
                            totalDx += (hx - touchLastX) * TOUCH_SENSITIVITY
                            totalDy += (hy - touchLastY) * TOUCH_SENSITIVITY
                            touchLastX = hx; touchLastY = hy
                        }
                        val cx = event.getX(idx); val cy = event.getY(idx)
                        totalDx += (cx - touchLastX) * TOUCH_SENSITIVITY
                        totalDy += (cy - touchLastY) * TOUCH_SENSITIVITY
                        touchLastX = cx; touchLastY = cy
                        viewModel.sendMouseReport(buttons = btn, dx = totalDx, dy = totalDy)
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pid = event.getPointerId(event.actionIndex)
                if (pid == touchPrimaryId) {
                    touchPrimaryId = -1
                    viewModel.sendMouseReport(buttons = 0)
                }
                // Release right-click if no remaining pointer is in the zone.
                val anyInZone = (0 until event.pointerCount).any { i ->
                    event.getPointerId(i) != pid && event.getY(i) >= threshold
                }
                if (!anyInZone) {
                    touchRightClickActive = false
                    binding.touchZoneOverlay.rightActive = false
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                threeFingerScrolling = false
                scrollCarryV = 0f; scrollCarryH = 0f
                touchPrimaryId = -1
                touchRightClickActive = false
                binding.touchZoneOverlay.rightActive = false
                viewModel.sendMouseReport(buttons = 0)
            }
        }
        return true
    }

    // ────────────────────────────────────────────────────────────────────────
    // Mouse mode: trackpad-style movement + configurable button zones
    //   First pointer → movement (delta only on MOVE, never on DOWN)
    //   Additional pointers → hit-tested against button zones
    // ────────────────────────────────────────────────────────────────────────

    private fun handleMouseModeEvent(event: MotionEvent, config: TouchMouseConfig): Boolean {
        val overlay = binding.touchZoneOverlay

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                val pid = event.getPointerId(0)
                val x = event.x; val y = event.y
                val zones = overlay.hitTestButtonBits(x, y)
                val modifiers = overlay.hitTestKeyboardModifiers(x, y)
                pointerZone[pid] = zones
                pointerKeyboardModifiers[pid] = modifiers
                updateSubRegionKeyboardModifiers()
                if (zones != 0) {
                    onZoneDown(zones, pid, config)
                } else {
                    mousePrimaryId = pid
                    mouseLastX = x; mouseLastY = y
                    overlay.updatePrimaryPointer(x, y)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                val x = event.getX(idx); val y = event.getY(idx)

                val zones = overlay.hitTestButtonBits(x, y)
                val modifiers = overlay.hitTestKeyboardModifiers(x, y)
                pointerZone[pid] = zones
                pointerKeyboardModifiers[pid] = modifiers
                updateSubRegionKeyboardModifiers()
                if (zones != 0) {
                    onZoneDown(zones, pid, config)
                } else if (mousePrimaryId == -1) {
                    // First touch was a zone press; promote this non-zone touch to movement pointer.
                    mousePrimaryId = pid
                    mouseLastX = x; mouseLastY = y
                    overlay.updatePrimaryPointer(x, y)
                }
                if (event.pointerCount == 3 && !threeFingerScrolling &&
                        viewModel.touchMouseConfig.value.scrollEnabled) {
                    threeFingerScrolling = true
                    scrollCarryV = 0f; scrollCarryH = 0f
                    scrollLastX = event.getX(0); scrollLastY = event.getY(0)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (threeFingerScrolling) {
                    handleScrollMove(event)
                } else {
                    val idx = event.findPointerIndex(mousePrimaryId)
                    if (idx >= 0) {
                        val scale = config.sensitivity * 0.3f
                        val bits = currentButtonBits(config)
                        var totalDx = 0f; var totalDy = 0f
                        for (h in 0 until event.historySize) {
                            val hx = event.getHistoricalX(idx, h)
                            val hy = event.getHistoricalY(idx, h)
                            totalDx += (hx - mouseLastX) * scale
                            totalDy += (hy - mouseLastY) * scale
                            mouseLastX = hx; mouseLastY = hy
                        }
                        val x = event.getX(idx); val y = event.getY(idx)
                        totalDx += (x - mouseLastX) * scale
                        totalDy += (y - mouseLastY) * scale
                        mouseLastX = x; mouseLastY = y
                        overlay.updatePrimaryPointer(x, y)
                        viewModel.sendMouseReport(buttons = bits, dx = totalDx, dy = totalDy)
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pid = event.getPointerId(event.actionIndex)
                val zones = pointerZone.remove(pid) ?: 0
                pointerKeyboardModifiers.remove(pid)
                updateSubRegionKeyboardModifiers()
                if (pid == mousePrimaryId) {
                    mousePrimaryId = -1
                    overlay.clearPrimaryPointer()
                }
                if (zones != 0) onZoneUp(zones, pid, config)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Release everything. Momentary buttons are released; latching remains.
                threeFingerScrolling = false
                scrollCarryV = 0f; scrollCarryH = 0f
                pointerZone.clear()
                pointerKeyboardModifiers.clear()
                updateSubRegionKeyboardModifiers()
                mousePrimaryId = -1
                overlay.clearPrimaryPointer()

                // Release any momentary button presses.
                val hadLeft  = leftPressPointers.isNotEmpty()
                val hadRight = rightPressPointers.isNotEmpty()
                leftPressPointers.clear()
                rightPressPointers.clear()
                middlePressPointers.clear()
                if (hadLeft  && config.leftButton.behavior  == ClickBehavior.MOMENTARY)
                    overlay.leftActive = false
                if (hadRight && config.rightButton.behavior == ClickBehavior.MOMENTARY)
                    overlay.rightActive = false

                // Send final report (latched buttons may still be active).
                viewModel.sendMouseReport(buttons = currentButtonBits(config))
            }
        }
        return true
    }

    // ────────────────────────────────────────────────────────────────────────
    // Three-finger scroll: accumulate Y delta → send wheel ticks
    //   Default: drag UP → scroll UP (positive wheel).
    //   Inverted: drag DOWN → scroll UP (natural/trackpad feel).
    // ────────────────────────────────────────────────────────────────────────

    private fun handleScrollMove(event: MotionEvent) {
        val invert = viewModel.touchMouseConfig.value.invertScroll
        // Vertical: finger up → positive (scroll up). Inverted: finger down → positive (natural).
        val vSign = if (invert) 1f else -1f
        // Horizontal: finger right → positive (pan right). Inverted: mirrors vertical flip.
        val hSign = if (invert) -1f else 1f
        for (h in 0 until event.historySize) {
            val hx = event.getHistoricalX(0, h)
            val hy = event.getHistoricalY(0, h)
            scrollCarryV += vSign * (hy - scrollLastY) / SCROLL_PIXELS_PER_TICK
            scrollCarryH += hSign * (hx - scrollLastX) / SCROLL_PIXELS_PER_TICK
            scrollLastX = hx; scrollLastY = hy
        }
        val cx = event.getX(0); val cy = event.getY(0)
        scrollCarryV += vSign * (cy - scrollLastY) / SCROLL_PIXELS_PER_TICK
        scrollCarryH += hSign * (cx - scrollLastX) / SCROLL_PIXELS_PER_TICK
        scrollLastX = cx; scrollLastY = cy
        val vTicks = scrollCarryV.toInt()
        val hTicks = scrollCarryH.toInt()
        if (vTicks != 0) scrollCarryV -= vTicks
        if (hTicks != 0) scrollCarryH -= hTicks
        if (vTicks != 0 || hTicks != 0) {
            viewModel.sendMouseReport(buttons = 0, wheel = vTicks, hwheel = hTicks)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Zone press / release with momentary vs latching semantics
    // ────────────────────────────────────────────────────────────────────────

    private fun onZoneDown(zones: Int, pointerId: Int, config: TouchMouseConfig) {
        val overlay = binding.touchZoneOverlay
        if ((zones and BTN_LEFT) != 0) {
            when (config.leftButton.behavior) {
                ClickBehavior.MOMENTARY -> {
                    leftPressPointers.add(pointerId)
                    overlay.leftActive = true
                }
                ClickBehavior.LATCHING -> {
                    leftLatched = !leftLatched
                    overlay.leftActive = leftLatched
                }
            }
        }
        if ((zones and BTN_RIGHT) != 0) {
            when (config.rightButton.behavior) {
                ClickBehavior.MOMENTARY -> {
                    rightPressPointers.add(pointerId)
                    overlay.rightActive = true
                }
                ClickBehavior.LATCHING -> {
                    rightLatched = !rightLatched
                    overlay.rightActive = rightLatched
                }
            }
        }
        if ((zones and BTN_MIDDLE) != 0) middlePressPointers.add(pointerId)
        viewModel.sendMouseReport(buttons = currentButtonBits(config))
    }

    private fun onZoneUp(zones: Int, pointerId: Int, config: TouchMouseConfig) {
        val overlay = binding.touchZoneOverlay
        if ((zones and BTN_LEFT) != 0) {
            if (config.leftButton.behavior == ClickBehavior.MOMENTARY) {
                leftPressPointers.remove(pointerId)
                overlay.leftActive = leftPressPointers.isNotEmpty()
            }
        }
        if ((zones and BTN_RIGHT) != 0) {
            if (config.rightButton.behavior == ClickBehavior.MOMENTARY) {
                rightPressPointers.remove(pointerId)
                overlay.rightActive = rightPressPointers.isNotEmpty()
            }
        }
        if ((zones and BTN_MIDDLE) != 0) middlePressPointers.remove(pointerId)
        viewModel.sendMouseReport(buttons = currentButtonBits(config))
    }

    private fun currentButtonBits(config: TouchMouseConfig): Int {
        var bits = 0
        val leftDown = when (config.leftButton.behavior) {
            ClickBehavior.MOMENTARY -> leftPressPointers.isNotEmpty()
            ClickBehavior.LATCHING  -> leftLatched
        }
        val rightDown = when (config.rightButton.behavior) {
            ClickBehavior.MOMENTARY -> rightPressPointers.isNotEmpty()
            ClickBehavior.LATCHING  -> rightLatched
        }
        if (leftDown  && config.leftButton.enabled)  bits = bits or BTN_LEFT
        if (rightDown && config.rightButton.enabled) bits = bits or BTN_RIGHT
        if (middlePressPointers.isNotEmpty()) bits = bits or BTN_MIDDLE
        return bits
    }

    private fun updateSubRegionKeyboardModifiers() {
        var modifiers = 0
        pointerKeyboardModifiers.values.forEach { modifiers = modifiers or it }
        viewModel.sendKeyboardReport(modifiers = modifiers)
    }

    // ────────────────────────────────────────────────────────────────────────
    // State & config observation
    // ────────────────────────────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val connected = state is BleHidManager.State.Connected
                    binding.ledStatus.backgroundTintList = ColorStateList.valueOf(
                        if (connected) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
                    )
                    binding.textConnStatus.text = if (connected)
                        getString(R.string.status_connected)
                    else
                        getString(R.string.status_disconnected)
                }
            }
        }
    }

    private fun observeConfig() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.touchMouseConfig.collect { config ->
                    binding.touchZoneOverlay.config = config
                    renderMacroButtons(config)

                    // Reset latching state when mode or button config changes.
                    if (config.mode == TouchMode.TOUCH) {
                        leftLatched = false; rightLatched = false
                        middlePressPointers.clear()
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
        binding.macroButtonRow.removeAllViews()
        binding.macroScroll.isVisible = config.macroButtons.isNotEmpty()
        config.macroButtons.forEach { macro ->
            val button = MaterialButton(requireContext()).apply {
                text = macro.label
                minHeight = resources.getDimensionPixelSize(R.dimen.macro_button_height)
                minWidth = 0
                setPadding(18, 0, 18, 0)
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
            val margin = (8 * resources.displayMetrics.density).toInt()
            binding.macroButtonRow.addView(
                button,
                ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = margin },
            )
        }
    }
}
