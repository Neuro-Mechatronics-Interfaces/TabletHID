package com.tablet.hid.ui.touchmouse

import android.view.MotionEvent
import com.tablet.hid.HidViewModel
import com.tablet.hid.model.ClickBehavior
import com.tablet.hid.model.TouchMouseConfig

class MouseModeHandler(
    private val overlay: TouchZoneOverlayView,
    private val viewModel: HidViewModel,
    private val scrollEnabled: () -> Boolean,
) {

    companion object {
        private const val BTN_LEFT = 1
        private const val BTN_RIGHT = 2
        private const val BTN_MIDDLE = 4
        private const val SCROLL_PIXELS_PER_TICK = 50f
    }

    private var mousePrimaryId = -1
    private var mouseLastX = 0f
    private var mouseLastY = 0f
    private val pointerZone = mutableMapOf<Int, Int>()
    private val pointerKeyboardModifiers = mutableMapOf<Int, Int>()
    private val leftPressPointers = mutableSetOf<Int>()
    private val rightPressPointers = mutableSetOf<Int>()
    private val middlePressPointers = mutableSetOf<Int>()
    private val sniperPointers = mutableSetOf<Int>()
    private var leftLatched = false
    private var rightLatched = false

    private var threeFingerScrolling = false
    private var scrollCarryV = 0f
    private var scrollCarryH = 0f
    private var scrollLastX = 0f
    private var scrollLastY = 0f

    fun resetLatchState() {
        leftLatched = false
        rightLatched = false
        middlePressPointers.clear()
    }

    fun handle(event: MotionEvent, config: TouchMouseConfig): Boolean {
        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                val pid = event.getPointerId(0)
                val x = event.x; val y = event.y
                if (config.sniperEnabled && overlay.hitTestSniper(x, y)) {
                    sniperPointers.add(pid)
                    overlay.sniperActive = true
                } else {
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
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                val x = event.getX(idx); val y = event.getY(idx)

                if (config.sniperEnabled && overlay.hitTestSniper(x, y)) {
                    sniperPointers.add(pid)
                    overlay.sniperActive = true
                } else {
                    val zones = overlay.hitTestButtonBits(x, y)
                    val modifiers = overlay.hitTestKeyboardModifiers(x, y)
                    pointerZone[pid] = zones
                    pointerKeyboardModifiers[pid] = modifiers
                    updateSubRegionKeyboardModifiers()
                    if (zones != 0) {
                        onZoneDown(zones, pid, config)
                    } else if (mousePrimaryId == -1) {
                        mousePrimaryId = pid
                        mouseLastX = x; mouseLastY = y
                        overlay.updatePrimaryPointer(x, y)
                    }
                }
                if (event.pointerCount == 3 && !threeFingerScrolling && scrollEnabled()) {
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
                        val baseScale = config.sensitivity * 0.3f
                        val scale = if (sniperPointers.isNotEmpty()) baseScale / config.sniperDivisor else baseScale
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
                if (sniperPointers.remove(pid)) {
                    overlay.sniperActive = sniperPointers.isNotEmpty()
                } else {
                    val zones = pointerZone.remove(pid) ?: 0
                    pointerKeyboardModifiers.remove(pid)
                    updateSubRegionKeyboardModifiers()
                    if (pid == mousePrimaryId) {
                        mousePrimaryId = -1
                        overlay.clearPrimaryPointer()
                    }
                    if (zones != 0) onZoneUp(zones, pid, config)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                threeFingerScrolling = false
                scrollCarryV = 0f; scrollCarryH = 0f
                sniperPointers.clear()
                overlay.sniperActive = false
                pointerZone.clear()
                pointerKeyboardModifiers.clear()
                updateSubRegionKeyboardModifiers()
                mousePrimaryId = -1
                overlay.clearPrimaryPointer()

                val hadLeft  = leftPressPointers.isNotEmpty()
                val hadRight = rightPressPointers.isNotEmpty()
                leftPressPointers.clear()
                rightPressPointers.clear()
                middlePressPointers.clear()
                if (hadLeft  && config.leftButton.behavior  == ClickBehavior.MOMENTARY)
                    overlay.leftActive = false
                if (hadRight && config.rightButton.behavior == ClickBehavior.MOMENTARY)
                    overlay.rightActive = false

                viewModel.sendMouseReport(buttons = currentButtonBits(config))
            }
        }
        return true
    }

    private fun onZoneDown(zones: Int, pointerId: Int, config: TouchMouseConfig) {
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

    private fun handleScrollMove(event: MotionEvent) {
        val invert = viewModel.touchMouseConfig.value.invertScroll
        val vSign = if (invert) 1f else -1f
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
}
