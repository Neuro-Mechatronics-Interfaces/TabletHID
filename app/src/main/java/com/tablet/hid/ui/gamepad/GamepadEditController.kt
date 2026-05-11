package com.tablet.hid.ui.gamepad

import android.content.res.Resources
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButton
import com.tablet.hid.HidViewModel
import com.tablet.hid.R
import com.tablet.hid.databinding.FragmentGamepadBinding
import com.tablet.hid.model.ButtonConfig
import com.tablet.hid.model.GamepadConfig
import com.tablet.hid.model.JoystickConfig
import com.tablet.hid.model.KeyboardMacroButtonConfig
import com.tablet.hid.ui.shared.ButtonLayoutSheet
import kotlin.math.abs

class GamepadEditController(
    private val binding: FragmentGamepadBinding,
    private val viewModel: HidViewModel,
    private val resources: Resources,
    private val fragmentManager: FragmentManager,
    private val onEditStateChanged: (Boolean) -> Unit,
) {

    companion object {
        private const val TAP_THRESHOLD_DP = 8f
    }

    var editMode = false
        private set

    private val macroButtonViews = mutableListOf<Pair<MaterialButton, KeyboardMacroButtonConfig>>()
    private val macroNaturalPositions = mutableListOf<android.graphics.PointF>()

    fun enterEditMode() {
        editMode = true
        binding.editModeBanner.visibility = View.VISIBLE

        fun attach(v: View, getB: (GamepadConfig) -> ButtonConfig,
                   setB: (GamepadConfig, ButtonConfig) -> GamepadConfig) =
            v.setOnTouchListener(editTouchListener(v,
                getConfig = { getB(viewModel.gamepadConfig.value) },
                saveConfig = { bc -> viewModel.updateGamepadConfig(setB(viewModel.gamepadConfig.value, bc)) }
            ))

        fun attachJoy(v: View, label: String,
                      getJ: (GamepadConfig) -> JoystickConfig,
                      setJ: (GamepadConfig, JoystickConfig) -> GamepadConfig) =
            v.setOnTouchListener(editJoyListener(v, label,
                getConfig = { getJ(viewModel.gamepadConfig.value) },
                saveConfig = { jc -> viewModel.updateGamepadConfig(setJ(viewModel.gamepadConfig.value, jc)) }
            ))

        attach(binding.btnA,      { it.btnA },     { c, b -> c.copy(btnA = b) })
        attach(binding.btnB,      { it.btnB },     { c, b -> c.copy(btnB = b) })
        attach(binding.btnX,      { it.btnX },     { c, b -> c.copy(btnX = b) })
        attach(binding.btnY,      { it.btnY },     { c, b -> c.copy(btnY = b) })
        attach(binding.btnLb,     { it.btnLb },    { c, b -> c.copy(btnLb = b) })
        attach(binding.btnRb,     { it.btnRb },    { c, b -> c.copy(btnRb = b) })
        attach(binding.btnLt,     { it.btnLt },    { c, b -> c.copy(btnLt = b) })
        attach(binding.btnRt,     { it.btnRt },    { c, b -> c.copy(btnRt = b) })
        attach(binding.btnBack,   { it.btnBack },  { c, b -> c.copy(btnBack = b) })
        attach(binding.btnStart,  { it.btnStart }, { c, b -> c.copy(btnStart = b) })
        attach(binding.dpadUp,    { it.dpadUp },   { c, b -> c.copy(dpadUp = b) })
        attach(binding.dpadDown,  { it.dpadDown }, { c, b -> c.copy(dpadDown = b) })
        attach(binding.dpadLeft,  { it.dpadLeft }, { c, b -> c.copy(dpadLeft = b) })
        attach(binding.dpadRight, { it.dpadRight },{ c, b -> c.copy(dpadRight = b) })
        attachJoy(binding.leftJoystick,  "Left Stick",  { it.leftJoystick },  { c, j -> c.copy(leftJoystick = j) })
        attachJoy(binding.rightJoystick, "Right Stick", { it.rightJoystick }, { c, j -> c.copy(rightJoystick = j) })

        if (binding.btnSingleJoystickSide.visibility == View.VISIBLE) {
            attach(binding.btnSingleJoystickSide,
                { it.singleJoystickSideBtn },
                { c, b -> c.copy(singleJoystickSideBtn = b) })
        }

        allButtonViews().forEach { it.alpha = 0.6f }
        attachMacroEditListeners()
    }

    fun exitEditMode() {
        saveAllMacroLayouts()
        editMode = false
        binding.editModeBanner.visibility = View.GONE
        binding.leftJoystick.setOnTouchListener(null)
        binding.rightJoystick.setOnTouchListener(null)
        binding.btnSingleJoystickSide.setOnTouchListener(null)
        allButtonViews().forEach { it.alpha = 1f }
        macroButtonViews.forEach { (button, macro) ->
            button.alpha = 1f
            button.setMacroNormalListener(macro)
        }
        onEditStateChanged(false)
    }

    fun renderMacroButtons(cfg: GamepadConfig) {
        macroButtonViews.clear()
        macroNaturalPositions.clear()
        binding.macroOverlay.removeAllViews()
        if (cfg.macroButtons.isEmpty()) {
            if (editMode) attachMacroEditListeners()
            return
        }
        val dp = resources.displayMetrics.density
        cfg.macroButtons.forEach { macro ->
            val button = MaterialButton(binding.root.context).apply {
                text = macro.label
                minHeight = resources.getDimensionPixelSize(R.dimen.macro_button_height)
                minWidth = 0
                setPadding(18, 0, 18, 0)
                scaleX = macro.layoutScaleX
                scaleY = macro.layoutScaleY
                setMacroNormalListener(macro)
            }
            binding.macroOverlay.addView(button, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            ))
            macroButtonViews += button to macro
        }
        binding.macroOverlay.post {
            if (binding.root.isAttachedToWindow.not()) return@post
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
        }
        if (editMode) attachMacroEditListeners()
    }

    private fun allButtonViews(): List<View> = listOf(
        binding.btnA, binding.btnB, binding.btnX, binding.btnY,
        binding.btnLb, binding.btnRb, binding.btnLt, binding.btnRt,
        binding.btnBack, binding.btnStart,
        binding.dpadUp, binding.dpadDown, binding.dpadLeft, binding.dpadRight,
        binding.leftJoystick, binding.rightJoystick,
        binding.btnSingleJoystickSide,
    )

    private fun saveAllMacroLayouts() {
        val dp = resources.displayMetrics.density
        val config = viewModel.gamepadConfig.value
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
        viewModel.updateGamepadConfig(config.copy(macroButtons = macros))
    }

    // ── Sheet helpers ─────────────────────────────────────────────────────────

    private fun showLayoutSheetForButton(
        view: View,
        getConfig: () -> ButtonConfig,
        saveConfig: (ButtonConfig) -> Unit,
    ) {
        val label = (view as? android.widget.Button)?.text?.toString() ?: "Button"
        val density = resources.displayMetrics.density
        val cfg = getConfig()
        ButtonLayoutSheet().apply {
            elementTitle = label
            initialOffsetX = cfg.offsetX.coerceIn(-400f, 400f)
            initialOffsetY = cfg.offsetY.coerceIn(-400f, 400f)
            initialScaleX  = cfg.scaleX.coerceIn(0.3f, 3.0f)
            initialScaleY  = cfg.scaleY.coerceIn(0.3f, 3.0f)
            onUpdate = { ox, oy, sx, sy ->
                view.translationX = ox * density
                view.translationY = oy * density
                view.scaleX = sx; view.scaleY = sy
                clampTranslationToParent(view)
                saveConfig(getConfig().copy(
                    offsetX = view.translationX / density,
                    offsetY = view.translationY / density,
                    scaleX = sx, scaleY = sy,
                ))
            }
        }.show(fragmentManager, "buttonLayout")
    }

    private fun showLayoutSheetForJoystick(
        view: View,
        label: String,
        getConfig: () -> JoystickConfig,
        saveConfig: (JoystickConfig) -> Unit,
    ) {
        val density = resources.displayMetrics.density
        val cfg = getConfig()
        ButtonLayoutSheet().apply {
            elementTitle = label
            initialOffsetX = cfg.offsetX.coerceIn(-400f, 400f)
            initialOffsetY = cfg.offsetY.coerceIn(-400f, 400f)
            initialScaleX  = cfg.scaleX.coerceIn(0.3f, 3.0f)
            initialScaleY  = cfg.scaleY.coerceIn(0.3f, 3.0f)
            onUpdate = { ox, oy, sx, sy ->
                view.translationX = ox * density
                view.translationY = oy * density
                view.scaleX = sx; view.scaleY = sy
                clampTranslationToParent(view)
                saveConfig(getConfig().copy(
                    offsetX = view.translationX / density,
                    offsetY = view.translationY / density,
                    scaleX = sx, scaleY = sy,
                ))
            }
        }.show(fragmentManager, "buttonLayout")
    }

    private fun showLayoutSheetForMacro(view: View, index: Int) {
        if (index >= macroNaturalPositions.size || index >= macroButtonViews.size) return
        val density = resources.displayMetrics.density
        val natural = macroNaturalPositions[index]
        val (_, macro) = macroButtonViews[index]
        ButtonLayoutSheet().apply {
            elementTitle = macro.label
            initialOffsetX = macro.layoutOffsetX.coerceIn(-400f, 400f)
            initialOffsetY = macro.layoutOffsetY.coerceIn(-400f, 400f)
            initialScaleX  = macro.layoutScaleX.coerceIn(0.3f, 3.0f)
            initialScaleY  = macro.layoutScaleY.coerceIn(0.3f, 3.0f)
            // Live preview — only update the view, don't save (avoid triggering re-render)
            onUpdate = { ox, oy, sx, sy ->
                view.translationX = natural.x + ox * density
                view.translationY = natural.y + oy * density
                view.scaleX = sx; view.scaleY = sy
            }
            // Persist once on dismiss
            onCommit = { ox, oy, sx, sy ->
                val config = viewModel.gamepadConfig.value
                val macros = config.macroButtons.toMutableList()
                if (index < macros.size) {
                    macros[index] = macros[index].copy(
                        layoutOffsetX = ox, layoutOffsetY = oy,
                        layoutScaleX = sx, layoutScaleY = sy,
                    )
                    viewModel.updateGamepadConfig(config.copy(macroButtons = macros))
                }
            }
        }.show(fragmentManager, "buttonLayout")
    }

    // ── Edit touch listeners ──────────────────────────────────────────────────

    @Suppress("ClickableViewAccessibility")
    private fun editTouchListener(
        view: View,
        getConfig: () -> ButtonConfig,
        saveConfig: (ButtonConfig) -> Unit,
    ): View.OnTouchListener {
        val tapThresholdPx = TAP_THRESHOLD_DP * resources.displayMetrics.density
        var lastSpanX = 1f; var lastSpanY = 1f
        val scaleDetector = ScaleGestureDetector(view.context,
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
                override fun onScaleEnd(d: ScaleGestureDetector) {
                    val density = resources.displayMetrics.density
                    saveConfig(getConfig().copy(
                        scaleX  = view.scaleX, scaleY  = view.scaleY,
                        offsetX = view.translationX / density, offsetY = view.translationY / density,
                    ))
                }
            })
        var downRawX = 0f; var downRawY = 0f
        var lastRawX = 0f; var lastRawY = 0f
        var wasDragged = false
        return View.OnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (scaleDetector.isInProgress) return@OnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX; downRawY = event.rawY
                    lastRawX = event.rawX; lastRawY = event.rawY
                    wasDragged = false
                }
                MotionEvent.ACTION_MOVE -> {
                    view.translationX += event.rawX - lastRawX
                    view.translationY += event.rawY - lastRawY
                    clampTranslationToParent(view)
                    lastRawX = event.rawX; lastRawY = event.rawY
                    if (abs(event.rawX - downRawX) > tapThresholdPx ||
                        abs(event.rawY - downRawY) > tapThresholdPx) wasDragged = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!wasDragged) {
                        showLayoutSheetForButton(view, getConfig, saveConfig)
                    } else {
                        val density = resources.displayMetrics.density
                        saveConfig(getConfig().copy(
                            offsetX = view.translationX / density, offsetY = view.translationY / density,
                            scaleX  = view.scaleX, scaleY  = view.scaleY,
                        ))
                    }
                }
            }
            true
        }
    }

    @Suppress("ClickableViewAccessibility")
    private fun editJoyListener(
        view: View,
        label: String,
        getConfig: () -> JoystickConfig,
        saveConfig: (JoystickConfig) -> Unit,
    ): View.OnTouchListener {
        val tapThresholdPx = TAP_THRESHOLD_DP * resources.displayMetrics.density
        var lastSpanX = 1f; var lastSpanY = 1f
        val scaleDetector = ScaleGestureDetector(view.context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                    lastSpanX = d.currentSpanX.coerceAtLeast(1f)
                    lastSpanY = d.currentSpanY.coerceAtLeast(1f)
                    return true
                }
                override fun onScale(d: ScaleGestureDetector): Boolean {
                    val fx = d.currentSpanX.coerceAtLeast(1f) / lastSpanX
                    val fy = d.currentSpanY.coerceAtLeast(1f) / lastSpanY
                    view.scaleX = (view.scaleX * fx).coerceAtLeast(0.4f)
                    view.scaleY = (view.scaleY * fy).coerceAtLeast(0.4f)
                    lastSpanX = d.currentSpanX.coerceAtLeast(1f)
                    lastSpanY = d.currentSpanY.coerceAtLeast(1f)
                    return true
                }
                override fun onScaleEnd(d: ScaleGestureDetector) {
                    val density = resources.displayMetrics.density
                    saveConfig(getConfig().copy(
                        scaleX  = view.scaleX, scaleY  = view.scaleY,
                        offsetX = view.translationX / density, offsetY = view.translationY / density,
                    ))
                }
            })
        var downRawX = 0f; var downRawY = 0f
        var lastRawX = 0f; var lastRawY = 0f
        var wasDragged = false
        return View.OnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (scaleDetector.isInProgress) return@OnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX; downRawY = event.rawY
                    lastRawX = event.rawX; lastRawY = event.rawY
                    wasDragged = false
                }
                MotionEvent.ACTION_MOVE -> {
                    view.translationX += event.rawX - lastRawX
                    view.translationY += event.rawY - lastRawY
                    clampTranslationToParent(view)
                    lastRawX = event.rawX; lastRawY = event.rawY
                    if (abs(event.rawX - downRawX) > tapThresholdPx ||
                        abs(event.rawY - downRawY) > tapThresholdPx) wasDragged = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!wasDragged) {
                        showLayoutSheetForJoystick(view, label, getConfig, saveConfig)
                    } else {
                        val density = resources.displayMetrics.density
                        saveConfig(getConfig().copy(
                            offsetX = view.translationX / density, offsetY = view.translationY / density,
                            scaleX  = view.scaleX, scaleY  = view.scaleY,
                        ))
                    }
                }
            }
            true
        }
    }

    private fun attachMacroEditListeners() {
        macroButtonViews.forEachIndexed { index, (button, _) ->
            button.alpha = 0.6f
            button.setOnTouchListener(macroEditTouchListener(button, index))
        }
    }

    @Suppress("ClickableViewAccessibility")
    private fun macroEditTouchListener(view: View, index: Int): View.OnTouchListener {
        val tapThresholdPx = TAP_THRESHOLD_DP * resources.displayMetrics.density
        var lastSpanX = 1f; var lastSpanY = 1f
        val scaleDetector = ScaleGestureDetector(view.context,
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
        var wasDragged = false
        return View.OnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (scaleDetector.isInProgress) return@OnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX; downRawY = event.rawY
                    lastRawX = event.rawX; lastRawY = event.rawY
                    wasDragged = false
                }
                MotionEvent.ACTION_MOVE -> {
                    view.translationX += event.rawX - lastRawX
                    view.translationY += event.rawY - lastRawY
                    clampTranslationToParent(view)
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

    /** Clamps view.translationX/Y so the scaled view stays entirely inside the root layout. */
    private fun clampTranslationToParent(view: View) {
        val pW = binding.root.width.toFloat()
        val pH = binding.root.height.toFloat()
        if (pW <= 0f || pH <= 0f) return
        val halfW = view.width  * view.scaleX / 2f
        val halfH = view.height * view.scaleY / 2f
        val naturalCX = (view.left + view.right)  / 2f
        val naturalCY = (view.top  + view.bottom) / 2f
        view.translationX = view.translationX.coerceIn(halfW - naturalCX, pW - naturalCX - halfW)
        view.translationY = view.translationY.coerceIn(halfH - naturalCY, pH - naturalCY - halfH)
    }

    @Suppress("ClickableViewAccessibility")
    private fun MaterialButton.setMacroNormalListener(macro: KeyboardMacroButtonConfig) {
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { viewModel.sendKeyboardReport(macro.modifiers, macro.keyUsages); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { viewModel.sendKeyboardReport(); true }
                else -> false
            }
        }
    }
}
