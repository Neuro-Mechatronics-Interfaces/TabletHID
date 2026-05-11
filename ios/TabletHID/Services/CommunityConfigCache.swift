import Foundation

/// UserDefaults-backed cache for community config records.
///
/// Mirrors CommunityConfigCache.kt exactly:
/// - Key "community_latest_at" holds the ISO 8601 delta cursor.
/// - Key "community_cache_v1"  holds a JSON array of CommunityConfigRecord objects.
/// - Maximum 500 records; on overflow the most-recent records by uploadedAt are kept.
final class CommunityConfigCache {

    private let defaults: UserDefaults
    private static let keyLatestAt = "community_latest_at"
    private static let keyCacheV1  = "community_cache_v1"
    private static let maxCacheSize = 500

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    // MARK: - Cursor

    func getLatestAt() -> String? {
        let s = defaults.string(forKey: Self.keyLatestAt)
        return (s?.isEmpty == false) ? s : nil
    }

    func setLatestAt(_ value: String) {
        defaults.set(value, forKey: Self.keyLatestAt)
    }

    // MARK: - Records

    func getAll() -> [CommunityConfigRecord] {
        guard let raw = defaults.string(forKey: Self.keyCacheV1),
              let data = raw.data(using: .utf8) else { return [] }
        return (try? JSONDecoder().decode([CommunityConfigRecord].self, from: data)) ?? []
    }

    func replaceAll(_ records: [CommunityConfigRecord]) {
        guard let data = try? JSONEncoder().encode(records),
              let str = String(data: data, encoding: .utf8) else { return }
        defaults.set(str, forKey: Self.keyCacheV1)
    }

    func replaceOrInsert(_ record: CommunityConfigRecord) {
        var records = getAll()
        if let index = records.firstIndex(where: { $0.id == record.id }) {
            records[index] = record
        } else {
            records.append(record)
        }
        if records.count > Self.maxCacheSize {
            records.sort { $0.uploadedAt > $1.uploadedAt }
            records = Array(records.prefix(Self.maxCacheSize))
        }
        replaceAll(records)
    }

    /// Merge-insert: insert new records by id; never replace existing records.
    /// Trims to the most-recent 500 records when the cache exceeds the cap.
    func mergeIn(_ newRecords: [CommunityConfigRecord]) {
        guard !newRecords.isEmpty else { return }
        let existing = getAll()
        var byId = OrderedDictionary<String, CommunityConfigRecord>()
        for record in existing { byId[record.id] = record }
        for record in newRecords {
            if byId[record.id] == nil { byId[record.id] = record }
        }
        var merged = Array(byId.values)
        if merged.count > Self.maxCacheSize {
            merged.sort { $0.uploadedAt > $1.uploadedAt }
            merged = Array(merged.prefix(Self.maxCacheSize))
        }
        replaceAll(merged)
    }

    func clear() {
        defaults.removeObject(forKey: Self.keyCacheV1)
        defaults.removeObject(forKey: Self.keyLatestAt)
    }
}

// MARK: - OrderedDictionary (insertion-order-preserving thin wrapper)

/// A minimal insertion-order-preserving dictionary used so merge produces
/// a deterministic ordering without sorting by key.
private struct OrderedDictionary<Key: Hashable, Value> {
    private var keys: [Key] = []
    private var dict: [Key: Value] = [:]

    subscript(key: Key) -> Value? {
        get { dict[key] }
        set {
            if let v = newValue {
                if dict[key] == nil { keys.append(key) }
                dict[key] = v
            } else {
                keys.removeAll { $0 == key }
                dict.removeValue(forKey: key)
            }
        }
    }

    var values: [Value] { keys.compactMap { dict[$0] } }
}
