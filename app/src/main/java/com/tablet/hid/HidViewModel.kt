package com.tablet.hid

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tablet.hid.bluetooth.HidManager
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HidViewModel(app: Application) : AndroidViewModel(app) {

    val hidManager = HidManager(app)
    val state: StateFlow<HidManager.State> = hidManager.state

    // ── Session logging ──────────────────────────────────────────────────────────

    private val _loggingEnabled = MutableStateFlow(LoggingStore.isEnabled(app))
    val loggingEnabled: StateFlow<Boolean> = _loggingEnabled.asStateFlow()

    private var sessionLogger: SessionLogger? = null

    init {
        viewModelScope.launch {
            state.collect { s ->
                if (s is HidManager.State.Connected && _loggingEnabled.value) {
                    startSession(s.mode)
                } else if (s !is HidManager.State.Connected) {
                    endSession()
                }
            }
        }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        LoggingStore.setEnabled(getApplication(), enabled)
        _loggingEnabled.value = enabled
        if (enabled) {
            val s = state.value
            if (s is HidManager.State.Connected) startSession(s.mode)
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
        // Refresh list so the UI sees any btName updates after connecting.
        refreshHosts()
    }

    fun disconnectAndUnbond() = hidManager.disconnectAndUnbond()

    fun sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int = 0) {
        sessionLogger?.logMouse(buttons, dx, dy, wheel)
        hidManager.sendMouseReport(buttons, dx, dy, wheel)
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

    fun disconnect() = hidManager.disconnect()

    override fun onCleared() {
        endSession()
        hidManager.cleanup()
    }
}
