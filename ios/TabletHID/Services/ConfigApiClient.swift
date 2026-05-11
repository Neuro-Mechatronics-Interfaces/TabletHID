import Foundation

/// HTTP client for the community config sharing API. Mirrors ConfigApiClient.kt.
///
/// Base URL is read at call time from `Bundle.main.infoDictionary["CommunityApiBaseUrl"]`.
/// A blank or missing URL causes every call to throw `ConfigApiError.notConfigured`.
actor ConfigApiClient {

    static let shared = ConfigApiClient()

    private init() {}

    // MARK: - Public API

    func fetchConfigs(
        mode: String?     = nil,
        platform: String? = nil,
        tags: [String]?   = nil,
        category: String? = nil,
        sort: String      = "recent",
        limit: Int        = 20,
        offset: Int       = 0,
        since: String?    = nil
    ) async throws -> CommunityListResponse {
        let base = try resolvedBaseUrl()
        var components = URLComponents(string: "\(base)/api/v1/configs")!
        var queryItems: [URLQueryItem] = [
            URLQueryItem(name: "sort",   value: sort),
            URLQueryItem(name: "limit",  value: "\(limit)"),
            URLQueryItem(name: "offset", value: "\(offset)"),
        ]
        if let mode     = mode,     !mode.isEmpty     { queryItems.append(.init(name: "mode",     value: mode)) }
        if let platform = platform, !platform.isEmpty { queryItems.append(.init(name: "platform", value: platform)) }
        if let category = category, !category.isEmpty { queryItems.append(.init(name: "category", value: category)) }
        if let tags = tags, !tags.isEmpty             { queryItems.append(.init(name: "tags",     value: tags.joined(separator: ","))) }
        if let since = since, !since.isEmpty          { queryItems.append(.init(name: "since",    value: since)) }
        components.queryItems = queryItems

        let (data, response) = try await fetch(url: components.url!)
        try assertHTTP200(response, data: data)
        return try parseListResponse(data)
    }

    func fetchConfig(id: String) async throws -> CommunityConfigRecord {
        let base = try resolvedBaseUrl()
        let url = URL(string: "\(base)/api/v1/configs/\(id)")!
        let (data, response) = try await fetch(url: url)
        try assertHTTP200(response, data: data)
        return try JSONDecoder().decode(CommunityConfigRecord.self, from: data)
    }

    /// Upload a config. Returns the new record's UUID on success.
    func uploadConfig(body: CommunityUploadBody) async throws -> String {
        let base = try resolvedBaseUrl()
        let url  = URL(string: "\(base)/api/v1/configs")!
        let payload = try serializeUploadBody(body)
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = payload

        let (data, response) = try await URLSession.shared.data(for: request)
        try assertHTTP2xx(response, data: data)
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let id = json["id"] as? String else {
            throw ConfigApiError.unexpectedResponse("upload response missing id")
        }
        return id
    }

    // MARK: - Helpers

    private func resolvedBaseUrl() throws -> String {
        let raw = Bundle.main.infoDictionary?["CommunityApiBaseUrl"] as? String ?? ""
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard !trimmed.isEmpty,
              !trimmed.contains("$("),
              let url = URL(string: trimmed),
              url.scheme != nil,
              url.host != nil else {
            throw ConfigApiError.notConfigured
        }
        return trimmed
    }

    private func fetch(url: URL) async throws -> (Data, URLResponse) {
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        request.httpMethod = "GET"
        return try await URLSession.shared.data(for: request)
    }

    private func assertHTTP200(_ response: URLResponse, data: Data) throws {
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? -1
            let body = String(data: data, encoding: .utf8) ?? ""
            throw ConfigApiError.httpError(code, body)
        }
    }

    private func assertHTTP2xx(_ response: URLResponse, data: Data) throws {
        guard let http = response as? HTTPURLResponse,
              (200...299).contains(http.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? -1
            let body = String(data: data, encoding: .utf8) ?? ""
            throw ConfigApiError.httpError(code, body)
        }
    }

    private func parseListResponse(_ data: Data) throws -> CommunityListResponse {
        // config_json in each record may be a nested object; decode manually so we can
        // normalise it to a string before handing off to CommunityConfigRecord.
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let configsArray = root["configs"] as? [[String: Any]] else {
            throw ConfigApiError.unexpectedResponse("configs array missing")
        }
        let records = try configsArray.map { try parseRecord($0) }
        let total    = root["total"]     as? Int    ?? records.count
        let latestAt = root["latest_at"] as? String
        return CommunityListResponse(configs: records, total: total, latestAt: latestAt)
    }

    /// Parse a raw JSON dictionary into a CommunityConfigRecord, normalising config_json
    /// to a string regardless of whether the server returned it as a nested object or string.
    func parseRecord(_ json: [String: Any]) throws -> CommunityConfigRecord {
        // Normalise config_json to a string so the Codable decoder always sees a string.
        var mutable = json
        let configJsonRaw = mutable["config_json"]
        if let dict = configJsonRaw as? [String: Any],
           let data = try? JSONSerialization.data(withJSONObject: dict),
           let str = String(data: data, encoding: .utf8) {
            mutable["config_json"] = str
        } else if mutable["config_json"] == nil {
            mutable["config_json"] = "{}"
        }

        // Re-serialise with the normalised field so the Codable init can decode it.
        let data = try JSONSerialization.data(withJSONObject: mutable)
        return try JSONDecoder().decode(CommunityConfigRecord.self, from: data)
    }

    private func serializeUploadBody(_ body: CommunityUploadBody) throws -> Data {
        var json: [String: Any] = [
            "platform":     body.platform,
            "mode":         body.mode,
            "profile_name": body.profileName,
            "config_json":  body.configJson,
        ]
        if let v = body.description,          !v.isEmpty { json["description"]           = v }
        if !body.tags.isEmpty                            { json["tags"]                   = body.tags }
        if let v = body.category,             !v.isEmpty { json["category"]               = v }
        if let v = body.appVersion,           !v.isEmpty { json["app_version"]            = v }
        if let v = body.deviceName,           !v.isEmpty { json["device_name"]            = v }
        if let v = body.deviceHwId,           !v.isEmpty { json["device_hw_id"]           = v }
        if let v = body.deviceOsVersion,      !v.isEmpty { json["device_os_version"]      = v }
        if let v = body.deviceOsApiLevel                 { json["device_os_api_level"]     = v }
        if let v = body.deviceScreenWidthPx              { json["device_screen_width_px"]  = v }
        if let v = body.deviceScreenHeightPx             { json["device_screen_height_px"] = v }
        if let v = body.deviceScreenDensityDpi           { json["device_screen_density_dpi"] = v }
        return try JSONSerialization.data(withJSONObject: json)
    }
}

// MARK: - Errors

enum ConfigApiError: Error, LocalizedError {
    case notConfigured
    case httpError(Int, String)
    case unexpectedResponse(String)

    var errorDescription: String? {
        switch self {
        case .notConfigured:
            return "Community API base URL is not configured. Set COMMUNITY_API_BASE_URL in Secrets.xcconfig."
        case .httpError(let code, let body):
            return "HTTP \(code): \(body)"
        case .unexpectedResponse(let detail):
            return "Unexpected server response: \(detail)"
        }
    }
}
