package com.tablet.hid.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.tablet.hid.model.DeviceMode
import com.tablet.hid.model.HidHost
import com.tablet.hid.util.AppearanceStore
import com.tablet.hid.util.HidHostStore
import com.tablet.hid.util.HidPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

@SuppressLint("MissingPermission")
class BleHidManager(private val context: Context) {

    companion object {
        private const val TAG = "BleHidManager"
        private val UUID_DIS         = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
        private val UUID_HID         = UUID.fromString("00001812-0000-1000-8000-00805F9B34FB")
        private val UUID_MANUF_NAME  = UUID.fromString("00002A29-0000-1000-8000-00805F9B34FB")
        private val UUID_PNP_ID      = UUID.fromString("00002A50-0000-1000-8000-00805F9B34FB")
        private val UUID_PROTO_MODE  = UUID.fromString("00002A4E-0000-1000-8000-00805F9B34FB")
        private val UUID_REPORT_MAP  = UUID.fromString("00002A4B-0000-1000-8000-00805F9B34FB")
        private val UUID_HID_INFO    = UUID.fromString("00002A4A-0000-1000-8000-00805F9B34FB")
        private val UUID_HID_CTRL    = UUID.fromString("00002A4C-0000-1000-8000-00805F9B34FB")
        private val UUID_REPORT      = UUID.fromString("00002A4D-0000-1000-8000-00805F9B34FB")
        private val UUID_CCCD        = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        private val UUID_REPORT_REF  = UUID.fromString("00002908-0000-1000-8000-00805F9B34FB")
    }

    sealed class State {
        object Idle : State()
        object Registering : State()
        object WaitingForConnection : State()
        data class Reconnecting(val deviceName: String) : State()
        data class Connected(val device: BluetoothDevice, val mode: DeviceMode) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var gattServer: BluetoothGattServer? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var activeMode: DeviceMode? = null
    private var connectedDevice: BluetoothDevice? = null

    // Built fresh each time startGattServer() is called.
    private var mouseReportChar: BluetoothGattCharacteristic? = null
    private var gamepadReportChar: BluetoothGattCharacteristic? = null

    // Sequential service-add queue — Android only allows one addService() in flight.
    private val serviceQueue = ArrayDeque<BluetoothGattService>()

    // -------------------------------------------------------------------------
    // Public API (mirrors HidManager interface)
    // -------------------------------------------------------------------------

    fun initialize(mode: DeviceMode, reconnectTarget: BluetoothDevice? = null) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = State.Error("Bluetooth is not available or disabled.")
            return
        }
        activeMode = mode
        _state.value = if (reconnectTarget != null)
            State.Reconnecting(reconnectTarget.name ?: reconnectTarget.address)
        else
            State.Registering

