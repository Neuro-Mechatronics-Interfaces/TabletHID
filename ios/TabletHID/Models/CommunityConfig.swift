import Foundation

// MARK: - CommunityConfigRecord

/// A single config record returned by the server. Field names match the server's snake_case
/// column names exactly (mapped via CodingKeys).
struct CommunityConfigRecord: Codable, Identifiable {
    let id: String
    let schemaVersion: Int
    let platform: String
    let mode: String
    let profileName: String
    let description: String?
    let tags: [String]
    let category: String?
    let appVersion: String?
    /// Raw canonical JSON string for the config payload.
    let configJson: String
    let uploadedAt: String
    let downloadCount: Int
    let deviceName: String?
    let deviceHwId: String?
    let deviceOsVersion: String?
    let deviceOsApiLevel: Int?
    let deviceScreenWidthPx: Int?
    let deviceScreenHeightPx: Int?
    let deviceScreenDensityDpi: Int?
    let deviceScreenDiagonalIn: Double?

    enum CodingKeys: String, CodingKey {
        case id
        case schemaVersion         = "schema_version"
        case platform
        case mode
        case profileName           = "profile_name"
        case description
        case tags
        case category
        case appVersion            = "app_version"
        case configJson            = "config_json"
        case uploadedAt            = "uploaded_at"
        case downloadCount         = "download_count"
        case deviceName            = "device_name"
        case deviceHwId            = "device_hw_id"
        case deviceOsVersion       = "device_os_version"
        case deviceOsApiLevel      = "device_os_api_level"
        case deviceScreenWidthPx   = "device_screen_width_px"
        case deviceScreenHeightPx  = "device_screen_height_px"
        case deviceScreenDensityDpi = "device_screen_density_dpi"
        case deviceScreenDiagonalIn = "device_screen_diagonal_in"
    }

    // Custom decode: config_json may arrive as a JSON object or as a string.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id              = try container.decode(String.self, forKey: .id)
        schemaVersion   = try container.decodeIfPresent(Int.self, forKey: .schemaVersion) ?? 1
        platform        = try container.decode(String.self, forKey: .platform)
        mode            = try container.decode(String.self, forKey: .mode)
        profileName     = try container.decode(String.self, forKey: .profileName)
        description     = try container.decodeIfPresent(String.self, forKey: .description)
        tags            = try container.decodeIfPresent([String].self, forKey: .tags) ?? []
        category        = try container.decodeIfPresent(String.self, forKey: .category)
        appVersion      = try container.decodeIfPresent(String.self, forKey: .appVersion)
        uploadedAt      = try container.decode(String.self, forKey: .uploadedAt)
        downloadCount   = try container.decodeIfPresent(Int.self, forKey: .downloadCount) ?? 0
        deviceName      = try container.decodeIfPresent(String.self, forKey: .deviceName)
        deviceHwId      = try container.decodeIfPresent(String.self, forKey: .deviceHwId)
        deviceOsVersion = try container.decodeIfPresent(String.self, forKey: .deviceOsVersion)
        deviceOsApiLevel = try container.decodeIfPresent(Int.self, forKey: .deviceOsApiLevel)
        deviceScreenWidthPx    = try container.decodeIfPresent(Int.self, forKey: .deviceScreenWidthPx)
        deviceScreenHeightPx   = try container.decodeIfPresent(Int.self, forKey: .deviceScreenHeightPx)
        deviceScreenDensityDpi = try container.decodeIfPresent(Int.self, forKey: .deviceScreenDensityDpi)
        deviceScreenDiagonalIn = try container.decodeIfPresent(Double.self, forKey: .deviceScreenDiagonalIn)

        // config_json may be a nested JSON object or a pre-serialised string.
        if let str = try? container.decode(String.self, forKey: .configJson) {
            configJson = str
        } else if
            let raw = try? container.decode(CodingKeylessDict.self, forKey: .configJson),
            let data = try? JSONSerialization.data(withJSONObject: raw.value),
            let str = String(data: data, encoding: .utf8)
        {
            configJson = str
        } else {
            configJson = "{}"
        }
    }
}

// Helper for decoding arbitrary JSON objects without a fixed key list.
private struct CodingKeylessDict: Decodable {
    let value: [String: Any]

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = try container.decode(AnyCodable.self)
        if let dict = raw.value as? [String: Any] {
            value = dict
        } else {
            value = [:]
        }
    }
}

