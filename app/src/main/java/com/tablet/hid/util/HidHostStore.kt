package com.tablet.hid.util

import android.content.Context
import com.tablet.hid.model.HidHost
import org.json.JSONArray
import org.json.JSONObject

object HidHostStore {

    private const val PREFS = "hid_prefs"
    private const val KEY_HOSTS = "known_hosts"
    private const val KEY_LEGACY = "last_device"
    private const val MAX_HOSTS = 10

    fun getAll(context: Context): List<HidHost> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // One-time migration from the old single-address key.
        if (!prefs.contains(KEY_HOSTS) && prefs.contains(KEY_LEGACY)) {
            val address = prefs.getString(KEY_LEGACY, null)
            if (address != null) {
                val migrated = listOf(HidHost(address = address, btName = "", lastSeenMs = 0L))
                persist(context, migrated)
                prefs.edit().remove(KEY_LEGACY).apply()
                return migrated
            }
        }

        val json = prefs.getString(KEY_HOSTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                HidHost(
                    address    = obj.getString("address"),
                    btName     = obj.optString("btName", ""),
                    alias      = obj.optString("alias", "").takeIf { it.isNotEmpty() },
                    lastSeenMs = obj.optLong("lastSeenMs", 0L)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Insert or update a host by address.
     * Preserves any existing user alias; updates btName and lastSeenMs from the live device.
     */
    fun upsert(context: Context, host: HidHost) {
        val list = getAll(context).toMutableList()
        val idx  = list.indexOfFirst { it.address == host.address }
        if (idx >= 0) {
            list[idx] = list[idx].copy(
                btName     = host.btName.ifBlank { list[idx].btName },
                lastSeenMs = host.lastSeenMs
            )
        } else {
            list.add(0, host)
        }
        persist(context, list.sortedByDescending { it.lastSeenMs }.take(MAX_HOSTS))
    }

    fun updateAlias(context: Context, address: String, alias: String?) {
        val trimmed = alias?.trim()?.takeIf { it.isNotEmpty() }
        persist(context, getAll(context).map {
            if (it.address == address) it.copy(alias = trimmed) else it
        })
    }

    fun remove(context: Context, address: String) {
        persist(context, getAll(context).filter { it.address != address })
    }

    private fun persist(context: Context, hosts: List<HidHost>) {
        val arr = JSONArray()
        hosts.forEach { h ->
            arr.put(JSONObject().apply {
                put("address",    h.address)
                put("btName",     h.btName)
                if (!h.alias.isNullOrBlank()) put("alias", h.alias)
                put("lastSeenMs", h.lastSeenMs)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_HOSTS, arr.toString()).apply()
    }
}
