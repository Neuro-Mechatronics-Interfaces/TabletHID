package com.tablet.hid.util

import android.content.Context

object HidPrefs {
    private const val PREFS = "hid_prefs"
    private const val KEY_DEVICE = "last_device"

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
}
