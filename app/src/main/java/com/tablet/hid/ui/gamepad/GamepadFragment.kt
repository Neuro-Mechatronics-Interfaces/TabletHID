package com.tablet.hid.ui.gamepad

import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.tablet.hid.bluetooth.HidReportDescriptors
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_A
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_B
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_BACK
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_LB
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_RB
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_START
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_X
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_Y
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_E
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_N
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_NE
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_NW
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_NONE
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_S
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_SE
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_SW
import com.tablet.hid.bluetooth.HidReportDescriptors.HAT_W
import com.tablet.hid.databinding.FragmentGamepadBinding
import com.tablet.hid.model.ButtonConfig
import com.tablet.hid.model.ClickBehavior
import com.tablet.hid.model.GamepadConfig
import com.tablet.hid.model.TriggerDragAxis
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GamepadFragment : Fragment() {

    private var _binding: FragmentGamepadBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HidViewModel by activityViewModels()

    // ── Gamepad axis/hat state ───────────────────────────────────────────────
    private var leftX = 0; private var leftY = 0
    private var rightX = 0; private var rightY = 0
    private var leftTrigger = 0; private var rightTrigger = 0
    private var hat = HAT_NONE

    private var dUp = false; private var dDown = false
    private var dLeft = false; private var dRight = false

    // ── Button state (latching + momentary) ──────────────────────────────────
    private val latchedBits = mutableSetOf<Int>()
    private val momentaryBits = mutableSetOf<Int>()

    // ── Turbo ────────────────────────────────────────────────────────────────
    private val turboJobs = mutableMapOf<Int, Job>()

    // ── Edit-mode ────────────────────────────────────────────────────────────
    private var editMode = false

    // ── Visual: base colors per button ──────────────────────────────────────
    private val baseColor = mapOf(
        BTN_A to 0xFF22C55E.toInt(), BTN_B to 0xFFEF4444.toInt(),
        BTN_X to 0xFF3B82F6.toInt(), BTN_Y to 0xFFF5C518.toInt(),
    )
    private val activeColor = mapOf(
        BTN_A to 0xFF86EFAC.toInt(), BTN_B to 0xFFFCA5A5.toInt(),
        BTN_X to 0xFF93C5FD.toInt(), BTN_Y to 0xFFFFE082.toInt(),
    )

    // ── Immersive mode ───────────────────────────────────────────────────────

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
            if (editMode) { exitEditMode(); return }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.exit_mode_title)
                .setMessage(R.string.exit_mode_message)
                .setPositiveButton(R.string.exit_mode_confirm) { _, _ ->
                    isEnabled = false
                    exitImmersiveMode()
                    requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    viewModel.disconnect()
                    findNavController().navigate(R.id.action_gamepad_to_home)
                }
                .setNegativeButton(R.string.exit_mode_stay, null)
                .show()
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGamepadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressCallback)

        setupJoysticks()
        setupButtons()
        setupDpad()

        binding.btnSettings.setOnClickListener { showConfigSheet() }
        binding.btnEditDone.setOnClickListener { exitEditMode() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val connected = state is HidManager.State.Connected
                    binding.ledStatus.backgroundTintList = ColorStateList.valueOf(
                        if (connected) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
                    )
                    binding.textConnStatus.text =
                        if (connected) "Connected" else "Disconnected"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.gamepadConfig.collect { applyConfig(it) }
            }
        }
    }

    // ── Config application ───────────────────────────────────────────────────

    private fun applyConfig(cfg: GamepadConfig) {
        val density = resources.displayMetrics.density
        fun View.applyLayout(ox: Float, oy: Float, sx: Float, sy: Float) {
            translationX = ox * density; translationY = oy * density
            scaleX = sx; scaleY = sy
        }
        fun View.applyEnabled(enabled: Boolean) {
            visibility = if (enabled) View.VISIBLE else View.GONE
        }
        binding.btnA.applyLayout(cfg.btnA.offsetX, cfg.btnA.offsetY, cfg.btnA.scaleX, cfg.btnA.scaleY);             binding.btnA.applyEnabled(cfg.btnA.enabled)
        binding.btnB.applyLayout(cfg.btnB.offsetX, cfg.btnB.offsetY, cfg.btnB.scaleX, cfg.btnB.scaleY);             binding.btnB.applyEnabled(cfg.btnB.enabled)
        binding.btnX.applyLayout(cfg.btnX.offsetX, cfg.btnX.offsetY, cfg.btnX.scaleX, cfg.btnX.scaleY);             binding.btnX.applyEnabled(cfg.btnX.enabled)
        binding.btnY.applyLayout(cfg.btnY.offsetX, cfg.btnY.offsetY, cfg.btnY.scaleX, cfg.btnY.scaleY);             binding.btnY.applyEnabled(cfg.btnY.enabled)
        binding.btnLb.applyLayout(cfg.btnLb.offsetX, cfg.btnLb.offsetY, cfg.btnLb.scaleX, cfg.btnLb.scaleY);       binding.btnLb.applyEnabled(cfg.btnLb.enabled)
        binding.btnRb.applyLayout(cfg.btnRb.offsetX, cfg.btnRb.offsetY, cfg.btnRb.scaleX, cfg.btnRb.scaleY);       binding.btnRb.applyEnabled(cfg.btnRb.enabled)
        binding.btnLt.applyLayout(cfg.btnLt.offsetX, cfg.btnLt.offsetY, cfg.btnLt.scaleX, cfg.btnLt.scaleY);       binding.btnLt.applyEnabled(cfg.btnLt.enabled)
        binding.btnRt.applyLayout(cfg.btnRt.offsetX, cfg.btnRt.offsetY, cfg.btnRt.scaleX, cfg.btnRt.scaleY);       binding.btnRt.applyEnabled(cfg.btnRt.enabled)
        binding.btnBack.applyLayout(cfg.btnBack.offsetX, cfg.btnBack.offsetY, cfg.btnBack.scaleX, cfg.btnBack.scaleY);   binding.btnBack.applyEnabled(cfg.btnBack.enabled)
        binding.btnStart.applyLayout(cfg.btnStart.offsetX, cfg.btnStart.offsetY, cfg.btnStart.scaleX, cfg.btnStart.scaleY); binding.btnStart.applyEnabled(cfg.btnStart.enabled)
        binding.dpadUp.applyLayout(cfg.dpadUp.offsetX, cfg.dpadUp.offsetY, cfg.dpadUp.scaleX, cfg.dpadUp.scaleY);         binding.dpadUp.applyEnabled(cfg.dpadUp.enabled)
        binding.dpadDown.applyLayout(cfg.dpadDown.offsetX, cfg.dpadDown.offsetY, cfg.dpadDown.scaleX, cfg.dpadDown.scaleY); binding.dpadDown.applyEnabled(cfg.dpadDown.enabled)
        binding.dpadLeft.applyLayout(cfg.dpadLeft.offsetX, cfg.dpadLeft.offsetY, cfg.dpadLeft.scaleX, cfg.dpadLeft.scaleY); binding.dpadLeft.applyEnabled(cfg.dpadLeft.enabled)
        binding.dpadRight.applyLayout(cfg.dpadRight.offsetX, cfg.dpadRight.offsetY, cfg.dpadRight.scaleX, cfg.dpadRight.scaleY); binding.dpadRight.applyEnabled(cfg.dpadRight.enabled)

        binding.leftJoystick.applyLayout(cfg.leftJoystick.offsetX, cfg.leftJoystick.offsetY, cfg.leftJoystick.scaleX, cfg.leftJoystick.scaleY)
        binding.leftJoystick.applyEnabled(cfg.leftJoystick.enabled)
        binding.rightJoystick.applyLayout(cfg.rightJoystick.offsetX, cfg.rightJoystick.offsetY, cfg.rightJoystick.scaleX, cfg.rightJoystick.scaleY)
        binding.rightJoystick.applyEnabled(cfg.rightJoystick.enabled)

        binding.leftJoystick.deadzone  = cfg.leftJoystick.deadzone
        binding.leftJoystick.gain      = cfg.leftJoystick.gain
        binding.rightJoystick.deadzone = cfg.rightJoystick.deadzone
        binding.rightJoystick.gain     = cfg.rightJoystick.gain

        if (!cfg.leftJoystick.enabled && (leftX != 0 || leftY != 0)) { leftX = 0; leftY = 0; sendReport() }
        if (!cfg.rightJoystick.enabled && (rightX != 0 || rightY != 0)) { rightX = 0; rightY = 0; sendReport() }
    }

    // ── Config sheet ─────────────────────────────────────────────────────────

    private fun showConfigSheet() {
        GamepadConfigSheet().apply {
            onEditLayoutRequested = { enterEditMode() }
        }.show(childFragmentManager, "gpConfig")
    }

    // ── Edit mode (drag to reposition, pinch to resize independently H/V) ────

    private fun enterEditMode() {
        editMode = true
        binding.editModeBanner.visibility = View.VISIBLE

        fun attach(v: View, getB: (GamepadConfig) -> ButtonConfig,
                   setB: (GamepadConfig, ButtonConfig) -> GamepadConfig) =
            v.setOnTouchListener(editTouchListener(v,
                getConfig = { getB(viewModel.gamepadConfig.value) },
                saveConfig = { bc -> viewModel.updateGamepadConfig(setB(viewModel.gamepadConfig.value, bc)) }
            ))

        fun attachJoy(v: View, getJ: (GamepadConfig) -> com.tablet.hid.model.JoystickConfig,
                      setJ: (GamepadConfig, com.tablet.hid.model.JoystickConfig) -> GamepadConfig) =
            v.setOnTouchListener(editJoyListener(v,
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
        attachJoy(binding.leftJoystick,  { it.leftJoystick },  { c, j -> c.copy(leftJoystick = j) })
        attachJoy(binding.rightJoystick, { it.rightJoystick }, { c, j -> c.copy(rightJoystick = j) })

        allButtonViews().forEach { it.alpha = 0.6f }
    }

    private fun exitEditMode() {
        editMode = false
        binding.editModeBanner.visibility = View.GONE
        setupButtons()
        setupDpad()
        binding.leftJoystick.setOnTouchListener(null)
        binding.rightJoystick.setOnTouchListener(null)
        allButtonViews().forEach { it.alpha = 1f }
        applyConfig(viewModel.gamepadConfig.value)
    }

    @Suppress("ClickableViewAccessibility")
    private fun editTouchListener(
        view: View,
        getConfig: () -> ButtonConfig,
        saveConfig: (ButtonConfig) -> Unit,
    ): View.OnTouchListener {
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
                    view.scaleX = (view.scaleX * fx).coerceIn(0.3f, 4.0f)
                    view.scaleY = (view.scaleY * fy).coerceIn(0.3f, 4.0f)
                    lastSpanX = d.currentSpanX.coerceAtLeast(1f)
                    lastSpanY = d.currentSpanY.coerceAtLeast(1f)
                    return true
                }
                override fun onScaleEnd(d: ScaleGestureDetector) {
                    val density = resources.displayMetrics.density
                    saveConfig(getConfig().copy(
                        scaleX  = view.scaleX,
                        scaleY  = view.scaleY,
                        offsetX = view.translationX / density,
                        offsetY = view.translationY / density,
                    ))
                }
            })
        var lastRawX = 0f; var lastRawY = 0f
        return View.OnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (scaleDetector.isInProgress) return@OnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastRawX = event.rawX; lastRawY = event.rawY }
                MotionEvent.ACTION_MOVE -> {
                    view.translationX += event.rawX - lastRawX
                    view.translationY += event.rawY - lastRawY
                    lastRawX = event.rawX; lastRawY = event.rawY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val density = resources.displayMetrics.density
                    saveConfig(getConfig().copy(
                        offsetX = view.translationX / density,
                        offsetY = view.translationY / density,
                        scaleX  = view.scaleX,
                        scaleY  = view.scaleY,
                    ))
                }
            }
            true
        }
    }

    @Suppress("ClickableViewAccessibility")
    private fun editJoyListener(
        view: View,
        getConfig: () -> com.tablet.hid.model.JoystickConfig,
        saveConfig: (com.tablet.hid.model.JoystickConfig) -> Unit,
    ): View.OnTouchListener {
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
                    view.scaleX = (view.scaleX * fx).coerceIn(0.4f, 3.0f)
                    view.scaleY = (view.scaleY * fy).coerceIn(0.4f, 3.0f)
                    lastSpanX = d.currentSpanX.coerceAtLeast(1f)
                    lastSpanY = d.currentSpanY.coerceAtLeast(1f)
                    return true
                }
                override fun onScaleEnd(d: ScaleGestureDetector) {
                    val density = resources.displayMetrics.density
                    saveConfig(getConfig().copy(
                        scaleX  = view.scaleX,
                        scaleY  = view.scaleY,
                        offsetX = view.translationX / density,
                        offsetY = view.translationY / density,
                    ))
                }
            })
        var lastRawX = 0f; var lastRawY = 0f
        return View.OnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (scaleDetector.isInProgress) return@OnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastRawX = event.rawX; lastRawY = event.rawY }
                MotionEvent.ACTION_MOVE -> {
                    view.translationX += event.rawX - lastRawX
                    view.translationY += event.rawY - lastRawY
                    lastRawX = event.rawX; lastRawY = event.rawY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val density = resources.displayMetrics.density
                    saveConfig(getConfig().copy(
                        offsetX = view.translationX / density,
                        offsetY = view.translationY / density,
                        scaleX  = view.scaleX,
                        scaleY  = view.scaleY,
                    ))
                }
            }
            true
        }
    }

    private fun allButtonViews(): List<View> = listOf(
        binding.btnA, binding.btnB, binding.btnX, binding.btnY,
        binding.btnLb, binding.btnRb, binding.btnLt, binding.btnRt,
        binding.btnBack, binding.btnStart,
        binding.dpadUp, binding.dpadDown, binding.dpadLeft, binding.dpadRight,
        binding.leftJoystick, binding.rightJoystick,
    )

    // ── Joysticks ─────────────────────────────────────────────────────────────

    private fun setupJoysticks() {
        binding.leftJoystick.listener = JoystickView.JoystickListener { nx, ny ->
            leftX = (nx * 32767).toInt(); leftY = (ny * 32767).toInt(); sendReport()
        }
        binding.rightJoystick.listener = JoystickView.JoystickListener { nx, ny ->
            rightX = (nx * 32767).toInt(); rightY = (ny * 32767).toInt(); sendReport()
        }
    }

    // ── Face / shoulder / trigger buttons ────────────────────────────────────

    private fun setupButtons() {
        val cfg = viewModel.gamepadConfig.value
        configuredButton(binding.btnA,    BTN_A,    cfg.btnA,    binding.btnA)
        configuredButton(binding.btnB,    BTN_B,    cfg.btnB,    binding.btnB)
        configuredButton(binding.btnX,    BTN_X,    cfg.btnX,    binding.btnX)
        configuredButton(binding.btnY,    BTN_Y,    cfg.btnY,    binding.btnY)
        configuredButton(binding.btnLb,   BTN_LB,   cfg.btnLb,   binding.btnLb)
        configuredButton(binding.btnRb,   BTN_RB,   cfg.btnRb,   binding.btnRb)
        configuredButton(binding.btnBack, BTN_BACK, cfg.btnBack, binding.btnBack)
        configuredButton(binding.btnStart,BTN_START,cfg.btnStart,binding.btnStart)

        triggerButton(binding.btnLt, cfg.btnLt, isLeft = true)
        triggerButton(binding.btnRt, cfg.btnRt, isLeft = false)
    }

    @Suppress("ClickableViewAccessibility")
    private fun configuredButton(v: View, bit: Int, cfg: ButtonConfig, indicator: View) {
        v.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    when (cfg.behavior) {
                        ClickBehavior.MOMENTARY -> {
                            if (cfg.turbo) startTurbo(bit, cfg)
                            else { momentaryBits.add(bit); setVisualActive(indicator, bit, true) }
                        }
                        ClickBehavior.LATCHING -> {
                            if (latchedBits.contains(bit)) {
                                latchedBits.remove(bit)
                                turboJobs.remove(bit)?.cancel()
                                setVisualActive(indicator, bit, false)
                            } else {
                                latchedBits.add(bit)
                                if (cfg.turbo) startTurbo(bit, cfg)
                                else setVisualActive(indicator, bit, true)
                            }
                        }
                    }
                    sendReport(); true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (cfg.behavior == ClickBehavior.MOMENTARY) {
                        turboJobs.remove(bit)?.cancel()
                        momentaryBits.remove(bit)
                        setVisualActive(indicator, bit, false)
                        sendReport()
                    }
                    true
                }
                else -> false
            }
        }
    }

    @Suppress("ClickableViewAccessibility")
    private fun triggerButton(v: View, cfg: ButtonConfig, isLeft: Boolean) {
        var startX = 0f; var startY = 0f
        v.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY
                    if (cfg.behavior == ClickBehavior.LATCHING) {
                        val on = if (isLeft) leftTrigger == 0 else rightTrigger == 0
                        val trigVal = if (on) 255 else 0
                        if (isLeft) leftTrigger = trigVal else rightTrigger = trigVal
                        setTriggerLevel(v, trigVal / 255f)
                    } else {
                        if (isLeft) leftTrigger = 255 else rightTrigger = 255
                        setTriggerLevel(v, 1f)
                    }
                    sendReport(); true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (cfg.behavior == ClickBehavior.MOMENTARY) {
                        val travelPx = cfg.triggerTravelDp * resources.displayMetrics.density
                        val delta = when (cfg.triggerAxis) {
                            TriggerDragAxis.UP    -> startY - event.rawY
                            TriggerDragAxis.DOWN  -> event.rawY - startY
                            TriggerDragAxis.LEFT  -> startX - event.rawX
                            TriggerDragAxis.RIGHT -> event.rawX - startX
                        }
                        val ratio = (delta.coerceAtLeast(0f) / travelPx).coerceIn(0f, 1f)
                        val trigVal = (255 * (1f - ratio)).toInt()
                        if (isLeft) leftTrigger = trigVal else rightTrigger = trigVal
                        setTriggerLevel(v, trigVal / 255f)
                        sendReport()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (cfg.behavior == ClickBehavior.MOMENTARY) {
                        if (isLeft) leftTrigger = 0 else rightTrigger = 0
                        setTriggerLevel(v, 0f)
                        sendReport()
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ── D-pad ─────────────────────────────────────────────────────────────────

    private fun setupDpad() {
        dpadButton(binding.dpadUp,    { d -> dUp    = d; updateHat() })
        dpadButton(binding.dpadDown,  { d -> dDown  = d; updateHat() })
        dpadButton(binding.dpadLeft,  { d -> dLeft  = d; updateHat() })
        dpadButton(binding.dpadRight, { d -> dRight = d; updateHat() })
    }

    @Suppress("ClickableViewAccessibility")
    private fun dpadButton(v: View, onChanged: (Boolean) -> Unit) {
        v.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    setTriggerLevel(v, 1f); onChanged(true); true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    setTriggerLevel(v, 0f); onChanged(false); true
                }
                else -> false
            }
        }
    }

    private fun updateHat() {
        hat = when {
            dUp && dRight  -> HAT_NE; dDown && dRight -> HAT_SE
            dDown && dLeft -> HAT_SW; dUp && dLeft    -> HAT_NW
            dUp            -> HAT_N;  dRight          -> HAT_E
            dDown          -> HAT_S;  dLeft           -> HAT_W
            else           -> HAT_NONE
        }
        sendReport()
    }

    // ── Turbo ─────────────────────────────────────────────────────────────────

    private fun startTurbo(bit: Int, cfg: ButtonConfig) {
        turboJobs[bit]?.cancel()
        turboJobs[bit] = viewLifecycleOwner.lifecycleScope.launch {
            val indicator = buttonViewForBit(bit) ?: return@launch
            while (true) {
                momentaryBits.add(bit)
                setVisualActive(indicator, bit, true)
                sendReport()
                delay(cfg.turboDurationMs.toLong())
                momentaryBits.remove(bit)
                setVisualActive(indicator, bit, false)
                sendReport()
                delay(cfg.turboIntervalMs.toLong())
            }
        }
    }

    private fun buttonViewForBit(bit: Int): View? = when (bit) {
        BTN_A     -> binding.btnA;    BTN_B    -> binding.btnB
        BTN_X     -> binding.btnX;    BTN_Y    -> binding.btnY
        BTN_LB    -> binding.btnLb;   BTN_RB   -> binding.btnRb
        BTN_BACK  -> binding.btnBack; BTN_START -> binding.btnStart
        else -> null
    }

    // ── Visual feedback ──────────────────────────────────────────────────────

    private fun setVisualActive(v: View, bit: Int, active: Boolean) {
        val color = if (active) activeColor[bit] ?: Color.argb(0xFF, 255, 255, 255)
                    else        baseColor[bit]   ?: Color.argb(0x33, 255, 255, 255)
        v.backgroundTintList = ColorStateList.valueOf(color)
    }

    /** Interpolates the button tint alpha from dim (0x33) at level 0 to bright (0xFF) at level 1. */
    private fun setTriggerLevel(v: View, level: Float) {
        val alpha = (0x33 + ((0xFF - 0x33) * level)).toInt().coerceIn(0x33, 0xFF)
        v.backgroundTintList = ColorStateList.valueOf(Color.argb(alpha, 255, 255, 255))
    }

    // ── Report dispatch ──────────────────────────────────────────────────────

    private fun sendReport() {
        var bits = 0
        momentaryBits.forEach { bits = bits or (1 shl it) }
        latchedBits.forEach   { bits = bits or (1 shl it) }
        viewModel.sendGamepadReport(
            leftX = leftX, leftY = leftY,
            rightX = rightX, rightY = rightY,
            leftTrigger = leftTrigger, rightTrigger = rightTrigger,
            buttons = bits, hat = hat
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        turboJobs.values.forEach { it.cancel() }
        turboJobs.clear()
        if (requireActivity().requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        _binding = null
    }
}
