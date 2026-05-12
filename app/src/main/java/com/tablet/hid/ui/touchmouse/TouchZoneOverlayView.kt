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
import com.tablet.hid.model.MouseButton
import com.tablet.hid.model.TouchMode
import com.tablet.hid.model.TouchMouseConfig
import com.tablet.hid.model.TouchMouseSubRegionConfig
import com.tablet.hid.model.ZoneType
import com.tablet.hid.util.UiPalette

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

    var editingSniper: Boolean = false
        set(value) { field = value; invalidate() }

    var sniperActive: Boolean = false
        set(value) { if (field != value) { field = value; invalidate() } }

    var palette: UiPalette = UiPalette.ENTRIES[0]
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

    fun hitTestSniper(x: Float, y: Float): Boolean {
        val cfg = config ?: return false
        if (!cfg.sniperEnabled) return false
        return RectF(
            cfg.sniperLeft * width, cfg.sniperTop * height,
            cfg.sniperRight * width, cfg.sniperBottom * height
        ).contains(x, y)
    }

    fun hitTestButtonBits(x: Float, y: Float): Int {
        val cfg = config ?: return 0
        var bits = 0
        if (cfg.leftButton.enabled) bits = bits or hitTestButtonBits(cfg.leftButton, MouseButton.LEFT, x, y)
        if (cfg.rightButton.enabled) bits = bits or hitTestButtonBits(cfg.rightButton, MouseButton.RIGHT, x, y)
        return bits
    }

    private fun hitTestButtonBits(
        btn: ButtonZoneConfig,
        defaultButton: MouseButton,
        x: Float,
        y: Float,
    ): Int {
        var bits = 0
        if (hitTest(btn, x, y)) bits = bits or defaultButton.bit
        btn.subRegions.forEach { subRegion ->
            if (subRegion.enabled && hitTest(subRegion, x, y)) {
                bits = bits or (subRegion.alternateMouseButton ?: defaultButton).bit
            }
        }
        return bits
    }

    fun hitTestKeyboardModifiers(x: Float, y: Float): Int {
        val cfg = config ?: return 0
        var modifiers = 0
        listOf(cfg.leftButton, cfg.rightButton).forEach { btn ->
            btn.subRegions.forEach { subRegion ->
                if (subRegion.enabled && subRegion.keyboardModifiers != 0 && hitTest(subRegion, x, y)) {
                    modifiers = modifiers or subRegion.keyboardModifiers
                }
            }
        }
        return modifiers
    }

    private fun hitTest(btn: ButtonZoneConfig, x: Float, y: Float): Boolean = when (btn.zoneType) {
        ZoneType.STATIC -> staticRect(btn).contains(x, y)
        ZoneType.DYNAMIC -> hitTestDynamic(btn.dynamicOffsetX, btn.dynamicOffsetY, btn.dynamicRadius, x, y)
    }

    private fun hitTest(subRegion: TouchMouseSubRegionConfig, x: Float, y: Float): Boolean =
        when (subRegion.zoneType) {
            ZoneType.STATIC -> staticRect(subRegion).contains(x, y)
            ZoneType.DYNAMIC -> hitTestDynamic(
                subRegion.dynamicOffsetX,
                subRegion.dynamicOffsetY,
                subRegion.dynamicRadius,
                x,
                y,
            )
        }

    private fun hitTestDynamic(offsetX: Float, offsetY: Float, radius: Float, x: Float, y: Float): Boolean {
        val cfg = config ?: return false
        val useShared = cfg.sharedDynamicZone
        return if (primaryX < 0) {
            false
        } else {
            val (cx, cy, r) = if (useShared) {
                dynamicCircle(cfg.sharedDynamicOffsetX, cfg.sharedDynamicOffsetY, cfg.sharedDynamicRadius)
            } else {
                dynamicCircle(offsetX, offsetY, radius)
            }
            val dx = x - cx; val dy = y - cy
            dx * dx + dy * dy <= r * r
        }
    }

    private fun staticRect(btn: ButtonZoneConfig) = RectF(
        btn.staticLeft * width, btn.staticTop * height,
        btn.staticRight * width, btn.staticBottom * height
    )

    private fun staticRect(subRegion: TouchMouseSubRegionConfig) = RectF(
        subRegion.staticLeft * width, subRegion.staticTop * height,
        subRegion.staticRight * width, subRegion.staticBottom * height
    )

    private fun dynamicCircle(offsetX: Float, offsetY: Float, radius: Float): Triple<Float, Float, Float> {
        val minDim = minOf(width, height).toFloat()
        return Triple(
            primaryX + offsetX * minDim,
            primaryY + offsetY * minDim,
            radius * minDim
        )
    }

    private fun dynamicCircle(btn: ButtonZoneConfig): Triple<Float, Float, Float> {
        val cfg = config
        return if (cfg?.sharedDynamicZone == true) {
            dynamicCircle(cfg.sharedDynamicOffsetX, cfg.sharedDynamicOffsetY, cfg.sharedDynamicRadius)
        } else {
            dynamicCircle(btn.dynamicOffsetX, btn.dynamicOffsetY, btn.dynamicRadius)
        }
    }

    private fun hasDynamicZones(cfg: TouchMouseConfig) =
        (cfg.leftButton.enabled && cfg.leftButton.zoneType == ZoneType.DYNAMIC) ||
            (cfg.rightButton.enabled && cfg.rightButton.zoneType == ZoneType.DYNAMIC)

    private fun sharedDynamicLabel(cfg: TouchMouseConfig): String {
        val parts = mutableListOf<String>()
        if (cfg.leftButton.enabled && cfg.leftButton.zoneType == ZoneType.DYNAMIC) parts += "L"
        if (cfg.rightButton.enabled && cfg.rightButton.zoneType == ZoneType.DYNAMIC) parts += "R"
        return parts.joinToString("+")
    }

    private fun sharedDynamicActive(cfg: TouchMouseConfig): Boolean =
        (cfg.leftButton.enabled && cfg.leftButton.zoneType == ZoneType.DYNAMIC && leftActive) ||
            (cfg.rightButton.enabled && cfg.rightButton.zoneType == ZoneType.DYNAMIC && rightActive)

    private fun sharedDynamicColor(cfg: TouchMouseConfig): Int =
        if (sharedDynamicActive(cfg)) palette.sharedActive else palette.sharedIdle

    override fun onDraw(canvas: Canvas) {
        val cfg = config ?: return

        when (cfg.mode) {
            TouchMode.TOUCH -> drawTouchModeZone(canvas)
            TouchMode.MOUSE -> {
                if (cfg.leftButton.enabled && (!cfg.sharedDynamicZone || cfg.leftButton.zoneType != ZoneType.DYNAMIC))
                    drawZone(canvas, cfg.leftButton, "L", leftActive, palette.leftIdle, palette.leftActive)
                if (cfg.rightButton.enabled && (!cfg.sharedDynamicZone || cfg.rightButton.zoneType != ZoneType.DYNAMIC))
                    drawZone(canvas, cfg.rightButton, "R", rightActive, palette.rightIdle, palette.rightActive)
                if (cfg.sharedDynamicZone && hasDynamicZones(cfg)) drawSharedDynamicZone(canvas, cfg)
                drawSubRegions(canvas, cfg.leftButton, palette.leftActive)
                drawSubRegions(canvas, cfg.rightButton, palette.rightActive)
                if (cfg.sniperEnabled) drawSniperZone(canvas, cfg)
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
        editFillPaint.color = when {
            editingSniper -> palette.sniperWithAlpha(55)
            editing       -> palette.leftWithAlpha(55)
            else          -> palette.rightWithAlpha(55)
        }
        editStrokePaint.color = when {
            editingSniper -> palette.sniperActive
            editing       -> palette.leftActive
            else          -> palette.rightActive
        }
        canvas.drawRoundRect(rect, 16f, 16f, editFillPaint)
        canvas.drawRoundRect(rect, 16f, 16f, editStrokePaint)
        if (rect.width() > 32f && rect.height() > 32f) {
            labelPaint.textSize = minOf(rect.width(), rect.height()) * 0.32f
            canvas.drawText(
                when { editingSniper -> "S"; editing -> "L"; else -> "R" },
                rect.centerX(), rect.centerY() + labelPaint.textSize * 0.38f,
                labelPaint
            )
        }
    }

    private fun drawTouchModeZone(canvas: Canvas) {
        val zoneTop = height * 0.82f
        val rect = RectF(0f, zoneTop, width.toFloat(), height.toFloat())
        fillPaint.color = if (rightActive) palette.rightWithAlpha(80) else palette.rightWithAlpha(26)
        canvas.drawRect(rect, fillPaint)
        strokePaint.color = palette.rightWithAlpha(60)
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

    private fun drawSharedDynamicZone(canvas: Canvas, cfg: TouchMouseConfig) {
        if (primaryX < 0) return
        val (cx, cy, r) = dynamicCircle(
            cfg.sharedDynamicOffsetX,
            cfg.sharedDynamicOffsetY,
            cfg.sharedDynamicRadius,
        )
        fillPaint.color = sharedDynamicColor(cfg)
        canvas.drawCircle(cx, cy, r, fillPaint)
        canvas.drawCircle(cx, cy, r, strokePaint)
        labelPaint.textSize = r * 0.56f
        canvas.drawText(sharedDynamicLabel(cfg), cx, cy + labelPaint.textSize * 0.38f, labelPaint)
    }

    private fun drawSniperZone(canvas: Canvas, cfg: TouchMouseConfig) {
        val rect = RectF(
            cfg.sniperLeft * width, cfg.sniperTop * height,
            cfg.sniperRight * width, cfg.sniperBottom * height
        )
        fillPaint.color = if (sniperActive) palette.sniperActive else palette.sniperIdle
        canvas.drawRoundRect(rect, 20f, 20f, fillPaint)
        canvas.drawRoundRect(rect, 20f, 20f, strokePaint)
        labelPaint.textSize = minOf(rect.width(), rect.height()) * 0.35f
        canvas.drawText("S", rect.centerX(), rect.centerY() + labelPaint.textSize * 0.38f, labelPaint)
    }

    private fun drawSubRegions(canvas: Canvas, btn: ButtonZoneConfig, color: Int) {
        btn.subRegions.forEach { subRegion ->
            if (!subRegion.enabled || subRegion.zoneType != ZoneType.STATIC) return@forEach
            val rect = staticRect(subRegion)
            fillPaint.color = Color.argb(75, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawRoundRect(rect, 14f, 14f, fillPaint)
            strokePaint.color = Color.argb(210, 255, 255, 255)
            canvas.drawRoundRect(rect, 14f, 14f, strokePaint)
            labelPaint.textSize = minOf(rect.width(), rect.height()) * 0.28f
            canvas.drawText(
                subRegion.alternateMouseButton?.name?.take(1) ?: "MOD",
                rect.centerX(),
                rect.centerY() + labelPaint.textSize * 0.38f,
                labelPaint,
            )
        }
    }
}
