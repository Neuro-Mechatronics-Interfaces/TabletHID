package com.tablet.hid.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tablet.hid.model.DeviceMode
import com.tablet.hid.model.HidHost
import com.tablet.hid.util.HidHostStore
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

    // Registered during fresh-pair to initiate the HID connection from our side the
    // moment the bond is established, rather than waiting for the host to open channels
    // (which competing system services can intercept on some devices).
    private var bondReceiver: BroadcastReceiver? = null

    // Tracks the last device we called connect() on, so we can retry once after a
    // CONNECTING→DISCONNECTED cycle (Windows needs ~8s to finish SDP + HID driver install).
    private var lastAttemptedDevice: BluetoothDevice? = null
    private var retryCount = 0
    private val retryHandler = Handler(Looper.getMainLooper())

    // -------------------------------------------------------------------------
    // Initialise / register
    // -------------------------------------------------------------------------

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
        initialize(mode, reconnectTarget = device)
    }

    /**
     * Disconnect from [host] if currently connected, remove its Bluetooth bond,
     * and clear it from prefs.
     */
    @SuppressLint("MissingPermission")
    fun forgetDevice(host: HidHost) {
        if (connectedDevice?.address == host.address) {
            try { hidDevice?.disconnect(connectedDevice!!) } catch (_: Exception) {}
            connectedDevice = null
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        try {
            adapter?.bondedDevices?.firstOrNull { it.address == host.address }?.let { removeBond(it) }
        } catch (_: SecurityException) {}
        if (HidPrefs.getLastDeviceAddress(context) == host.address) {
            HidPrefs.clearLastDevice(context)
        }
        if (_state.value !is State.Connected) _state.value = State.Idle
    }

    /**
     * Register the HID profile for [mode].
     * If [reconnectTarget] is provided the manager will call [BluetoothHidDevice.connect] on it
     * immediately after the app is registered, skipping the manual discoverable flow.
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

        if (reconnectTarget == null) {
            // Rename the adapter so the host sees a recognisable name during pairing.
            originalAdapterName = adapter.name
            try { adapter.setName(DEVICE_NAME) } catch (e: Exception) {
                Log.w(TAG, "setName failed: ${e.message}")
            }
            // Watch for the bond completing so we can initiate the HID connection
            // from our side immediately — avoids races with competing system services.
            registerBondReceiver()
        }

        adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            val hid = proxy as BluetoothHidDevice
            hidDevice = hid
            Log.d(TAG, "onServiceConnected: proxy=$proxy class=${proxy.javaClass.simpleName} " +
                    "connectedDevices=${hid.connectedDevices}")
            registerApp()
        }
        override fun onServiceDisconnected(profile: Int) {
            Log.d(TAG, "onServiceDisconnected: profile=$profile — proxy lost, clearing hidDevice")
            hidDevice = null
            connectedDevice = null
            _state.value = State.Idle
        }
    }

    private fun registerApp() {
        // Always register the combined descriptor so the host maintains a single bond
        // for both mouse (Report ID 1) and gamepad (Report ID 2).
        // Subclass 0x00 (uncategorized) avoids Windows loading the mouse minidriver
        // for a descriptor that also contains a gamepad collection — which causes
        // "Driver Error" when the mouse minidriver rejects the combined descriptor.
        val sdp = BluetoothHidDeviceAppSdpSettings(
            DEVICE_NAME, "TabletHID Mouse+Gamepad", "NML",
            0x00.toByte(),
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
                    connectWithSetName(target)
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
                    lastAttemptedDevice = null
                    retryCount = 0
                    retryHandler.removeCallbacksAndMessages(null)
                    unregisterBondReceiver()
                    // Do NOT restore adapter name here — reverting the name while connected
                    // causes mobiledesktop.core to reclaim the HID slot within ~2 seconds.
                    // Name is restored in disconnect() instead.
                    HidPrefs.saveLastDevice(context, device.address)
                    HidHostStore.upsert(context, HidHost(
                        address    = device.address,
                        btName     = device.name ?: "",
                        lastSeenMs = System.currentTimeMillis()
                    ))
                    _state.value = State.Connected(device, activeMode!!)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    val retryDevice = lastAttemptedDevice
                    if (retryDevice != null && retryCount < 1) {
                        // CONNECTING→DISCONNECTED: Windows is still installing the HID driver.
                        // Keep the current state and retry once after giving Windows time to finish.
                        lastAttemptedDevice = null
                        retryCount++
                        val currentState = _state.value
                        retryHandler.postDelayed({
                            if (_state.value == currentState) {
                                Log.d(TAG, "Retrying HID connect after Windows driver install delay (attempt $retryCount)")
                                connectWithSetName(retryDevice)
                            }
                        }, 8000L)
                    } else {
                        lastAttemptedDevice = null
                        retryCount = 0
                        if (_state.value !is State.Reconnecting) {
                            _state.value = State.WaitingForConnection
                        }
                    }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val size = when (id) {
                HidReportDescriptors.REPORT_ID_MOUSE   -> 7
                HidReportDescriptors.REPORT_ID_GAMEPAD -> 13
                else -> 1
            }
            Log.d(TAG, "onGetReport type=$type id=$id bufferSize=$bufferSize → replying $size bytes")
            hidDevice?.replyReport(device, type, id, ByteArray(size))
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            Log.d(TAG, "onSetProtocol protocol=$protocol (0=boot, 1=report)")
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            Log.w(TAG, "onVirtualCableUnplug from ${device.address} — host is unpairing us")
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
        retryHandler.removeCallbacksAndMessages(null)
        lastAttemptedDevice = null
        retryCount = 0
        unregisterBondReceiver()
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

    /** Full teardown: disconnect and close the profile proxy, keeping the bond intact
     *  so the reconnect column appears on next launch. */
    fun cleanup() {
        disconnect()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        hidDevice = null
    }

    // Calls setName() immediately before connect() to suppress mobiledesktop.core
    // interference at connection time (not just at registration time).
    private fun connectWithSetName(device: BluetoothDevice) {
        lastAttemptedDevice = device
        try {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.setName(DEVICE_NAME)
        } catch (e: Exception) {
            Log.w(TAG, "setName (pre-connect): ${e.message}")
        }
        val ok = hidDevice?.connect(device)
        Log.d(TAG, "connect(${device.address}) returned $ok")
    }

    private fun registerBondReceiver() {
        unregisterBondReceiver()
        bondReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                val bondState = intent.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                if (bondState != BluetoothDevice.BOND_BONDED) return
                val device: BluetoothDevice = intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE) ?: return
                // Connect immediately while mobiledesktop.core is still freshly suppressed
                // by the setName() call made earlier in initialize(). Delaying gives
                // mobiledesktop.core time to wake back up and block the L2CAP setup.
                Log.d(TAG, "Bond established with ${device.address}, initiating HID connect")
                connectWithSetName(device)
            }
        }
        context.registerReceiver(
            bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    }

    private fun unregisterBondReceiver() {
        bondReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: IllegalArgumentException) {}
            bondReceiver = null
        }
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