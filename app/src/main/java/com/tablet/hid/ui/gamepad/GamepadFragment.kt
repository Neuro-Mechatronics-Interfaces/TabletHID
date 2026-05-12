package com.tablet.hid.ui.gamepad

import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import com.tablet.hid.bluetooth.BleHidManager
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_A
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_B
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_BACK
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_LB
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_RB
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_START
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_X
import com.tablet.hid.bluetooth.HidReportDescriptors.BTN_Y
import com.tablet.hid.databinding.FragmentGamepadBinding
import com.tablet.hid.model.GamepadConfig
import com.tablet.hid.model.JoystickSide
import com.tablet.hid.model.OrientationPreference
import com.tablet.hid.util.HidPrefs
import com.tablet.hid.util.OrientationStore
import kotlinx.coroutines.launch

class GamepadFragment : Fragment() {

    private var _binding: FragmentGamepadBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HidViewModel by activityViewModels()

    private lateinit var stateManager: GamepadStateManager
    private lateinit var editController: GamepadEditController
    private lateinit var inputController: GamepadInputController

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
            if (editController.editMode) { editController.exitEditMode(); return }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.exit_mode_title)
                .setMessage(R.string.exit_mode_message)
                .setPositiveButton(R.string.exit_mode_confirm) { _, _ ->
                    isEnabled = false
                    exitImmersiveMode()
                    requireActivity().requestedOrientation =
                        OrientationStore.toActivityOrientation(OrientationStore.get(requireContext()))
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

        applyConfigOrientation(viewModel.gamepadConfig.value)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressCallback)

        stateManager = GamepadStateManager(
            viewModel = viewModel,
            lifecycleOwner = viewLifecycleOwner,
            config = { viewModel.gamepadConfig.value },
            buttonViews = {
                mapOf(
                    BTN_A     to binding.btnA,
                    BTN_B     to binding.btnB,
                    BTN_X     to binding.btnX,
                    BTN_Y     to binding.btnY,
                    BTN_LB    to binding.btnLb,
                    BTN_RB    to binding.btnRb,
                    BTN_BACK  to binding.btnBack,
                    BTN_START to binding.btnStart,
                )
            },
        )

        editController = GamepadEditController(
            binding = binding,
            viewModel = viewModel,
            resources = resources,
            fragmentManager = childFragmentManager,
            onEditStateChanged = { _ ->
                inputController.setupButtons()
                inputController.setupDpad()
                applyConfig(viewModel.gamepadConfig.value)
            },
        )

        inputController = GamepadInputController(
            binding = binding,
            viewModel = viewModel,
            resources = resources,
            state = stateManager,
        )

        binding.btnOrientationLock.setOnClickListener { cycleOrientationLock() }
        updateOrientationIcon()

        inputController.setupJoysticks()
        inputController.setupButtons()
        inputController.setupDpad()

        binding.btnSettings.setOnClickListener { showConfigSheet() }
        binding.btnEditDone.setOnClickListener { editController.exitEditMode() }
        binding.btnSingleJoystickSide.setOnClickListener { inputController.toggleSingleJoystickOutputSide() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val ledColor = requireContext().getColor(when (state) {
                        is BleHidManager.State.Connected -> R.color.led_connected
                        is BleHidManager.State.Idle,
                        is BleHidManager.State.Error     -> R.color.led_disconnected
                        else                             -> R.color.led_connecting
                    })
                    binding.ledStatus.backgroundTintList = ColorStateList.valueOf(ledColor)
                    binding.textConnStatus.text = when (state) {
                        is BleHidManager.State.Connected          -> getString(R.string.status_connected)
                        is BleHidManager.State.Registering        -> getString(R.string.home_status_connecting)
                        is BleHidManager.State.WaitingForConnection -> getString(R.string.home_status_waiting)
                        is BleHidManager.State.Reconnecting       -> getString(R.string.tutorial_status_reconnecting, state.deviceName)
                        is BleHidManager.State.PendingApproval   -> getString(R.string.home_status_pending_approval)
                        is BleHidManager.State.Error              -> getString(R.string.status_disconnected)
                        is BleHidManager.State.Idle               -> getString(R.string.status_disconnected)
                    }
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

    private fun applyConfigOrientation(cfg: GamepadConfig) {
        requireActivity().requestedOrientation = when (cfg.orientationPreference) {
            OrientationPreference.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationPreference.PORTRAIT  -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationPreference.SYSTEM    -> OrientationStore.toActivityOrientation(OrientationStore.get(requireContext()))
        }
    }

    private fun applyConfig(cfg: GamepadConfig) {
        applyConfigOrientation(cfg)
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

        fun label(key: String, default: String) =
            cfg.customButtonLabels[key]?.takeIf { it.isNotBlank() } ?: default
        binding.btnA.text     = label("a",     "A")
        binding.btnB.text     = label("b",     "B")
        binding.btnX.text     = label("x",     "X")
        binding.btnY.text     = label("y",     "Y")
        binding.btnLb.text    = label("lb",    "LB")
        binding.btnRb.text    = label("rb",    "RB")
        binding.btnLt.text    = label("lt",    "LT")
        binding.btnRt.text    = label("rt",    "RT")
        binding.btnBack.text  = label("back",  "Back")
        binding.btnStart.text = label("start", "Start")
        binding.dpadUp.text    = label("dup",   "▲")
        binding.dpadDown.text  = label("ddown", "▼")
        binding.dpadLeft.text  = label("dleft", "◀")
        binding.dpadRight.text = label("dright","▶")

        binding.leftJoystick.applyLayout(cfg.leftJoystick.offsetX, cfg.leftJoystick.offsetY, cfg.leftJoystick.scaleX, cfg.leftJoystick.scaleY)
        binding.leftJoystick.applyEnabled(cfg.leftJoystick.enabled)
        binding.rightJoystick.applyLayout(cfg.rightJoystick.offsetX, cfg.rightJoystick.offsetY, cfg.rightJoystick.scaleX, cfg.rightJoystick.scaleY)
        binding.rightJoystick.applyEnabled(!cfg.singleJoystickMode && cfg.rightJoystick.enabled)
        binding.btnSingleJoystickSide.visibility =
            if (cfg.singleJoystickMode && cfg.singleJoystickSideToggleEnabled && cfg.leftJoystick.enabled) View.VISIBLE else View.GONE
        binding.btnSingleJoystickSide.text =
            if (cfg.singleJoystickOutputSide == JoystickSide.LEFT) "L" else "R"
        binding.btnSingleJoystickSide.applyLayout(
            cfg.singleJoystickSideBtn.offsetX, cfg.singleJoystickSideBtn.offsetY,
            cfg.singleJoystickSideBtn.scaleX,  cfg.singleJoystickSideBtn.scaleY,
        )

        // Tint the joystick ring to match the current output side when the toggle is active.
        val joystickAccent = requireContext().getColor(
            if (cfg.singleJoystickMode && cfg.singleJoystickSideToggleEnabled) {
                if (cfg.singleJoystickOutputSide == JoystickSide.LEFT)
                    R.color.joystick_accent_left
                else
                    R.color.joystick_accent_right
            } else {
                R.color.overlay_white_40
            }
        )
        binding.leftJoystick.accentColor = joystickAccent

        editController.renderMacroButtons(cfg)

        binding.leftJoystick.deadzone  = cfg.leftJoystick.deadzone
        binding.leftJoystick.gain      = cfg.leftJoystick.gain
        binding.rightJoystick.deadzone = cfg.rightJoystick.deadzone
        binding.rightJoystick.gain     = cfg.rightJoystick.gain

        if (!cfg.leftJoystick.enabled && (stateManager.leftX != 0 || stateManager.leftY != 0)) {
            stateManager.leftX = 0; stateManager.leftY = 0; stateManager.sendReport()
        }
        if ((cfg.singleJoystickMode || !cfg.rightJoystick.enabled) && (stateManager.rightX != 0 || stateManager.rightY != 0)) {
            stateManager.rightX = 0; stateManager.rightY = 0; stateManager.sendReport()
        }
    }

    // ── Config sheet ─────────────────────────────────────────────────────────

    private fun showConfigSheet() {
        GamepadConfigSheet().apply {
            onEditLayoutRequested = { editController.enterEditMode() }
        }.show(childFragmentManager, "gpConfig")
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

    override fun onDestroyView() {
        super.onDestroyView()
        stateManager.reset()
        requireActivity().requestedOrientation =
            OrientationStore.toActivityOrientation(OrientationStore.get(requireContext()))
        _binding = null
    }
}
