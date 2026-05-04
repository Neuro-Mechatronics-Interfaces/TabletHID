package com.tablet.hid

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import android.bluetooth.BluetoothDevice
import com.tablet.hid.bluetooth.HidManager
import com.tablet.hid.bluetooth.HidReportDescriptors
import com.tablet.hid.model.DeviceMode
import com.tablet.hid.model.GamepadConfig
import com.tablet.hid.model.Profile
import com.tablet.hid.model.TouchMouseConfig
import com.tablet.hid.util.GamepadConfigStore
import com.tablet.hid.util.ProfileStore
import com.tablet.hid.util.TouchMouseConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HidViewModel(app: Application) : AndroidViewModel(app) {

    val hidManager = HidManager(app)
    val state: StateFlow<HidManager.State> = hidManager.state

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

    fun reconnect(mode: DeviceMode, device: BluetoothDevice) =
        hidManager.initialize(mode, reconnectTarget = device)

    fun disconnectAndUnbond() = hidManager.disconnectAndUnbond()

    fun sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int = 0) =
        hidManager.sendMouseReport(buttons, dx, dy, wheel)

    fun sendGamepadReport(
        leftX: Int = 0, leftY: Int = 0,
        rightX: Int = 0, rightY: Int = 0,
        leftTrigger: Int = 0, rightTrigger: Int = 0,
        buttons: Int = 0,
        hat: Int = HidReportDescriptors.HAT_NONE,
    ) = hidManager.sendGamepadReport(leftX, leftY, rightX, rightY, leftTrigger, rightTrigger, buttons, hat)

    fun disconnect() = hidManager.disconnect()

    override fun onCleared() {
        hidManager.cleanup()
    }
}
