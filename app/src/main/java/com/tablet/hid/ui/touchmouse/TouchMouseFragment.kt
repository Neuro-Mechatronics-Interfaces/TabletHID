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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tablet.hid.HidViewModel
import com.tablet.hid.R
import com.tablet.hid.bluetooth.HidManager
import com.tablet.hid.databinding.FragmentTouchMouseBinding
import com.tablet.hid.model.ClickBehavior
import com.tablet.hid.model.TouchMode
import com.tablet.hid.model.TouchMouseConfig
import com.tablet.hid.model.ZoneType
import com.tablet.hid.util.OrientationStore
import kotlin.math.sqrt
import kotlinx.coroutines.launch

class TouchMouseFragment : Fragment() {

    private var _binding: FragmentTouchMouseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HidViewModel by activityViewModels()

    // ── Zone-edit state ──────────────────────────────────────────────────────
    private enum class EditMode { NONE, LEFT_ZONE, RIGHT_ZONE }
    private var editMode = EditMode.NONE

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
    private var touchAccumDx = 0f   // sub-pixel remainder carried across reports
    private var touchAccumDy = 0f
    private var touchRightClickActive = false

    // ── Mouse-mode state (MOUSE profile) ────────────────────────────────────
    private var mousePrimaryId = -1
    private var mouseLastX = 0f
    private var mouseLastY = 0f
    private var mouseAccumDx = 0f   // sub-pixel remainder carried across reports
    private var mouseAccumDy = 0f
    // pointerZone[id] = BTN_LEFT or BTN_RIGHT (zone the pointer is committed to)
    private val pointerZone = mutableMapOf<Int, Int>()
    // Momentary: track which pointers are currently pressing each button
    private val leftPressPointers = mutableSetOf<Int>()
    private val rightPressPointers = mutableSetOf<Int>()
    // Latching state
    private var leftLatched = false
    private var rightLatched = false

    companion object {
        private const val BTN_LEFT = 1
        private const val BTN_RIGHT = 2
        private const val TOUCH_SENSITIVITY = 1.5f
        private const val RIGHT_CLICK_ZONE_FRAC = 0.82f
    }

