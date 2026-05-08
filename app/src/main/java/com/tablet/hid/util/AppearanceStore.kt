package com.tablet.hid.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object AppearanceStore {
    private const val PREFS = "tablet_hid_appearance"
    private const val KEY   = "night_mode_index"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_LARGE_TEXT = "large_text"
    private const val KEY_HIGH_CONTRAST = "high_contrast"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    const val DEFAULT_DEVICE_NAME = "TabletHID"

    const val INDEX_SYSTEM = 0
    const val INDEX_LIGHT  = 1
    const val INDEX_DARK   = 2

    fun get(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, INDEX_SYSTEM)

    fun set(context: Context, index: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY, index).apply()
    }

    fun getDeviceName(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_NAME, DEFAULT_DEVICE_NAME)
        return sanitizeDeviceName(saved)
    }

    fun setDeviceName(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEVICE_NAME, sanitizeDeviceName(name)).apply()
    }

    fun sanitizeDeviceName(name: String?): String {
        val trimmed = name.orEmpty().trim()
        return trimmed.ifBlank { DEFAULT_DEVICE_NAME }.take(32)
    }

    fun isLargeText(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LARGE_TEXT, false)

    fun setLargeText(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LARGE_TEXT, enabled).apply()
    }

    fun isHighContrast(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIGH_CONTRAST, false)

    fun setHighContrast(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
    }

    fun hasCompletedOnboarding(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingComplete(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
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
