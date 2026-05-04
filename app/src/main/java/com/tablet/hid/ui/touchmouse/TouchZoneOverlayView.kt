package com.tablet.hid.ui.touchmouse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.tablet.hid.model.ButtonZoneConfig
import com.tablet.hid.model.TouchMode
import com.tablet.hid.model.TouchMouseConfig
import com.tablet.hid.model.ZoneType

class TouchZoneOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var config: TouchMouseConfig? = null
        set(value) { field = value; invalidate() }

    var leftActive: Boolean = false
        set(value) { if (field != value) { field = value; invalidate() } }

    var rightActive: Boolean = false
        set(value) { if (field != value) { field = value; invalidate() } }

    // null = not editing; true = editing left zone; false = editing right zone
    var editingLeft: Boolean? = null
        set(value) { field = value; invalidate() }

    var editDragStart: PointF? = null
    var editDragEnd: PointF? = null
        set(value) { field = value; invalidate() }

    private var primaryX = -1f
    private var primaryY = -1f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(200, 255, 255, 255)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val editStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(14f, 8f), 0f)
    }
    private val editFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    companion object {
        val LEFT_IDLE   = Color.argb(100,  30, 140, 255)
        val LEFT_ACTIVE = Color.argb(215,   0, 190, 255)
        val RIGHT_IDLE   = Color.argb(100, 255, 120,  20)
        val RIGHT_ACTIVE = Color.argb(215, 255, 170,   0)
        // Touch-mode right-click zone colour
        private val RC_IDLE   = Color.argb(26,  255,  68, 68)
        private val RC_ACTIVE = Color.argb(80,  255,  68, 68)
    }

    init { setWillNotDraw(false) }

    fun updatePrimaryPointer(x: Float, y: Float) {
        primaryX = x; primaryY = y
        val cfg = config ?: return
        if ((cfg.leftButton.enabled && cfg.leftButton.zoneType == ZoneType.DYNAMIC) ||
            (cfg.rightButton.enabled && cfg.rightButton.zoneType == ZoneType.DYNAMIC)) invalidate()
    }

    fun clearPrimaryPointer() { primaryX = -1f; primaryY = -1f; invalidate() }

    // Hit-test helpers used by the fragment for touch routing.
    fun hitTestLeft(x: Float, y: Float): Boolean {
        val btn = config?.leftButton ?: return false
        return btn.enabled && hitTest(btn, x, y)
    }

    fun hitTestRight(x: Float, y: Float): Boolean {
        val btn = config?.rightButton ?: return false
        return btn.enabled && hitTest(btn, x, y)
    }

    private fun hitTest(btn: ButtonZoneConfig, x: Float, y: Float): Boolean = when (btn.zoneType) {
        ZoneType.STATIC -> staticRect(btn).contains(x, y)
        ZoneType.DYNAMIC -> {
            if (primaryX < 0) false
            else {
                val (cx, cy, r) = dynamicCircle(btn)
                val dx = x - cx; val dy = y - cy
                dx * dx + dy * dy <= r * r
            }
        }
    }

    private fun staticRect(btn: ButtonZoneConfig) = RectF(
        btn.staticLeft * width, btn.staticTop * height,
        btn.staticRight * width, btn.staticBottom * height
    )

    private fun dynamicCircle(btn: ButtonZoneConfig): Triple<Float, Float, Float> {
        val minDim = minOf(width, height).toFloat()
        return Triple(
            primaryX + btn.dynamicOffsetX * minDim,
            primaryY + btn.dynamicOffsetY * minDim,
            btn.dynamicRadius * minDim
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cfg = config ?: return

        when (cfg.mode) {
            TouchMode.TOUCH -> drawTouchModeZone(canvas)
            TouchMode.MOUSE -> {
                if (cfg.leftButton.enabled)
                    drawZone(canvas, cfg.leftButton, "L", leftActive, LEFT_IDLE, LEFT_ACTIVE)
                if (cfg.rightButton.enabled)
                    drawZone(canvas, cfg.rightButton, "R", rightActive, RIGHT_IDLE, RIGHT_ACTIVE)
            }
        }

        // Rubber-band overlay for static zone editing
        val editing = editingLeft ?: return
        val start = editDragStart ?: return
        val end = editDragEnd ?: return

        val rect = RectF(
            minOf(start.x, end.x), minOf(start.y, end.y),
            maxOf(start.x, end.x), maxOf(start.y, end.y)
        )
        editFillPaint.color = if (editing) Color.argb(55, 30, 140, 255) else Color.argb(55, 255, 120, 20)
        editStrokePaint.color = if (editing) LEFT_ACTIVE else RIGHT_ACTIVE
        canvas.drawRoundRect(rect, 16f, 16f, editFillPaint)
        canvas.drawRoundRect(rect, 16f, 16f, editStrokePaint)
        if (rect.width() > 32f && rect.height() > 32f) {
            labelPaint.textSize = minOf(rect.width(), rect.height()) * 0.32f
            canvas.drawText(
                if (editing) "L" else "R",
                rect.centerX(), rect.centerY() + labelPaint.textSize * 0.38f,
                labelPaint
            )
        }
    }

    private fun drawTouchModeZone(canvas: Canvas) {
        val zoneTop = height * 0.82f
        val rect = RectF(0f, zoneTop, width.toFloat(), height.toFloat())
        fillPaint.color = if (rightActive) RC_ACTIVE else RC_IDLE
        canvas.drawRect(rect, fillPaint)
        strokePaint.color = Color.argb(60, 255, 68, 68)
        canvas.drawLine(0f, zoneTop, width.toFloat(), zoneTop, strokePaint)
        strokePaint.color = Color.argb(200, 255, 255, 255)

        val textSize = 12f * resources.displayMetrics.density
        labelPaint.textSize = textSize
        labelPaint.alpha = 102  // ~40%
        canvas.drawText(
            if (rightActive) "RIGHT CLICK — ACTIVE" else "RIGHT CLICK ZONE",
            width / 2f, zoneTop + (height - zoneTop) / 2f + textSize * 0.38f,
            labelPaint
        )
        labelPaint.alpha = 255
    }

    private fun drawZone(
        canvas: Canvas, btn: ButtonZoneConfig, label: String, active: Boolean,
        idleColor: Int, activeColor: Int
    ) {
        fillPaint.color = if (active) activeColor else idleColor
        when (btn.zoneType) {
            ZoneType.STATIC -> {
                val rect = staticRect(btn)
                canvas.drawRoundRect(rect, 20f, 20f, fillPaint)
                canvas.drawRoundRect(rect, 20f, 20f, strokePaint)
                labelPaint.textSize = minOf(rect.width(), rect.height()) * 0.35f
                canvas.drawText(label, rect.centerX(), rect.centerY() + labelPaint.textSize * 0.38f, labelPaint)
            }
            ZoneType.DYNAMIC -> {
                if (primaryX < 0) return
                val (cx, cy, r) = dynamicCircle(btn)
                canvas.drawCircle(cx, cy, r, fillPaint)
                canvas.drawCircle(cx, cy, r, strokePaint)
                labelPaint.textSize = r * 0.62f
                canvas.drawText(label, cx, cy + labelPaint.textSize * 0.38f, labelPaint)
            }
        }
    }
}
