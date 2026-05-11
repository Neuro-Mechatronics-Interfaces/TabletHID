package com.tablet.hid.util

import android.content.Context
import android.content.SharedPreferences
import com.tablet.hid.model.CommunityConfigRecord
import org.json.JSONArray
import org.json.JSONObject

class CommunityConfigCache(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("community_config_cache", Context.MODE_PRIVATE)

    fun getLatestAt(): String? =
        prefs.getString("community_latest_at", null).takeIf { !it.isNullOrEmpty() }

    fun setLatestAt(value: String) {
        prefs.edit().putString("community_latest_at", value).apply()
    }

    fun getAll(): List<CommunityConfigRecord> {
        val raw = prefs.getString("community_cache_v1", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> ConfigApiClient.parseRecord(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    fun replaceAll(records: List<CommunityConfigRecord>) {
        val arr = JSONArray()
        records.forEach { arr.put(recordToJson(it)) }
        prefs.edit().putString("community_cache_v1", arr.toString()).apply()
    }

    fun mergeIn(newRecords: List<CommunityConfigRecord>) {
        if (newRecords.isEmpty()) return
        val existing = getAll()
        val byId = LinkedHashMap<String, CommunityConfigRecord>(existing.size + newRecords.size)
        existing.forEach { byId[it.id] = it }
        newRecords.forEach { record -> byId.putIfAbsent(record.id, record) }
        val merged = byId.values.toMutableList()
        if (merged.size > MAX_CACHE_SIZE) {
            merged.sortByDescending { it.uploadedAt }
            replaceAll(merged.subList(0, MAX_CACHE_SIZE))
        } else {
            replaceAll(merged)
        }
    }

    fun clear() {
        prefs.edit()
            .remove("community_cache_v1")
            .remove("community_latest_at")
            .apply()
    }

    private fun recordToJson(record: CommunityConfigRecord): JSONObject {
        val json = JSONObject()
        json.put("id", record.id)
        json.put("schema_version", record.schemaVersion)
        json.put("platform", record.platform)
        json.put("mode", record.mode)
        json.put("profile_name", record.profileName)
        record.description?.let { json.put("description", it) }
        val tags = JSONArray()
        record.tags.forEach { tags.put(it) }
        json.put("tags", tags)
        record.category?.let { json.put("category", it) }
        record.appVersion?.let { json.put("app_version", it) }
        json.put("config_json", record.configJson)
        json.put("uploaded_at", record.uploadedAt)
        json.put("download_count", record.downloadCount)
        record.deviceName?.let { json.put("device_name", it) }
        record.deviceHwId?.let { json.put("device_hw_id", it) }
        record.deviceOsVersion?.let { json.put("device_os_version", it) }
        record.deviceOsApiLevel?.let { json.put("device_os_api_level", it) }
        record.deviceScreenWidthPx?.let { json.put("device_screen_width_px", it) }
        record.deviceScreenHeightPx?.let { json.put("device_screen_height_px", it) }
        record.deviceScreenDensityDpi?.let { json.put("device_screen_density_dpi", it) }
        record.deviceScreenDiagonalIn?.let { json.put("device_screen_diagonal_in", it.toDouble()) }
        return json
    }

    companion object {
        private const val MAX_CACHE_SIZE = 500
    }
}
