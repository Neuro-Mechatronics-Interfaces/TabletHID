package com.tablet.hid.util

import com.tablet.hid.BuildConfig
import com.tablet.hid.model.CommunityConfigRecord
import com.tablet.hid.model.CommunityListResponse
import com.tablet.hid.model.CommunityUploadBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object ConfigApiClient {

    private fun baseUrl(): Result<String> {
        val url = BuildConfig.COMMUNITY_API_BASE_URL
        return if (url.isBlank()) {
            Result.failure(IllegalStateException("API base URL not configured"))
        } else {
            Result.success(url.trimEnd('/'))
        }
    }

    suspend fun fetchConfigs(
        mode: String? = null,
        platform: String? = null,
        tags: List<String>? = null,
        category: String? = null,
        sort: String = "recent",
        limit: Int = 20,
        offset: Int = 0,
        since: String? = null,
    ): Result<CommunityListResponse> = withContext(Dispatchers.IO) {
        val base = baseUrl().getOrElse { return@withContext Result.failure(it) }
        val params = buildList {
            add("sort=$sort")
            add("limit=$limit")
            add("offset=$offset")
            if (!mode.isNullOrBlank()) add("mode=$mode")
            if (!platform.isNullOrBlank()) add("platform=$platform")
            if (!category.isNullOrBlank()) add("category=$category")
            if (!tags.isNullOrEmpty()) add("tags=${tags.joinToString(",")}")
            if (!since.isNullOrBlank()) add("since=$since")
        }
        val urlStr = "$base/api/v1/configs?${params.joinToString("&")}"
        runCatching {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            val code = conn.responseCode
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            if (code != 200) error("HTTP $code: $body")
            parseListResponse(JSONObject(body))
        }
    }

    suspend fun fetchConfig(id: String): Result<CommunityConfigRecord> = withContext(Dispatchers.IO) {
        val base = baseUrl().getOrElse { return@withContext Result.failure(it) }
        val urlStr = "$base/api/v1/configs/$id"
        runCatching {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            val code = conn.responseCode
            val body = if (code == 200) conn.inputStream.bufferedReader().readText()
                       else conn.errorStream.bufferedReader().readText()
            conn.disconnect()
            if (code != 200) error("HTTP $code: $body")
            parseRecord(JSONObject(body))
        }
    }

    suspend fun uploadConfig(body: CommunityUploadBody): Result<String> = withContext(Dispatchers.IO) {
        val base = baseUrl().getOrElse { return@withContext Result.failure(it) }
        val urlStr = "$base/api/v1/configs"
        runCatching {
            val payload = serializeUploadBody(body)
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = conn.responseCode
            val responseBody = if (code == 200 || code == 201)
                conn.inputStream.bufferedReader().readText()
            else
                conn.errorStream.bufferedReader().readText()
            conn.disconnect()
            if (code != 200 && code != 201) error("HTTP $code: $responseBody")
            JSONObject(responseBody).getString("id")
        }
    }

    private fun serializeUploadBody(body: CommunityUploadBody): JSONObject {
        val json = JSONObject()
        json.put("platform", body.platform)
        json.put("mode", body.mode)
        json.put("profile_name", body.profileName)
        json.put("config_json", when (body.configJson) {
            is JSONObject -> body.configJson
            is String -> JSONObject(body.configJson)
            else -> body.configJson
        })
        body.description?.let { json.put("description", it) }
        if (body.tags.isNotEmpty()) {
            val arr = JSONArray()
            body.tags.forEach { arr.put(it) }
            json.put("tags", arr)
        }
        body.category?.let { json.put("category", it) }
        body.appVersion?.let { json.put("app_version", it) }
        body.deviceName?.let { json.put("device_name", it) }
        body.deviceHwId?.let { json.put("device_hw_id", it) }
        body.deviceOsVersion?.let { json.put("device_os_version", it) }
        body.deviceOsApiLevel?.let { json.put("device_os_api_level", it) }
        body.deviceScreenWidthPx?.let { json.put("device_screen_width_px", it) }
        body.deviceScreenHeightPx?.let { json.put("device_screen_height_px", it) }
        body.deviceScreenDensityDpi?.let { json.put("device_screen_density_dpi", it) }
        return json
    }

    private fun parseListResponse(json: JSONObject): CommunityListResponse {
        val arr = json.getJSONArray("configs")
        val configs = List(arr.length()) { i -> parseRecord(arr.getJSONObject(i)) }
        return CommunityListResponse(
            configs = configs,
            total = json.optInt("total", configs.size),
            latestAt = json.optString("latest_at").takeIf { it.isNotEmpty() },
        )
    }

    internal fun parseRecord(json: JSONObject): CommunityConfigRecord {
        val tagsArr = json.optJSONArray("tags")
        val tags = if (tagsArr != null) List(tagsArr.length()) { i -> tagsArr.getString(i) } else emptyList()
        val configJsonVal = json.opt("config_json")
        val configJsonStr = when (configJsonVal) {
            is JSONObject -> configJsonVal.toString()
            is String -> configJsonVal
            else -> "{}"
        }
        return CommunityConfigRecord(
            id = json.getString("id"),
            schemaVersion = json.optInt("schema_version", 1),
            platform = json.getString("platform"),
            mode = json.getString("mode"),
            profileName = json.getString("profile_name"),
            description = json.optString("description").takeIf { it.isNotEmpty() },
            tags = tags,
            category = json.optString("category").takeIf { it.isNotEmpty() },
            appVersion = json.optString("app_version").takeIf { it.isNotEmpty() },
            configJson = configJsonStr,
            uploadedAt = json.getString("uploaded_at"),
            downloadCount = json.optInt("download_count", 0),
            deviceName = json.optString("device_name").takeIf { it.isNotEmpty() },
            deviceHwId = json.optString("device_hw_id").takeIf { it.isNotEmpty() },
            deviceOsVersion = json.optString("device_os_version").takeIf { it.isNotEmpty() },
            deviceOsApiLevel = if (json.has("device_os_api_level") && !json.isNull("device_os_api_level"))
                json.getInt("device_os_api_level") else null,
            deviceScreenWidthPx = if (json.has("device_screen_width_px") && !json.isNull("device_screen_width_px"))
                json.getInt("device_screen_width_px") else null,
            deviceScreenHeightPx = if (json.has("device_screen_height_px") && !json.isNull("device_screen_height_px"))
                json.getInt("device_screen_height_px") else null,
            deviceScreenDensityDpi = if (json.has("device_screen_density_dpi") && !json.isNull("device_screen_density_dpi"))
                json.getInt("device_screen_density_dpi") else null,
            deviceScreenDiagonalIn = if (json.has("device_screen_diagonal_in") && !json.isNull("device_screen_diagonal_in"))
                json.getDouble("device_screen_diagonal_in").toFloat() else null,
        )
    }
}
