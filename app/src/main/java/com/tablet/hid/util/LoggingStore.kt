package com.tablet.hid.util

import android.content.Context
import java.io.File

object LoggingStore {
    private const val PREFS = "tablet_hid_logging"
    private const val KEY   = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, enabled).apply()
    }

    fun sessionDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "sessions")
    }

    fun sessionDirDisplayPath(context: Context): String =
        if (context.getExternalFilesDir(null) != null)
            "Android/data/com.tablet.hid/files/sessions"
        else
            "files/sessions (internal storage)"
}
