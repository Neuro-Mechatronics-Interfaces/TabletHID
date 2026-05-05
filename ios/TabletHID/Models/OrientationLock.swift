import Foundation
#if canImport(UIKit)
import UIKit
#endif

enum OrientationLock: String, CaseIterable, Identifiable {
    case system    = "system"
    case portrait  = "portrait"
    case landscape = "landscape"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .system:    return "System"
        case .portrait:  return "Portrait"
        case .landscape: return "Landscape"
        }
    }

    var symbolName: String {
        switch self {
        case .system:    return "arrow.2.circlepath"
        case .portrait:  return "iphone"
        case .landscape: return "rectangle"
        }
    }

    var next: OrientationLock {
        let cases = OrientationLock.allCases
        let idx = cases.firstIndex(of: self)!
        return cases[(idx + 1) % cases.count]
    }

    #if canImport(UIKit)
    var interfaceOrientationMask: UIInterfaceOrientationMask {
        switch self {
        case .system:    return .all
        case .portrait:  return .portrait
        case .landscape: return .landscape
        }
    }
    #endif
}
