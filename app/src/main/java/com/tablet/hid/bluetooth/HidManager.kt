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
import com.tablet.hid.model.HidHost
import com.tablet.hid.util.AppearanceStore
import com.tablet.hid.util.HidHostStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class HidManager(private val context: Context) {

    companion object {
        private const val TAG = "HidManager"
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
    private var activeDeviceName: String = AppearanceStore.DEFAULT_DEVICE_NAME

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
    /**
     * Resolve [host] to a live bonded [BluetoothDevice] and call [initialize] with it.
     * If the host is no longer bonded the state moves to Error.
     */
    @SuppressLint("MissingPermission")
    fun reconnect(mode: DeviceMode, host: HidHost) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: run {
            _state.value = State.Error("Bluetooth unavailable")
            return
        }
        val device = try {
            adapter.bondedDevices?.firstOrNull { it.address == host.address }
        } catch (_: SecurityException) { null }

        if (device == null) {
            _state.value = State.Error("${host.displayName} is no longer bonded — remove and re-pair.")
            return
        }
        initialize(mode, reconnectTarget = device, hostDisplayName = host.displayName)
    }

    /**
     * Disconnect from [host] if currently connected, remove its Bluetooth bond,
     * and remove it from the known-hosts list.
     */
    @SuppressLint("MissingPermission")
    fun forgetDevice(host: HidHost) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val device = try {
            adapter?.bondedDevices?.firstOrNull { it.address == host.address }
        } catch (_: SecurityException) { null }

        if (connectedDevice?.address == host.address) {
            try { hidDevice?.disconnect(connectedDevice!!) } catch (_: Exception) {}
            connectedDevice = null
        }
        device?.let { removeBond(it) }
        HidHostStore.remove(context, host.address)
        if (_state.value !is State.Connected) _state.value = State.Idle
    }

    fun initialize(mode: DeviceMode, reconnectTarget: BluetoothDevice? = null, hostDisplayName: String? = null) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = State.Error("Bluetooth is not available or disabled.")
            return
        }

        activeMode = mode
        activeDeviceName = AppearanceStore.getDeviceName(context)
        this.reconnectTarget = reconnectTarget
        _state.value = if (reconnectTarget != null)
            State.Reconnecting(hostDisplayName ?: reconnectTarget.name ?: reconnectTarget.address)
        else
            State.Registering

        // Rename the adapter so the host sees the configured TabletHID name
        // during the pairing dialog rather than the tablet's own Bluetooth name.
        // Only needed for fresh pair; reconnect skips discovery entirely.
        if (reconnectTarget == null) {
            originalAdapterName = adapter.name
            try { adapter.setName(activeDeviceName) } catch (e: Exception) {
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
            activeDeviceName, "Tablet HID Peripheral", activeDeviceName,
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
                    HidHostStore.upsert(context, HidHost(
                        address    = device.address,
                        btName     = device.name ?: "",
                        lastSeenMs = System.currentTimeMillis()
                    ))
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
