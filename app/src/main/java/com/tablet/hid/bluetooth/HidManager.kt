package com.tablet.hid.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.tablet.hid.model.DeviceMode
import com.tablet.hid.util.HidPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class HidManager(private val context: Context) {

    companion object {
        private const val TAG = "HidManager"
        private const val DEVICE_NAME = "TabletHID"
    }

    sealed class State {
        object Idle : State()
        object Registering : State()
        object WaitingForConnection : State()
        /** Actively trying to reconnect to a previously bonded device. */
        data class Reconnecting(val deviceName: String) : State()
        data class Connected(val device: BluetoothDevice, val mode: DeviceMode) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private var activeMode: DeviceMode? = null

    // Set when the caller wants to auto-connect to an already-bonded device after registration.
    private var reconnectTarget: BluetoothDevice? = null

    // Saved before we rename the adapter; restored on connect or disconnect.
    private var originalAdapterName: String? = null

    // -------------------------------------------------------------------------
    // Initialise / register
    // -------------------------------------------------------------------------

    /**
     * Register the HID profile for [mode].
     * If [reconnectTarget] is provided the manager will call [BluetoothHidDevice.connect] on it
     * immediately after the app is registered, skipping the manual discoverable flow.
     * If the mode has changed since the last bond, the old bond is removed first.
     */
    fun initialize(mode: DeviceMode, reconnectTarget: BluetoothDevice? = null) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = State.Error("Bluetooth is not available or disabled.")
            return
        }

        activeMode = mode
        this.reconnectTarget = reconnectTarget
        _state.value = if (reconnectTarget != null)
            State.Reconnecting(reconnectTarget.name ?: reconnectTarget.address)
        else
            State.Registering

        // Rename the adapter to "TabletHID" so the host sees a recognisable name
        // during the pairing dialog rather than the tablet's own Bluetooth name.
        // Only needed for fresh pair; reconnect skips discovery entirely.
        if (reconnectTarget == null) {
            originalAdapterName = adapter.name
            try { adapter.setName(DEVICE_NAME) } catch (e: Exception) {
                Log.w(TAG, "setName failed: ${e.message}")
            }
        }

        adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            hidDevice = proxy as BluetoothHidDevice
            registerApp()
        }
        override fun onServiceDisconnected(profile: Int) {
            hidDevice = null
            connectedDevice = null
            _state.value = State.Idle
        }
    }

    private fun registerApp() {
        // Always register the combined descriptor so the host maintains a single bond
        // for both mouse (Report ID 1) and gamepad (Report ID 2).
        val sdp = BluetoothHidDeviceAppSdpSettings(
            DEVICE_NAME, "Tablet HID Peripheral", "TabletHID",
            BluetoothHidDevice.SUBCLASS1_MOUSE,
            HidReportDescriptors.COMBINED_REPORT_DESCRIPTOR
        )
        val ok = hidDevice?.registerApp(sdp, null, null, Executors.newSingleThreadExecutor(), hidCallback)
        if (ok != true) {
            Log.e(TAG, "registerApp returned false")
            _state.value = State.Error("Failed to register HID app.")
        }
    }

    // -------------------------------------------------------------------------
    // HID callback
    // -------------------------------------------------------------------------

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged registered=$registered device=$pluggedDevice")
            if (registered) {
                val target = reconnectTarget
                if (target != null) {
                    // Try to initiate connection from our side to the bonded host.
                    val ok = hidDevice?.connect(target)
                    Log.d(TAG, "connect(${target.address}) returned $ok")
                } else {
                    _state.value = State.WaitingForConnection
                }
            } else {
                reconnectTarget = null
                _state.value = State.Idle
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "onConnectionStateChanged state=$state device=${device.address}")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    reconnectTarget = null
                    restoreAdapterName()
                    HidPrefs.saveLastDevice(context, device.address)
                    _state.value = State.Connected(device, activeMode!!)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    // Stay in Reconnecting if we were trying to reconnect and it failed.
                    if (_state.value !is State.Reconnecting) {
                        _state.value = State.WaitingForConnection
                    }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val size = when (id) {
                HidReportDescriptors.REPORT_ID_MOUSE   -> 6
                HidReportDescriptors.REPORT_ID_GAMEPAD -> 13
                else -> 1
            }
            hidDevice?.replyReport(device, type, id, ByteArray(size))
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            Log.d(TAG, "onSetProtocol protocol=$protocol")
        }
    }

    // -------------------------------------------------------------------------
    // Send reports
    // -------------------------------------------------------------------------

    fun sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int = 0) {
        val device = connectedDevice ?: return
        hidDevice?.sendReport(
            device,
            HidReportDescriptors.REPORT_ID_MOUSE.toInt(),
            HidReportDescriptors.buildMouseReport(buttons, dx, dy, wheel)
        )
    }

    fun sendGamepadReport(
        leftX: Int = 0, leftY: Int = 0,
        rightX: Int = 0, rightY: Int = 0,
        leftTrigger: Int = 0, rightTrigger: Int = 0,
        buttons: Int = 0,
        hat: Int = HidReportDescriptors.HAT_NONE
    ) {
        val device = connectedDevice ?: return
        hidDevice?.sendReport(
            device,
            HidReportDescriptors.REPORT_ID_GAMEPAD.toInt(),
            HidReportDescriptors.buildGamepadReport(
                leftX, leftY, rightX, rightY,
                leftTrigger, rightTrigger, buttons, hat
            )
        )
    }

    // -------------------------------------------------------------------------
    // Disconnect / cleanup
    // -------------------------------------------------------------------------

    /**
     * Disconnect from the current device and unregister the HID app,
     * but keep the Bluetooth bond intact so [initialize] with the same mode
     * can reconnect without re-pairing.
     */
    fun disconnect() {
        restoreAdapterName()
        connectedDevice?.let { device ->
            try { hidDevice?.disconnect(device) } catch (e: Exception) { Log.w(TAG, e) }
        }
        connectedDevice = null
        reconnectTarget = null
        try { hidDevice?.unregisterApp() } catch (e: Exception) { Log.w(TAG, e) }
        _state.value = State.Idle
    }

    /**
     * Disconnect AND remove the Bluetooth bond.
     * Call this when the user explicitly switches modes or closes the app,
     * so the host is clean for a fresh pair with a potentially different profile.
     */
    fun disconnectAndUnbond() {
        connectedDevice?.let { removeBond(it) }
        disconnect()
        HidPrefs.clearLastDevice(context)
        activeMode = null
    }

    /** Full teardown: disconnect + unbond + close the profile proxy. */
    fun cleanup() {
        disconnectAndUnbond()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        hidDevice = null
    }

    private fun restoreAdapterName() {
        val name = originalAdapterName ?: return
        originalAdapterName = null
        try {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.setName(name)
        } catch (e: Exception) {
            Log.w(TAG, "restoreAdapterName failed: ${e.message}")
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun removeBond(device: BluetoothDevice) {
        try {
            device.javaClass.getMethod("removeBond").invoke(device)
        } catch (e: Exception) {
            Log.w(TAG, "removeBond failed: ${e.message}")
        }
    }
}