    // GestureDetector used only in Touch mode for double-tap → double-click.
    private val touchGesture by lazy {
        GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val btn = if (touchRightClickActive) BTN_RIGHT else BTN_LEFT
                repeat(2) {
                    viewModel.sendMouseReport(buttons = btn, dx = 0, dy = 0)
                    viewModel.sendMouseReport(buttons = 0, dx = 0, dy = 0)
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
    }

    override fun onPause() {
        super.onPause()
        exitImmersiveMode()
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.show()
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

        fun derive(clickX: Float, clickY: Float): Triple<Float, Float, Float> {
            val ox = ((clickX - calPrimaryX) / minDim).coerceIn(-1f, 1f)
            val oy = ((clickY - calPrimaryY) / minDim).coerceIn(-1f, 1f)
            val dx = clickX - calPrimaryX; val dy = clickY - calPrimaryY
            val radius = (sqrt((dx * dx + dy * dy).toDouble()).toFloat() * 0.45f / minDim)
                .coerceIn(0.04f, 0.15f)
            return Triple(ox, oy, radius)
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
        val isLeft = editMode == EditMode.LEFT_ZONE
        val newConfig = if (isLeft) {
            prev.copy(leftButton = prev.leftButton.copy(
                staticLeft = left, staticTop = top, staticRight = right, staticBottom = bottom
            ))
        } else {
            prev.copy(rightButton = prev.rightButton.copy(
                staticLeft = left, staticTop = top, staticRight = right, staticBottom = bottom
            ))
        }
        viewModel.updateTouchMouseConfig(newConfig)
        cancelZoneEdit()
    }

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
                    viewModel.sendMouseReport(buttons = BTN_RIGHT, dx = 0, dy = 0)
                } else {
                    touchPrimaryId = event.getPointerId(0)
                    touchLastX = event.x; touchLastY = event.y
                    touchAccumDx = 0f; touchAccumDy = 0f
                    val btn = if (touchRightClickActive) BTN_RIGHT else BTN_LEFT
                    viewModel.sendMouseReport(buttons = btn, dx = 0, dy = 0)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val y = event.getY(idx)
                if (y >= threshold && !touchRightClickActive) {
                    touchRightClickActive = true
                    binding.touchZoneOverlay.rightActive = true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(touchPrimaryId)
                if (idx >= 0) {
                    val btn = if (touchRightClickActive) BTN_RIGHT else BTN_LEFT
                    // Consume historical batched samples before the current position.
                    // Android batches to one callback per frame; each batch may contain
                    // multiple sensor readings. Processing them individually gives
                    // smoother, higher-rate delta output instead of one large jump.
                    for (h in 0 until event.historySize) {
                        val hx = event.getHistoricalX(idx, h)
                        val hy = event.getHistoricalY(idx, h)
                        val rawDx = (hx - touchLastX) * TOUCH_SENSITIVITY + touchAccumDx
                        val rawDy = (hy - touchLastY) * TOUCH_SENSITIVITY + touchAccumDy
                        val dx = rawDx.toInt().coerceIn(-32768, 32767)
                        val dy = rawDy.toInt().coerceIn(-32768, 32767)
                        touchAccumDx = rawDx - dx
                        touchAccumDy = rawDy - dy
                        touchLastX = hx; touchLastY = hy
                        if (dx != 0 || dy != 0) viewModel.sendMouseReport(buttons = btn, dx = dx, dy = dy)
                    }
                    // Current (most recent) position.
                    val cx = event.getX(idx); val cy = event.getY(idx)
                    val rawDx = (cx - touchLastX) * TOUCH_SENSITIVITY + touchAccumDx
                    val rawDy = (cy - touchLastY) * TOUCH_SENSITIVITY + touchAccumDy
                    val dx = rawDx.toInt().coerceIn(-32768, 32767)
                    val dy = rawDy.toInt().coerceIn(-32768, 32767)
                    touchAccumDx = rawDx - dx
                    touchAccumDy = rawDy - dy
                    touchLastX = cx; touchLastY = cy
                    viewModel.sendMouseReport(buttons = btn, dx = dx, dy = dy)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pid = event.getPointerId(event.actionIndex)
                if (pid == touchPrimaryId) {
                    touchPrimaryId = -1
                    viewModel.sendMouseReport(buttons = 0, dx = 0, dy = 0)
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
                touchPrimaryId = -1
                touchAccumDx = 0f; touchAccumDy = 0f
                touchRightClickActive = false
                binding.touchZoneOverlay.rightActive = false
                viewModel.sendMouseReport(buttons = 0, dx = 0, dy = 0)
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
                val zone = when {
                    overlay.hitTestLeft(x, y)  -> BTN_LEFT
                    overlay.hitTestRight(x, y) -> BTN_RIGHT
                    else -> 0
                }
                pointerZone[pid] = zone
                mouseAccumDx = 0f; mouseAccumDy = 0f
                if (zone != 0) {
                    onZoneDown(zone, pid, config)
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

                val zone = when {
                    overlay.hitTestLeft(x, y)  -> BTN_LEFT
                    overlay.hitTestRight(x, y) -> BTN_RIGHT
                    else -> 0
                }
                pointerZone[pid] = zone
                if (zone != 0) {
                    onZoneDown(zone, pid, config)
                } else if (mousePrimaryId == -1) {
                    // First touch was a zone press; promote this non-zone touch to movement pointer.
                    mousePrimaryId = pid
                    mouseLastX = x; mouseLastY = y
                    mouseAccumDx = 0f; mouseAccumDy = 0f
                    overlay.updatePrimaryPointer(x, y)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(mousePrimaryId)
                if (idx >= 0) {
                    val scale = config.sensitivity * 0.3f
                    val bits = currentButtonBits(config)
                    // Consume historical batched samples before the current position.
                    for (h in 0 until event.historySize) {
                        val hx = event.getHistoricalX(idx, h)
                        val hy = event.getHistoricalY(idx, h)
                        val rawDx = (hx - mouseLastX) * scale + mouseAccumDx
                        val rawDy = (hy - mouseLastY) * scale + mouseAccumDy
                        val dx = rawDx.toInt().coerceIn(-32768, 32767)
                        val dy = rawDy.toInt().coerceIn(-32768, 32767)
                        mouseAccumDx = rawDx - dx
                        mouseAccumDy = rawDy - dy
                        mouseLastX = hx; mouseLastY = hy
                        if (dx != 0 || dy != 0) viewModel.sendMouseReport(buttons = bits, dx = dx, dy = dy)
                    }
                    // Current (most recent) position.
                    val x = event.getX(idx); val y = event.getY(idx)
                    val rawDx = (x - mouseLastX) * scale + mouseAccumDx
                    val rawDy = (y - mouseLastY) * scale + mouseAccumDy
                    val dx = rawDx.toInt().coerceIn(-32768, 32767)
                    val dy = rawDy.toInt().coerceIn(-32768, 32767)
                    mouseAccumDx = rawDx - dx
                    mouseAccumDy = rawDy - dy
                    mouseLastX = x; mouseLastY = y
                    overlay.updatePrimaryPointer(x, y)
                    viewModel.sendMouseReport(buttons = bits, dx = dx, dy = dy)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pid = event.getPointerId(event.actionIndex)
                val zone = pointerZone.remove(pid) ?: 0
                if (pid == mousePrimaryId) {
                    mousePrimaryId = -1
                    overlay.clearPrimaryPointer()
                }
                if (zone != 0) onZoneUp(zone, pid, config)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Release everything. Momentary buttons are released; latching remains.
                pointerZone.clear()
                mousePrimaryId = -1
                overlay.clearPrimaryPointer()

                // Release any momentary button presses.
                val hadLeft  = leftPressPointers.isNotEmpty()
                val hadRight = rightPressPointers.isNotEmpty()
                leftPressPointers.clear()
                rightPressPointers.clear()
                if (hadLeft  && config.leftButton.behavior  == ClickBehavior.MOMENTARY)
                    overlay.leftActive = false
                if (hadRight && config.rightButton.behavior == ClickBehavior.MOMENTARY)
                    overlay.rightActive = false

                // Send final report (latched buttons may still be active).
                viewModel.sendMouseReport(buttons = currentButtonBits(config), dx = 0, dy = 0)
            }
        }
        return true
    }

    // ────────────────────────────────────────────────────────────────────────
    // Zone press / release with momentary vs latching semantics
    // ────────────────────────────────────────────────────────────────────────

    private fun onZoneDown(zone: Int, pointerId: Int, config: TouchMouseConfig) {
        val overlay = binding.touchZoneOverlay
        if (zone == BTN_LEFT) {
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
        } else {
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
        viewModel.sendMouseReport(buttons = currentButtonBits(config), dx = 0, dy = 0)
    }

    private fun onZoneUp(zone: Int, pointerId: Int, config: TouchMouseConfig) {
        val overlay = binding.touchZoneOverlay
        if (zone == BTN_LEFT) {
            if (config.leftButton.behavior == ClickBehavior.MOMENTARY) {
                leftPressPointers.remove(pointerId)
                overlay.leftActive = leftPressPointers.isNotEmpty()
            }
        } else {
            if (config.rightButton.behavior == ClickBehavior.MOMENTARY) {
                rightPressPointers.remove(pointerId)
                overlay.rightActive = rightPressPointers.isNotEmpty()
            }
        }
        viewModel.sendMouseReport(buttons = currentButtonBits(config), dx = 0, dy = 0)
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
        return bits
    }

    // ────────────────────────────────────────────────────────────────────────
    // State & config observation
    // ────────────────────────────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val connected = state is HidManager.State.Connected
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

                    // Reset latching state when mode or button config changes.
                    if (config.mode == TouchMode.TOUCH) {
                        leftLatched = false; rightLatched = false
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
}
