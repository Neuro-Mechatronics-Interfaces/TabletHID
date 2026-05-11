package com.tablet.hid.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.tablet.hid.HidForegroundService
import com.tablet.hid.MainActivity
import com.tablet.hid.R
import com.tablet.hid.util.HidPrefs

class HidWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_DISCONNECT = "com.tablet.hid.widget.ACTION_DISCONNECT"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WIDGET_DISCONNECT) {
            context.startService(
                Intent(context, HidForegroundService::class.java).apply {
                    action = HidForegroundService.ACTION_WIDGET_DISCONNECT
                }
            )
        }
        super.onReceive(context, intent)
    }

    private fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        val connected = HidWidgetState.isConnected(context)
        val deviceName = HidWidgetState.getDeviceName(context)
        val lastAddr = HidPrefs.getLastDeviceAddress(context)

        val views = RemoteViews(context.packageName, R.layout.widget_hid)

        val statusText = if (connected && deviceName != null) {
            "Connected · $deviceName"
        } else {
            "Disconnected"
        }
        views.setTextViewText(R.id.widgetStatus, statusText)

        when {
            connected -> {
                views.setTextViewText(R.id.widgetBtn, context.getString(R.string.btn_disconnect))
                views.setViewVisibility(R.id.widgetBtn, View.VISIBLE)
                val pi = PendingIntent.getBroadcast(
                    context, 0,
                    Intent(ACTION_WIDGET_DISCONNECT).setClass(context, HidWidgetProvider::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widgetBtn, pi)
            }
            lastAddr != null -> {
                views.setTextViewText(R.id.widgetBtn, context.getString(R.string.widget_reconnect))
                views.setViewVisibility(R.id.widgetBtn, View.VISIBLE)
                val pi = PendingIntent.getActivity(
                    context, 1,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("widget_reconnect", true)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widgetBtn, pi)
            }
            else -> {
                views.setViewVisibility(R.id.widgetBtn, View.INVISIBLE)
            }
        }

        // Tapping the widget title/status opens the app
        val openPi = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, openPi)

        mgr.updateAppWidget(widgetId, views)
    }
}