        try { adapter.setName(AppearanceStore.getDeviceName(context)) } catch (e: Exception) {
            Log.w(TAG, "setName: ${e.message}")
        }
        startGattServer()
    }

    fun reconnect(mode: DeviceMode, host: HidHost) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = State.Error("Bluetooth is not available or disabled.")
            return
        }
        activeMode = mode
        _state.value = State.Reconnecting(host.displayName)
        try { adapter.setName(AppearanceStore.getDeviceName(context)) } catch (_: Exception) {}
        startGattServer()
    }

    fun forgetDevice(host: HidHost) {
        if (connectedDevice?.address == host.address) {
            try { gattServer?.cancelConnection(connectedDevice!!) } catch (_: Exception) {}
            connectedDevice = null
        }
        HidHostStore.remove(context, host.address)
        if (HidPrefs.getLastDeviceAddress(context) == host.address) {
            HidPrefs.clearLastDevice(context)
        }
        if (_state.value !is State.Connected) _state.value = State.Idle
    }

    fun disconnect() {
        stopAdvertising()
        connectedDevice?.let {
            try { gattServer?.cancelConnection(it) } catch (_: Exception) {}
        }
        connectedDevice = null
        closeGattServer()
        _state.value = State.Idle
    }

    fun disconnectAndUnbond() {
        disconnect()
        HidPrefs.clearLastDevice(context)
        activeMode = null
    }

    fun cleanup() = disconnect()

    // -------------------------------------------------------------------------
    // Report sending
    // -------------------------------------------------------------------------

    fun sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int = 0, hwheel: Int = 0) {
        val device = connectedDevice ?: return
        val char = mouseReportChar ?: return
        val report = HidReportDescriptors.buildMouseReport(buttons, dx, dy, wheel, hwheel)
        notify(device, char, report)
    }

    fun sendGamepadReport(
        leftX: Int = 0, leftY: Int = 0,
        rightX: Int = 0, rightY: Int = 0,
        leftTrigger: Int = 0, rightTrigger: Int = 0,
        buttons: Int = 0,
        hat: Int = HidReportDescriptors.HAT_NONE
    ) {
        val device = connectedDevice ?: return
        val char = gamepadReportChar ?: return
        val report = HidReportDescriptors.buildGamepadReport(
            leftX, leftY, rightX, rightY, leftTrigger, rightTrigger, buttons, hat)
        notify(device, char, report)
    }

    // -------------------------------------------------------------------------
    // GATT server setup
    // -------------------------------------------------------------------------

    private fun startGattServer() {
        closeGattServer()

        val btManager = context.getSystemService(BluetoothManager::class.java)
        gattServer = btManager?.openGattServer(context, gattServerCallback) ?: run {
            _state.value = State.Error("Failed to open GATT server.")
            return
        }

        mouseReportChar = buildReportChar().also { char ->
            char.addDescriptor(BluetoothGattDescriptor(
                UUID_REPORT_REF, BluetoothGattDescriptor.PERMISSION_READ
            ).also { it.value = byteArrayOf(HidReportDescriptors.REPORT_ID_MOUSE, 0x01) })
        }
        gamepadReportChar = buildReportChar().also { char ->
            char.addDescriptor(BluetoothGattDescriptor(
                UUID_REPORT_REF, BluetoothGattDescriptor.PERMISSION_READ
            ).also { it.value = byteArrayOf(HidReportDescriptors.REPORT_ID_GAMEPAD, 0x01) })
        }

        serviceQueue.clear()
        serviceQueue.add(buildDisService())
        serviceQueue.add(buildHidService())
        drainServiceQueue()
    }

    private fun drainServiceQueue() {
        val next = serviceQueue.removeFirstOrNull() ?: run {
            startAdvertising()
            return
        }
        try {
            gattServer?.addService(next)
        } catch (e: Exception) {
            Log.e(TAG, "addService failed: ${e.message}")
            _state.value = State.Error("Failed to set up BLE HID service.")
        }
    }

    private fun buildDisService(): BluetoothGattService {
        val svc = BluetoothGattService(UUID_DIS, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        svc.addCharacteristic(BluetoothGattCharacteristic(
            UUID_MANUF_NAME,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).also { it.value = "NML".toByteArray() })
        svc.addCharacteristic(BluetoothGattCharacteristic(
            UUID_PNP_ID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).also {
            // Source=USB (0x02), VID=0x045E (Microsoft), PID=0x02FD, Version=0x0110
            it.value = byteArrayOf(0x02, 0x5E, 0x04, 0xFD.toByte(), 0x02, 0x10, 0x01)
        })
        return svc
    }

    private fun buildHidService(): BluetoothGattService {
        val svc = BluetoothGattService(UUID_HID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        svc.addCharacteristic(BluetoothGattCharacteristic(
            UUID_PROTO_MODE,
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_READ or
                    BluetoothGattCharacteristic.PERMISSION_WRITE
        ).also { it.value = byteArrayOf(0x01) })

        svc.addCharacteristic(BluetoothGattCharacteristic(
            UUID_REPORT_MAP,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).also { it.value = HidReportDescriptors.COMBINED_REPORT_DESCRIPTOR })

        // HID 1.11, country=0, flags=normallyConnectable|wakeup
        svc.addCharacteristic(BluetoothGattCharacteristic(
            UUID_HID_INFO,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).also { it.value = byteArrayOf(0x11, 0x01, 0x00, 0x02) })

        svc.addCharacteristic(BluetoothGattCharacteristic(
            UUID_HID_CTRL,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        ))

        svc.addCharacteristic(mouseReportChar!!)
        svc.addCharacteristic(gamepadReportChar!!)
        return svc
    }

    private fun buildReportChar(): BluetoothGattCharacteristic {
        val char = BluetoothGattCharacteristic(
            UUID_REPORT,
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        )
        char.addDescriptor(BluetoothGattDescriptor(
            UUID_CCCD,
            BluetoothGattDescriptor.PERMISSION_READ or
                    BluetoothGattDescriptor.PERMISSION_WRITE
        ).also { it.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE })
        return char
    }

    // -------------------------------------------------------------------------
    // BLE advertising
    // -------------------------------------------------------------------------

    private fun startAdvertising() {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val advertiser = adapter?.bluetoothLeAdvertiser ?: run {
            _state.value = State.Error("BLE advertising not supported on this device.")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(UUID_HID))
            .build()

        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d(TAG, "BLE advertising started")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "BLE advertising failed errorCode=$errorCode")
                _state.value = State.Error("BLE advertising failed (error $errorCode).")
            }
        }
        advertiseCallback = cb
        advertiser.startAdvertising(settings, data, cb)

        // For reconnect flow, stay in Reconnecting until host connects.
        if (_state.value !is State.Reconnecting) {
            _state.value = State.WaitingForConnection
        }
    }

    private fun stopAdvertising() {
        val cb = advertiseCallback ?: return
        advertiseCallback = null
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        try { adapter?.bluetoothLeAdvertiser?.stopAdvertising(cb) } catch (_: Exception) {}
    }

    // -------------------------------------------------------------------------
    // GATT server callback
    // -------------------------------------------------------------------------

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onServiceAdded failed status=$status uuid=${service.uuid}")
                _state.value = State.Error("Failed to add GATT service (${service.uuid}).")
                return
            }
            Log.d(TAG, "onServiceAdded uuid=${service.uuid}")
            drainServiceQueue()
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange device=${device.address} status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    stopAdvertising()
                    HidPrefs.saveLastDevice(context, device.address)
                    HidHostStore.upsert(context, HidHost(
                        address    = device.address,
                        btName     = device.name ?: "",
                        lastSeenMs = System.currentTimeMillis()
                    ))
                    _state.value = State.Connected(device, activeMode!!)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedDevice?.address == device.address) {
                        connectedDevice = null
                    }
                    // Re-advertise unless disconnect() was called explicitly (state=Idle).
                    if (_state.value !is State.Idle) {
                        Log.d(TAG, "Disconnected — re-advertising for reconnection")
                        _state.value = State.WaitingForConnection
                        startAdvertising()
                    }
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value ?: ByteArray(0)
            val slice = if (offset < value.size) value.copyOfRange(offset, value.size) else ByteArray(0)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            val value = descriptor.value ?: ByteArray(0)
            val slice = if (offset < value.size) value.copyOfRange(offset, value.size) else ByteArray(0)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (descriptor.uuid == UUID_CCCD) {
                descriptor.value = value
                Log.d(TAG, "CCCD write: char=${descriptor.characteristic?.uuid} " +
                        "value=${value.contentToString()} device=${device.address}")
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            characteristic.value = value
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun notify(device: BluetoothDevice, char: BluetoothGattCharacteristic, report: ByteArray) {
        val server = gattServer ?: return
        if (Build.VERSION.SDK_INT >= 33) {
            server.notifyCharacteristicChanged(device, char, false, report)
        } else {
            char.value = report
            server.notifyCharacteristicChanged(device, char, false)
        }
    }

    private fun closeGattServer() {
        try { gattServer?.close() } catch (_: Exception) {}
        gattServer = null
        mouseReportChar = null
        gamepadReportChar = null
        serviceQueue.clear()
    }
}
