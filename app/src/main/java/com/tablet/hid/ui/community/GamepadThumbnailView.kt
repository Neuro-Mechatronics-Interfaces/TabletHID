package com.tablet.hid.ui.community

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.tablet.hid.util.GamepadElementRect
import com.tablet.hid.util.GamepadLayoutResolver
import org.json.JSONObject

class GamepadThumbnailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val REF_LONG = 600f
        private const val REF_SHORT = 340f
        private val BUTTON_COLORS = mapOf(
            "a" to 0xFF4ade80.toInt(),
            "b" to 0xFFf87171.toInt(),
            "x" to 0xFF60a5fa.toInt(),
            "y" to 0xFFfbbf24.toInt(),
        )
        private const val NEUTRAL_COLOR = 0xFFb0b8c1.toInt()
        private val BUTTON_KEYS = listOf(
            "a", "b", "x", "y", "lb", "rb", "lt", "rt",
            "back", "start", "dpadUp", "dpadDown", "dpadLeft", "dpadRight",
        )
    }

    private var isLandscape = true
    private var layout: Map<String, GamepadElementRect> = emptyMap()
    private val buttonEnabled = mutableMapOf<String, Boolean>()
    private val buttonOX = mutableMapOf<String, Float>()
    private val buttonOY = mutableMapOf<String, Float>()
    private val buttonSX = mutableMapOf<String, Float>()
    private val buttonSY = mutableMapOf<String, Float>()
    private var leftJoyEnabled = true
    private var rightJoyEnabled = true
    private var singleMode = false
    private var leftOX = 0f; private var leftOY = 0f; private var leftSX = 1f
    private var rightOX = 0f; private var rightOY = 0f; private var rightSX = 1f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFF0f0f1a.toInt() }
    private val rect = RectF()

    fun setLandscape(landscape: Boolean) {
        if (isLandscape == landscape) return
        isLandscape = landscape
        layout = emptyMap()
        requestLayout()
        invalidate()
    }

    fun setConfigJson(json: String) {
        try {
            val root = JSONObject(json)
            val buttons = root.optJSONObject("buttons") ?: JSONObject()
            for (key in BUTTON_KEYS) {
                val btn = buttons.optJSONObject(key)
                buttonEnabled[key] = btn?.optBoolean("enabled", true) ?: true
                buttonOX[key] = btn?.optDouble("offsetX", 0.0)?.toFloat() ?: 0f
                buttonOY[key] = btn?.optDouble("offsetY", 0.0)?.toFloat() ?: 0f
                buttonSX[key] = btn?.optDouble("scaleX", 1.0)?.toFloat() ?: 1f
                buttonSY[key] = btn?.optDouble("scaleY", 1.0)?.toFloat() ?: 1f
            }
            val leftJs = root.optJSONObject("leftJoystick")
            val rightJs = root.optJSONObject("rightJoystick")
            leftJoyEnabled = leftJs?.optBoolean("enabled", true) ?: true
            rightJoyEnabled = rightJs?.optBoolean("enabled", true) ?: true
            singleMode = root.optBoolean("singleJoystickMode", false)
            leftOX = leftJs?.optDouble("offsetX", 0.0)?.toFloat() ?: 0f
            leftOY = leftJs?.optDouble("offsetY", 0.0)?.toFloat() ?: 0f
            leftSX = leftJs?.optDouble("scaleX", 1.0)?.toFloat() ?: 1f
            rightOX = rightJs?.optDouble("offsetX", 0.0)?.toFloat() ?: 0f
            rightOY = rightJs?.optDouble("offsetY", 0.0)?.toFloat() ?: 0f
            rightSX = rightJs?.optDouble("scaleX", 1.0)?.toFloat() ?: 1f
        } catch (_: Exception) { }
        layout = emptyMap()
        ensureLayout()
        invalidate()
    }

    private fun refW() = if (isLandscape) REF_LONG else REF_SHORT
    private fun refH() = if (isLandscape) REF_SHORT else REF_LONG

    private fun ensureLayout() {
        if (layout.isEmpty()) {
            layout = GamepadLayoutResolver.resolveLayout(context, refW(), refH())
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ensureLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val h = (w * refH() / refW()).toInt().coerceAtLeast(1)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        ensureLayout()
        if (layout.isEmpty()) return
        val scale = width.toFloat() / refW()
        val cornerBg = 12f * scale

        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerBg, cornerBg, bgPaint)

        for (key in BUTTON_KEYS) {
            val nat = layout[key] ?: continue
            if (buttonEnabled[key] == false) continue
            drawButton(canvas, key, nat, scale)
        }

        val leftNat = layout["leftJoystick"]
        val rightNat = layout["rightJoystick"]
        if (leftJoyEnabled && leftNat != null) {
            drawJoystick(canvas, leftNat, leftOX, leftOY, leftSX, scale)
        }
        if (rightJoyEnabled && !singleMode && rightNat != null) {
            drawJoystick(canvas, rightNat, rightOX, rightOY, rightSX, scale)
        }
    }

    private fun drawButton(canvas: Canvas, key: String, nat: GamepadElementRect, scale: Float) {
        val ox = buttonOX[key] ?: 0f
        val oy = buttonOY[key] ?: 0f
        val sx = buttonSX[key] ?: 1f
        val sy = buttonSY[key] ?: 1f
        val cx = (nat.left + nat.w / 2f + ox) * scale
        val cy = (nat.top + nat.h / 2f + oy) * scale
        val hw = nat.w * sx * scale / 2f
        val hh = nat.h * sy * scale / 2f
        val baseColor = BUTTON_COLORS[key] ?: NEUTRAL_COLOR
        val isTrigger = key == "lt" || key == "rt"
        val isDpad = key.startsWith("dpad")
        val corner = when {
            isTrigger -> 4f * scale
            isDpad    -> 3f * scale
            else      -> 14f * scale
        }
        rect.set(cx - hw, cy - hh, cx + hw, cy + hh)
        fillPaint.color = (0x33 shl 24) or (baseColor and 0x00FFFFFF)
        canvas.drawRoundRect(rect, corner, corner, fillPaint)
        strokePaint.color = (0x99 shl 24) or (baseColor and 0x00FFFFFF)
        canvas.drawRoundRect(rect, corner, corner, strokePaint)
    }

    private fun drawJoystick(
        canvas: Canvas, nat: GamepadElementRect,
        ox: Float, oy: Float, sx: Float, scale: Float,
    ) {
        val cx = (nat.left + nat.w / 2f + ox) * scale
        val cy = (nat.top + nat.h / 2f + oy) * scale
        val radius = nat.w * sx * scale / 2f

        fillPaint.color = 0x1AFFFFFF
        canvas.drawCircle(cx, cy, radius, fillPaint)
        strokePaint.color = 0x60FFFFFF
        canvas.drawCircle(cx, cy, radius, strokePaint)

        val knobRadius = radius * 0.38f
        fillPaint.color = 0xBFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, knobRadius, fillPaint)
    }
}
