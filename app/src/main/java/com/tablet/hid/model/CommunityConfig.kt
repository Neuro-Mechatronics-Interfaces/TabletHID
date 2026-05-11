package com.tablet.hid.model

data class CommunityConfigRecord(
    val id: String,
    val schemaVersion: Int,
    val platform: String,
    val mode: String,
    val profileName: String,
    val description: String?,
    val tags: List<String>,
    val category: String?,
    val appVersion: String?,
    val configJson: String,
    val uploadedAt: String,
    val downloadCount: Int,
    val deviceName: String?,
    val deviceHwId: String?,
    val deviceOsVersion: String?,
    val deviceOsApiLevel: Int?,
    val deviceScreenWidthPx: Int?,
    val deviceScreenHeightPx: Int?,
    val deviceScreenDensityDpi: Int?,
    val deviceScreenDiagonalIn: Float?,
)

data class CommunityListResponse(
    val configs: List<CommunityConfigRecord>,
    val total: Int,
    val latestAt: String?,
)

data class CommunityUploadBody(
    val platform: String,
    val mode: String,
    val profileName: String,
    val configJson: Any,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val category: String? = null,
    val appVersion: String? = null,
    val deviceName: String? = null,
    val deviceHwId: String? = null,
    val deviceOsVersion: String? = null,
    val deviceOsApiLevel: Int? = null,
    val deviceScreenWidthPx: Int? = null,
    val deviceScreenHeightPx: Int? = null,
    val deviceScreenDensityDpi: Int? = null,
)
