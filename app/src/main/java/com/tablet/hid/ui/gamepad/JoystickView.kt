package com.tablet.hid.ui.gamepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    fun interface JoystickListener {
        /** normalizedX and normalizedY are in [-1f, 1f]. */
        fun onMoved(normalizedX: Float, normalizedY: Float)
    }

    var listener: JoystickListener? = null

    /** Values in [0, 1]. Input magnitude below this threshold outputs zero. */
    var deadzone: Float = 0f
    /** Output multiplier applied after deadzone rescaling, clamped to ±1. */
    var gain: Float = 1f

    /** Tints the outer ring and fill. Alpha of the ring color is used as-is;
     *  fill alpha is half of ring alpha so the two layers stay distinct. */
    var accentColor: Int = Color.parseColor("#66FFFFFF")
        set(value) {
            field = value
            outerRingPaint.color = value
            val halfAlpha = (Color.alpha(value) / 2).coerceIn(0, 255)
            outerPaint.color = Color.argb(
                halfAlpha, Color.red(value), Color.green(value), Color.blue(value))
            invalidate()
        }

    private var knobOffsetX = 0f
    private var knobOffsetY = 0f
    private var activePointerId = -1

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.FILL
    }

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        style = Paint.Style.FILL
    }

    private val centerX get() = width / 2f
    private val centerY get() = height / 2f
    private val outerRadius get() = (min(width, height) / 2f) - 6f
    private val knobRadius get() = outerRadius * 0.38f
    private val maxKnobOffset get() = outerRadius - knobRadius

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(centerX, centerY, outerRadius, outerPaint)
        canvas.drawCircle(centerX, centerY, outerRadius, outerRingPaint)
        canvas.drawCircle(centerX + knobOffsetX, centerY + knobOffsetY, knobRadius, knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                updateKnob(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(activePointerId)
                if (idx >= 0) updateKnob(event.getX(idx), event.getY(idx))
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    recenter()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                recenter()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateKnob(touchX: Float, touchY: Float) {
        val dx = touchX - centerX
        val dy = touchY - centerY
        val dist = sqrt(dx * dx + dy * dy)
        val max = maxKnobOffset

        if (dist <= max) {
            knobOffsetX = dx
            knobOffsetY = dy
        } else {
            val angle = atan2(dy, dx)
            knobOffsetX = cos(angle) * max
            knobOffsetY = sin(angle) * max
        }

        invalidate()
        val norm = if (max > 0f) 1f / max else 0f
        var nx = knobOffsetX * norm
        var ny = knobOffsetY * norm
        val mag = sqrt(nx * nx + ny * ny)
        if (mag < deadzone) {
            nx = 0f; ny = 0f
        } else if (mag > 0f) {
            val rescaled = ((mag - deadzone) / (1f - deadzone).coerceAtLeast(0.001f)) * gain
            val s = rescaled / mag
            nx = (nx * s).coerceIn(-1f, 1f)
            ny = (ny * s).coerceIn(-1f, 1f)
        }
        listener?.onMoved(nx, ny)
    }

    private fun recenter() {
        activePointerId = -1
        knobOffsetX = 0f
        knobOffsetY = 0f
        invalidate()
        listener?.onMoved(0f, 0f)
    }
}
