package com.tablet.hid

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tablet.hid.bluetooth.BleHidManager
import com.tablet.hid.bluetooth.HidReportDescriptors
import com.tablet.hid.model.DeviceMode
import com.tablet.hid.model.GamepadConfig
import com.tablet.hid.model.HidHost
import com.tablet.hid.model.Profile
import com.tablet.hid.model.TouchMouseConfig
import com.tablet.hid.util.GamepadConfigStore
import com.tablet.hid.util.HidHostStore
import com.tablet.hid.util.LoggingStore
import com.tablet.hid.util.ProfileStore
import com.tablet.hid.util.SessionLogger
import com.tablet.hid.util.TouchMouseConfigStore
import com.tablet.hid.widget.HidWidgetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HidViewModel(app: Application) : AndroidViewModel(app) {

    val hidManager: BleHidManager = (app as TabletHidApplication).hidManager
    val state: StateFlow<BleHidManager.State> = hidManager.state

    // Set by MainActivity when the app is launched via a home-screen shortcut.
    var pendingStartMode: String? = null

    // ── Session logging ──────────────────────────────────────────────────────────

    private val _loggingEnabled = MutableStateFlow(LoggingStore.isEnabled(app))


    private var sessionLogger: SessionLogger? = null

    // ── Mouse rate controller: 50 Hz fixed-rate with EMA smoothing ─────────────────
    // Touch events accumulate raw float displacement into mouseTargetDx/Dy.
    // A fixed 50 Hz coroutine applies a light EMA toward the target, then sends
    // one HID report per tick with the discretised delta. Sub-pixel fractions
    // carry over in mouseCarryX/Y so no movement is lost to truncation.
    //
    // Button state changes are sent immediately (separate path) so clicks are
    // never delayed by the 20 ms timer window.

    private val mouseLock        = Any()
    private var mouseButtonState = 0    // guarded by mouseLock
    private var mouseTargetDx    = 0f  // guarded by mouseLock
    private var mouseTargetDy    = 0f  // guarded by mouseLock

    // Owned exclusively by the timer coroutine — no locking needed:
    private var mouseEmaDx  = 0f
    private var mouseEmaDy  = 0f
    private var mouseCarryX = 0f
    private var mouseCarryY = 0f

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(20L) // 50 Hz
                val buttons: Int; val targetDx: Float; val targetDy: Float
                synchronized(mouseLock) {
                    buttons  = mouseButtonState
                    targetDx = mouseTargetDx
                    targetDy = mouseTargetDy
                }
                mouseEmaDx = 0.8f * targetDx + 0.2f * mouseEmaDx
                mouseEmaDy = 0.8f * targetDy + 0.2f * mouseEmaDy
                val rawX = mouseEmaDx + mouseCarryX
                val rawY = mouseEmaDy + mouseCarryY
                val sendDx = rawX.toInt()
                val sendDy = rawY.toInt()
                mouseCarryX = rawX - sendDx
                mouseCarryY = rawY - sendDy
                if (sendDx != 0 || sendDy != 0) {
                    synchronized(mouseLock) {
                        mouseTargetDx -= sendDx
                        mouseTargetDy -= sendDy
                    }
                    sessionLogger?.logMouse(buttons, sendDx, sendDy, 0)
                    hidManager.sendMouseReport(buttons, sendDx, sendDy)
                }
            }
        }
        viewModelScope.launch {
            state.collect { s ->
                if (s is BleHidManager.State.Connected && _loggingEnabled.value) {
                    startSession(s.mode)
                } else if (s !is BleHidManager.State.Connected) {
                    endSession()
                }
                val ctx: Context = getApplication()
                if (ActivityCompat.checkSelfPermission(
                        ctx,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@collect
                }
                when (s) {
                    is BleHidManager.State.Connected ->
                        HidWidgetState.update(ctx, true, s.deviceName)
                    else ->
                        HidWidgetState.update(ctx, false, null)
                }
            }
        }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        LoggingStore.setEnabled(getApplication(), enabled)
        _loggingEnabled.value = enabled
        if (enabled) {
            val s = state.value
            if (s is BleHidManager.State.Connected) startSession(s.mode)
        } else {
            endSession()
        }
    }

    private fun startSession(mode: DeviceMode) {
        endSession()
        sessionLogger = try {
            SessionLogger(
                context      = getApplication(),
                mode         = mode,
                profileName  = _activeProfile.value.name,
                touchConfig  = if (mode == DeviceMode.TOUCH_MOUSE) _touchMouseConfig.value else null,
                gamepadConfig = if (mode == DeviceMode.GAMEPAD) _gamepadConfig.value else null,
            )
        } catch (e: Exception) {
            Log.e("HidViewModel", "SessionLogger init failed", e)
            null
        }
    }

    private fun endSession() {
        sessionLogger?.close()
        sessionLogger = null
    }

    // ── Known hosts ──────────────────────────────────────────────────────────────

    private val _knownHosts = MutableStateFlow(HidHostStore.getAll(app))
    val knownHosts: StateFlow<List<HidHost>> = _knownHosts.asStateFlow()
    private fun refreshHosts() {
        _knownHosts.value = HidHostStore.getAll(getApplication())
    }

    fun renameHost(address: String, alias: String?) {
        HidHostStore.updateAlias(getApplication(), address, alias)
        refreshHosts()
    }

    fun forgetHost(host: HidHost) {
        hidManager.forgetDevice(host)
        refreshHosts()
    }

    fun approvePendingConnection() {
        hidManager.approvePendingConnection()
        refreshHosts()
    }

    fun rejectPendingConnection() = hidManager.rejectPendingConnection()

    // ── Profile state ────────────────────────────────────────────────────────────

    private val _customProfiles = MutableStateFlow(ProfileStore.getCustomProfiles(app))
    val customProfiles: StateFlow<List<Profile>> = _customProfiles.asStateFlow()

    private val _activeProfile = MutableStateFlow(ProfileStore.getActiveProfile(app))
    val activeProfile: StateFlow<Profile> = _activeProfile.asStateFlow()

    fun setProfile(profile: Profile) {
        ProfileStore.saveActiveKey(getApplication(), profile.key)
        _activeProfile.value = profile
        _touchMouseConfig.value = TouchMouseConfigStore.load(getApplication(), profile)
        _gamepadConfig.value    = GamepadConfigStore.load(getApplication(), profile)
    }

    fun addCustomProfile(name: String): Profile {
        val profile = ProfileStore.addCustomProfile(getApplication(), name)
        _customProfiles.value = ProfileStore.getCustomProfiles(getApplication())
        return profile
    }

    // ── Touch Mouse config ───────────────────────────────────────────────────────

    private val _touchMouseConfig =
        MutableStateFlow(TouchMouseConfigStore.load(app, _activeProfile.value))
    val touchMouseConfig: StateFlow<TouchMouseConfig> = _touchMouseConfig.asStateFlow()

    fun updateTouchMouseConfig(config: TouchMouseConfig) {
        _touchMouseConfig.value = config
        TouchMouseConfigStore.save(getApplication(), config, _activeProfile.value)
    }

    // ── Gamepad config ───────────────────────────────────────────────────────────

    private val _gamepadConfig =
        MutableStateFlow(GamepadConfigStore.load(app, _activeProfile.value))
    val gamepadConfig: StateFlow<GamepadConfig> = _gamepadConfig.asStateFlow()

    fun updateGamepadConfig(config: GamepadConfig) {
        _gamepadConfig.value = config
        GamepadConfigStore.save(getApplication(), config, _activeProfile.value)
    }

    // ── HID operations ───────────────────────────────────────────────────────────

    fun initialize(mode: DeviceMode) = hidManager.initialize(mode)

    fun reconnect(mode: DeviceMode, host: HidHost) {
        hidManager.reconnect(mode, host)
        refreshHosts()
    }

    fun sendMouseReport(buttons: Int, dx: Float = 0f, dy: Float = 0f, wheel: Int = 0, hwheel: Int = 0) {
        val buttonsChanged: Boolean
        synchronized(mouseLock) {
            buttonsChanged   = buttons != mouseButtonState
            mouseButtonState = buttons
            mouseTargetDx   += dx
            mouseTargetDy   += dy
        }
        // Button changes and scroll events bypass the timer for immediate delivery.
        if (buttonsChanged || wheel != 0 || hwheel != 0) {
            hidManager.sendMouseReport(buttons, 0, 0, wheel, hwheel)
        }
    }

    fun sendGamepadReport(
        leftX: Int = 0, leftY: Int = 0,
        rightX: Int = 0, rightY: Int = 0,
        leftTrigger: Int = 0, rightTrigger: Int = 0,
        buttons: Int = 0,
        hat: Int = HidReportDescriptors.HAT_NONE,
    ) {
        sessionLogger?.logGamepad(leftX, leftY, rightX, rightY, leftTrigger, rightTrigger, buttons, hat)
        hidManager.sendGamepadReport(leftX, leftY, rightX, rightY, leftTrigger, rightTrigger, buttons, hat)
    }

    fun sendKeyboardReport(modifiers: Int = 0, keyUsages: Iterable<Int> = emptyList()) {
        hidManager.sendKeyboardReport(modifiers, keyUsages)
    }

    fun disconnect() = hidManager.disconnect()

    // ── Foreground service helpers ────────────────────────────────────────────

    fun startServiceForMode(context: Context, mode: DeviceMode, reconnectAddress: String? = null) {
        val intent = HidForegroundService.startIntent(context, mode, reconnectAddress)
        ContextCompat.startForegroundService(context, intent)
    }

    override fun onCleared() {
        endSession()
        // Disconnect only when no background service is keeping the connection alive.
        if (!HidForegroundService.isRunning) hidManager.disconnect()
    }
}
