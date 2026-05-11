package com.tablet.hid.ui.touchmouse

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import com.tablet.hid.HidViewModel
import kotlin.math.sqrt

class TouchModeHandler(
    context: Context,
    private val overlay: TouchZoneOverlayView,
    private val viewModel: HidViewModel,
    private val scrollEnabled: () -> Boolean,
) {

    companion object {
        const val BTN_LEFT = 1
        const val BTN_RIGHT = 2
        private const val TOUCH_SENSITIVITY = 1.5f
        private const val RIGHT_CLICK_ZONE_FRAC = 0.82f
        private const val SCROLL_PIXELS_PER_TICK = 50f
        private const val LATCH_HOLD_MS = 220L
        private const val LATCH_RELEASE_DIST = 20f
    }

    private var touchPrimaryId = -1
    private var touchLastX = 0f
    private var touchLastY = 0f
    private var touchRightClickActive = false
    private var touchLatchActive = false
    private var touchLatchJustActivated = false
    private var potentialLatch = false
    private var touchInitialX = 0f
    private var touchInitialY = 0f
    private val latchHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var threeFingerScrolling = false
    private var scrollCarryV = 0f
    private var scrollCarryH = 0f
    private var scrollLastX = 0f
    private var scrollLastY = 0f

    private val touchGesture by lazy {
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (threeFingerScrolling || touchLatchActive) return false
                potentialLatch = true
                latchHandler.postDelayed({
                    if (potentialLatch) {
                        potentialLatch = false
                        touchLatchActive = true
                        touchLatchJustActivated = true
                        overlay.leftActive = true
                        viewModel.sendMouseReport(buttons = BTN_LEFT)
                    }
                }, LATCH_HOLD_MS)
                return true
            }

            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                if (!potentialLatch) return false
                if (e.actionMasked == MotionEvent.ACTION_UP) {
                    latchHandler.removeCallbacksAndMessages(null)
                    potentialLatch = false
                    val btn = if (touchRightClickActive) BTN_RIGHT else BTN_LEFT
                    repeat(2) {
                        viewModel.sendMouseReport(buttons = btn)
                        viewModel.sendMouseReport(buttons = 0)
                    }
                }
                return true
            }
        })
    }

    private fun releaseLatch() {
        touchLatchActive = false
        touchLatchJustActivated = false
        overlay.leftActive = false
        viewModel.sendMouseReport(buttons = 0)
    }

    fun destroy() {
        latchHandler.removeCallbacksAndMessages(null)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun handle(event: MotionEvent): Boolean {
        touchGesture.onTouchEvent(event)

        val surfaceH = overlay.height.toFloat()
        val threshold = surfaceH * RIGHT_CLICK_ZONE_FRAC

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchInitialX = event.x; touchInitialY = event.y
                if (potentialLatch) {
                    touchPrimaryId = event.getPointerId(0)
                    touchLastX = event.x; touchLastY = event.y
                } else if (touchLatchActive) {
                    touchPrimaryId = event.getPointerId(0)
                    touchLastX = event.x; touchLastY = event.y
                } else {
                    val y = event.y
                    if (y >= threshold) {
                        touchRightClickActive = true
                        overlay.rightActive = true
                        viewModel.sendMouseReport(buttons = BTN_RIGHT)
                    } else {
                        touchPrimaryId = event.getPointerId(0)
                        touchLastX = event.x; touchLastY = event.y
                        val btn = if (touchRightClickActive) BTN_RIGHT else BTN_LEFT
                        viewModel.sendMouseReport(buttons = btn)
                    }
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val y = event.getY(idx)
                if (y >= threshold && !touchRightClickActive) {
                    touchRightClickActive = true
                    overlay.rightActive = true
                }
                if (event.pointerCount == 3 && !threeFingerScrolling && scrollEnabled()) {
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
                } else if (!potentialLatch) {
                    val idx = event.findPointerIndex(touchPrimaryId)
                    if (idx >= 0) {
                        val btn = when {
                            touchLatchActive     -> BTN_LEFT
                            touchRightClickActive -> BTN_RIGHT
                            else                 -> BTN_LEFT
                        }
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
                val anyInZone = (0 until event.pointerCount).any { i ->
                    event.getPointerId(i) != pid && event.getY(i) >= threshold
                }
                if (!anyInZone) {
                    touchRightClickActive = false
                    overlay.rightActive = false
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                latchHandler.removeCallbacksAndMessages(null)
                potentialLatch = false
                threeFingerScrolling = false
                scrollCarryV = 0f; scrollCarryH = 0f
                touchPrimaryId = -1
                if (touchLatchActive) {
                    if (touchLatchJustActivated) {
                        touchLatchJustActivated = false
                    } else {
                        val dx = event.x - touchInitialX; val dy = event.y - touchInitialY
                        if (sqrt(dx * dx + dy * dy) < LATCH_RELEASE_DIST) releaseLatch()
                    }
                } else {
                    touchRightClickActive = false
                    overlay.rightActive = false
                    viewModel.sendMouseReport(buttons = 0)
                }
            }
        }
        return true
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
