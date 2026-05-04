import Foundation

struct Profile: Identifiable, Codable, Hashable {
    let name: String
    let key: String

    var id: String { key }

    static let defaultProfile = Profile(name: "Default", key: "default")
    static let accessBasic = Profile(name: "Access Basic", key: "access_basic")
    static let accessAdvanced = Profile(name: "Access Advanced", key: "access_advanced")
    static let builtIns = [defaultProfile, accessBasic, accessAdvanced]
}
