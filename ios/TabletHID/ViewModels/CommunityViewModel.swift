import Foundation
import Observation

/// Observable view model for the community config-sharing feature.
/// Mirrors CommunityViewModel.kt: same state shape, same filter/sort logic, same
/// refresh/syncIfStale strategy against the local CommunityConfigCache.
@Observable
@MainActor
final class CommunityViewModel {

    // MARK: - State

    var isLoading: Bool = false
    var error: String?  = nil
    var configs: [CommunityConfigRecord] = []
    var filterMode: String?     = nil
    var filterPlatform: String? = nil
    var filterCategory: String? = nil
    var sortOrder: String       = "recent"
    var searchQuery: String     = ""

    // MARK: - Private

    private let cache = CommunityConfigCache()

    init() {
        let cached = cache.getAll()
        if !cached.isEmpty {
            configs = applyFilters(cached)
        }
    }

    // MARK: - Filter / sort setters

    func setFilterMode(_ mode: String?) {
        filterMode = mode
        configs = applyFilters(cache.getAll())
    }

    func setFilterPlatform(_ platform: String?) {
        filterPlatform = platform
        configs = applyFilters(cache.getAll())
    }

    func setFilterCategory(_ category: String?) {
        filterCategory = category
        configs = applyFilters(cache.getAll())
    }

    func setSortOrder(_ order: String) {
        sortOrder = order
        configs = applyFilters(cache.getAll())
    }

    func setSearchQuery(_ query: String) {
        searchQuery = query
        configs = applyFilters(cache.getAll())
    }

    // MARK: - Network

    /// Full refresh: replaces the cache with a fresh page from the server.
    func refresh() async {
        isLoading = true
        error = nil
        do {
            let response = try await ConfigApiClient.shared.fetchConfigs(
                mode:     filterMode,
                platform: filterPlatform,
                category: filterCategory,
                sort:     sortOrder,
                limit:    100
            )
            cache.clear()
            cache.replaceAll(response.configs)
            if let at = response.latestAt { cache.setLatestAt(at) }
            isLoading = false
            configs = applyFilters(response.configs)
        } catch {
            isLoading = false
            self.error = error.localizedDescription
        }
    }

    /// Incremental sync: fetches only records newer than the stored cursor.
    func syncIfStale() async {
        let since = cache.getLatestAt()
        isLoading = true
        error = nil
        do {
            let response = try await ConfigApiClient.shared.fetchConfigs(
                sort:  "recent",
                limit: 100,
                since: since
            )
            cache.mergeIn(response.configs)
            if let at = response.latestAt { cache.setLatestAt(at) }
            let all = cache.getAll()
            isLoading = false
            configs = applyFilters(all)
        } catch {
            isLoading = false
            self.error = error.localizedDescription
        }
    }

    /// Upload a config to the server. Returns the new record id on success.
    func uploadConfig(_ body: CommunityUploadBody) async throws -> String {
        return try await ConfigApiClient.shared.uploadConfig(body: body)
    }

    /// Fetching by id increments the server-side download count; use this before import.
    func fetchConfigForImport(id: String) async throws -> CommunityConfigRecord {
        let record = try await ConfigApiClient.shared.fetchConfig(id: id)
        cache.replaceOrInsert(record)
        configs = applyFilters(cache.getAll())
        return record
    }

    // MARK: - Filtering (client-side, same logic as Android)

    private func applyFilters(_ all: [CommunityConfigRecord]) -> [CommunityConfigRecord] {
        var result = all
        if let mode = filterMode, !mode.isEmpty {
            result = result.filter { $0.mode == mode }
        }
        if let platform = filterPlatform, !platform.isEmpty {
            result = result.filter { $0.platform == platform }
        }
        if let category = filterCategory, !category.isEmpty {
            result = result.filter { $0.category == category }
        }
        if !searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let q = searchQuery.lowercased()
            result = result.filter { record in
                record.profileName.lowercased().contains(q) ||
                (record.description?.lowercased().contains(q) ?? false) ||
                (record.deviceName?.lowercased().contains(q) ?? false) ||
                record.tags.contains(where: { $0.lowercased().contains(q) })
            }
        }
        switch sortOrder {
        case "popular":
            result.sort { $0.downloadCount > $1.downloadCount }
        default:
            result.sort { $0.uploadedAt > $1.uploadedAt }
        }
        return result
    }
}
