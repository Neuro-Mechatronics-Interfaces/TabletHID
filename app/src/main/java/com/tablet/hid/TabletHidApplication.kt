package com.tablet.hid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.tablet.hid.bluetooth.BleHidManager
import com.tablet.hid.util.AppearanceStore

class TabletHidApplication : Application() {

    // Process-scoped singleton — shared by HidViewModel and HidForegroundService so only
    // one GATT server is ever open at a time.
    val hidManager: BleHidManager by lazy { BleHidManager(this) }

    override fun onCreate() {
        super.onCreate()
        AppearanceStore.apply(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            HidForegroundService.CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
