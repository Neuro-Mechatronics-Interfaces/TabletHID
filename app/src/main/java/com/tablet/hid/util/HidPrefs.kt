package com.tablet.hid.util

import android.content.Context

object HidPrefs {
    private const val PREFS = "hid_prefs"
    private const val KEY_DEVICE = "last_device"
    private const val KEY_AUTO_RECONNECT = "auto_reconnect"
    private const val KEY_SCREEN_PINNING = "screen_pinning"

    fun saveLastDevice(context: Context, address: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEVICE, address).apply()
    }

    fun getLastDeviceAddress(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DEVICE, null)

    fun clearLastDevice(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_DEVICE).apply()
    }

    fun isAutoReconnectEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_RECONNECT, false)

    fun setAutoReconnectEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_RECONNECT, value).apply()
    }

    fun isScreenPinningEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SCREEN_PINNING, false)

    fun setScreenPinningEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SCREEN_PINNING, value).apply()
    }
}
