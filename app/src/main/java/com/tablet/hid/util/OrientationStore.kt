package com.tablet.hid.util

import android.content.Context
import android.content.pm.ActivityInfo

object OrientationStore {
    private const val PREF = "orientation_pref"
    private const val KEY  = "orientation_lock"

    const val SYSTEM    = 0
    const val PORTRAIT  = 1
    const val LANDSCAPE = 2

    fun get(context: Context): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY, SYSTEM)

    fun set(context: Context, value: Int) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putInt(KEY, value).apply()

    fun toActivityOrientation(value: Int): Int = when (value) {
        PORTRAIT  -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else      -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
