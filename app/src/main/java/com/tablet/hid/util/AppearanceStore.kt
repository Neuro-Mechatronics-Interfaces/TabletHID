package com.tablet.hid.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object AppearanceStore {
    private const val PREFS = "tablet_hid_appearance"
    private const val KEY   = "night_mode_index"

    const val INDEX_SYSTEM = 0
    const val INDEX_LIGHT  = 1
    const val INDEX_DARK   = 2

    fun get(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, INDEX_SYSTEM)

    fun set(context: Context, index: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY, index).apply()
    }

    fun toNightMode(index: Int): Int = when (index) {
        INDEX_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        INDEX_DARK  -> AppCompatDelegate.MODE_NIGHT_YES
        else        -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(toNightMode(get(context)))
    }
}
