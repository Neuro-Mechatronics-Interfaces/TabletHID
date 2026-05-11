package com.tablet.hid.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object HidWidgetState {

    private const val PREFS = "hid_widget_state"

    fun update(context: Context, connected: Boolean, deviceName: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("connected", connected)
            .putString("device_name", deviceName)
            .apply()
        refreshWidgets(context)
    }

    fun isConnected(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("connected", false)

    fun getDeviceName(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("device_name", null)

    fun refreshWidgets(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(
            ComponentName(context, HidWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return
        context.sendBroadcast(
            Intent(context, HidWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
        )
    }
}