private struct AnyCodable: Decodable {
    let value: Any

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let v = try? container.decode(Bool.self) { value = v }
        else if let v = try? container.decode(Int.self) { value = v }
        else if let v = try? container.decode(Double.self) { value = v }
        else if let v = try? container.decode(String.self) { value = v }
        else if let v = try? container.decode([AnyCodable].self) { value = v.map { $0.value } }
        else if let v = try? container.decode([String: AnyCodable].self) {
            value = v.mapValues { $0.value }
        } else {
            value = NSNull()
        }
    }
}

// MARK: - CommunityListResponse

struct CommunityListResponse: Codable {
    let configs: [CommunityConfigRecord]
    let total: Int
    let latestAt: String?

    enum CodingKeys: String, CodingKey {
        case configs
        case total
        case latestAt = "latest_at"
    }
}

// MARK: - CommunityUploadBody

/// Request body for POST /api/v1/configs. Field names match the server contract.
struct CommunityUploadBody: Encodable {
    let platform: String
    let mode: String
    let profileName: String
    /// Canonical JSON dictionary for the config payload (will be serialised as a nested object).
    let configJson: [String: Any]
    var description: String?
    var tags: [String]
    var category: String?
    var appVersion: String?
    var deviceName: String?
    var deviceHwId: String?
    var deviceOsVersion: String?
    var deviceOsApiLevel: Int?
    var deviceScreenWidthPx: Int?
    var deviceScreenHeightPx: Int?
    var deviceScreenDensityDpi: Int?

    enum CodingKeys: String, CodingKey {
        case platform
        case mode
        case profileName           = "profile_name"
        case configJson            = "config_json"
        case description
        case tags
        case category
        case appVersion            = "app_version"
        case deviceName            = "device_name"
        case deviceHwId            = "device_hw_id"
        case deviceOsVersion       = "device_os_version"
        case deviceOsApiLevel      = "device_os_api_level"
        case deviceScreenWidthPx   = "device_screen_width_px"
        case deviceScreenHeightPx  = "device_screen_height_px"
        case deviceScreenDensityDpi = "device_screen_density_dpi"
    }

    init(
        platform: String,
        mode: String,
        profileName: String,
        configJson: [String: Any],
        description: String? = nil,
        tags: [String] = [],
        category: String? = nil,
        appVersion: String? = nil,
        deviceName: String? = nil,
        deviceHwId: String? = nil,
        deviceOsVersion: String? = nil,
        deviceOsApiLevel: Int? = nil,
        deviceScreenWidthPx: Int? = nil,
        deviceScreenHeightPx: Int? = nil,
        deviceScreenDensityDpi: Int? = nil
    ) {
        self.platform = platform
        self.mode = mode
        self.profileName = profileName
        self.configJson = configJson
        self.description = description
        self.tags = tags
        self.category = category
        self.appVersion = appVersion
        self.deviceName = deviceName
        self.deviceHwId = deviceHwId
        self.deviceOsVersion = deviceOsVersion
        self.deviceOsApiLevel = deviceOsApiLevel
        self.deviceScreenWidthPx = deviceScreenWidthPx
        self.deviceScreenHeightPx = deviceScreenHeightPx
        self.deviceScreenDensityDpi = deviceScreenDensityDpi
    }

    // CommunityUploadBody uses [String: Any] which isn't auto-Codable; we only need encoding.
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(platform, forKey: .platform)
        try container.encode(mode, forKey: .mode)
        try container.encode(profileName, forKey: .profileName)
        try container.encodeIfPresent(description, forKey: .description)
        if !tags.isEmpty { try container.encode(tags, forKey: .tags) }
        try container.encodeIfPresent(category, forKey: .category)
        try container.encodeIfPresent(appVersion, forKey: .appVersion)
        try container.encodeIfPresent(deviceName, forKey: .deviceName)
        try container.encodeIfPresent(deviceHwId, forKey: .deviceHwId)
        try container.encodeIfPresent(deviceOsVersion, forKey: .deviceOsVersion)
        try container.encodeIfPresent(deviceOsApiLevel, forKey: .deviceOsApiLevel)
        try container.encodeIfPresent(deviceScreenWidthPx, forKey: .deviceScreenWidthPx)
        try container.encodeIfPresent(deviceScreenHeightPx, forKey: .deviceScreenHeightPx)
        try container.encodeIfPresent(deviceScreenDensityDpi, forKey: .deviceScreenDensityDpi)
        // config_json is encoded as a nested object by manually writing it.
        // JSONEncoder doesn't support [String: Any]; we handle this in ConfigApiClient.
    }
}
