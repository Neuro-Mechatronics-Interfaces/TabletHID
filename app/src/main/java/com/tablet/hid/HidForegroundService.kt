package com.tablet.hid

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tablet.hid.bluetooth.BleHidManager
import com.tablet.hid.model.DeviceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HidForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "hid_connection"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_ADDRESS = "extra_address"
        const val ACTION_DISCONNECT = "com.tablet.hid.ACTION_DISCONNECT"
        const val ACTION_WIDGET_DISCONNECT = "com.tablet.hid.ACTION_WIDGET_DISCONNECT"
        private const val ACTION_STOP = "com.tablet.hid.ACTION_STOP_SERVICE"

        @Volatile var isRunning = false

        fun startIntent(context: Context, mode: DeviceMode, reconnectAddress: String? = null): Intent =
            Intent(context, HidForegroundService::class.java).apply {
                putExtra(EXTRA_MODE, mode.name)
                reconnectAddress?.let { putExtra(EXTRA_ADDRESS, it) }
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val hidManager get() = (applicationContext as TabletHidApplication).hidManager

    private val disconnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_DISCONNECT -> hidManager.disconnect()
                ACTION_STOP -> { hidManager.disconnect(); stopSelf() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val filter = IntentFilter(ACTION_DISCONNECT).apply { addAction(ACTION_STOP) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(disconnectReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(disconnectReceiver, filter)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_WIDGET_DISCONNECT) {
            hidManager.disconnect()
            return START_NOT_STICKY
        }
        val modeName = intent?.getStringExtra(EXTRA_MODE) ?: DeviceMode.TOUCH_MOUSE.name
        val mode = runCatching { DeviceMode.valueOf(modeName) }.getOrDefault(DeviceMode.TOUCH_MOUSE)
        val address = intent?.getStringExtra(EXTRA_ADDRESS)

        val reconnectTarget: BluetoothDevice? = address?.let { addr ->
            runCatching {
                applicationContext.getSystemService(BluetoothManager::class.java)
                    ?.adapter?.getRemoteDevice(addr)
            }.getOrNull()
        }

        startForeground(NOTIFICATION_ID, buildNotification(BleHidManager.State.Registering))
        hidManager.initialize(mode, reconnectTarget)

        scope.launch {
            hidManager.state.collect { state ->
                updateNotification(state)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        // Don't cleanup the shared hidManager here — HidViewModel.onCleared() handles
        // disconnection when the app exits. Explicit disconnect comes via ACTION_STOP.
        unregisterReceiver(disconnectReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(state: BleHidManager.State) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: BleHidManager.State): Notification {
        val contentText = when (state) {
            is BleHidManager.State.Idle -> "Idle"
            is BleHidManager.State.Registering -> "Starting…"
            is BleHidManager.State.WaitingForConnection -> "Waiting for connection"
            is BleHidManager.State.Reconnecting -> "Reconnecting to ${state.deviceName}"
            is BleHidManager.State.Connected ->
                "Connected to ${state.deviceName}"
            is BleHidManager.State.Error -> "Error: ${state.message}"
        }

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setContentIntent(openIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        when (state) {
            is BleHidManager.State.Connected -> {
                val disconnectIntent = PendingIntent.getBroadcast(
                    this, 0,
                    Intent(ACTION_DISCONNECT),
                    PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.notif_action_disconnect),
                    disconnectIntent
                )
            }
            is BleHidManager.State.Idle, is BleHidManager.State.Error -> {
                val stopIntent = PendingIntent.getBroadcast(
                    this, 1,
                    Intent(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.notif_action_stop),
                    stopIntent
                )
            }
            else -> Unit
        }

        return builder.build()
    }
}
