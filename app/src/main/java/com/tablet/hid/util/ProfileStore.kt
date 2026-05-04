package com.tablet.hid.util

import android.content.Context
import com.tablet.hid.model.Profile

/**
 * Persists the active profile key and the list of user-created custom profiles.
 * Built-in profiles are not stored here — they are defined in Profile.BUILT_INS.
 */
object ProfileStore {
    private const val PREFS = "profile_store"
    private const val KEY_ACTIVE  = "active_profile_key"
    private const val KEY_CUSTOM  = "custom_profile_keys"   // pipe-separated list of keys
    private const val PREFIX_NAME = "profile_name_"

    fun getActiveKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE, Profile.DEFAULT.key) ?: Profile.DEFAULT.key

    fun saveActiveKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACTIVE, key).apply()
    }

    fun getCustomProfiles(context: Context): List<Profile> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CUSTOM, null) ?: return emptyList()
        return raw.split("|").filter { it.isNotEmpty() }.mapNotNull { key ->
            val name = prefs.getString(PREFIX_NAME + key, null) ?: return@mapNotNull null
            Profile(name, key)
        }
    }

    fun addCustomProfile(context: Context, name: String): Profile {
        val key = "custom_${System.currentTimeMillis()}"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_CUSTOM, null)
            ?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()
        prefs.edit()
            .putString(KEY_CUSTOM, (existing + key).joinToString("|"))
            .putString(PREFIX_NAME + key, name)
            .apply()
        return Profile(name, key)
    }

    fun getActiveProfile(context: Context): Profile {
        val key = getActiveKey(context)
        return Profile.fromKey(key, getCustomProfiles(context))
    }
}
