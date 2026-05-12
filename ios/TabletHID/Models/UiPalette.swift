#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif
import SwiftUI

struct UiPalette: Hashable, Identifiable {
    let id: Int
    let name: String
    let leftRgb:   UInt32
    let rightRgb:  UInt32
    let sniperRgb: UInt32
    let macroRgb:  UInt32

    // MARK: - SwiftUI colors
    var leftColor:   Color { swiftUIColor(leftRgb) }
    var rightColor:  Color { swiftUIColor(rightRgb) }
    var sniperColor: Color { swiftUIColor(sniperRgb) }
    var macroColor:  Color { swiftUIColor(macroRgb) }

    // MARK: - Platform colors (UIKit)
    #if canImport(UIKit)
    func leftUIColor(alpha: CGFloat)   -> UIColor { uiColor(leftRgb,   alpha: alpha) }
    func rightUIColor(alpha: CGFloat)  -> UIColor { uiColor(rightRgb,  alpha: alpha) }
    func sniperUIColor(alpha: CGFloat) -> UIColor { uiColor(sniperRgb, alpha: alpha) }
    #elseif canImport(AppKit)
    func leftNSColor(alpha: CGFloat)   -> NSColor { nsColor(leftRgb,   alpha: alpha) }
    func rightNSColor(alpha: CGFloat)  -> NSColor { nsColor(rightRgb,  alpha: alpha) }
    func sniperNSColor(alpha: CGFloat) -> NSColor { nsColor(sniperRgb, alpha: alpha) }
    #endif

    // MARK: - Presets (mirroring Android UiPalette.ENTRIES)
    static let all: [UiPalette] = [
        UiPalette(id: 0, name: "Default",    leftRgb: 0x1E8CFF, rightRgb: 0xFF7814, sniperRgb: 0x00BE78, macroRgb: 0x5C6BC0),
        UiPalette(id: 1, name: "Neon",       leftRgb: 0x00CFFF, rightRgb: 0xFF00FF, sniperRgb: 0x00FF88, macroRgb: 0x00E5FF),
        UiPalette(id: 2, name: "Fire",       leftRgb: 0xFF4444, rightRgb: 0xFF8C00, sniperRgb: 0xFFD700, macroRgb: 0xFF6B35),
        UiPalette(id: 3, name: "Ice",        leftRgb: 0x87CEEB, rightRgb: 0xB0E2FF, sniperRgb: 0xE0FFFF, macroRgb: 0x4FC3F7),
        UiPalette(id: 4, name: "Monochrome", leftRgb: 0xFFFFFF, rightRgb: 0xAAAAAA, sniperRgb: 0xCCCCCC, macroRgb: 0xFFFFFF),
    ]
    static var `default`: UiPalette { all[0] }

    // MARK: - Private helpers
    private func components(_ hex: UInt32) -> (r: CGFloat, g: CGFloat, b: CGFloat) {
        (CGFloat((hex >> 16) & 0xFF) / 255,
         CGFloat((hex >> 8)  & 0xFF) / 255,
         CGFloat( hex        & 0xFF) / 255)
    }

    private func swiftUIColor(_ hex: UInt32) -> Color {
        let c = components(hex)
        return Color(red: c.r, green: c.g, blue: c.b)
    }

    #if canImport(UIKit)
    private func uiColor(_ hex: UInt32, alpha: CGFloat) -> UIColor {
        let c = components(hex)
        return UIColor(red: c.r, green: c.g, blue: c.b, alpha: alpha)
    }
    #elseif canImport(AppKit)
    private func nsColor(_ hex: UInt32, alpha: CGFloat) -> NSColor {
        let c = components(hex)
        return NSColor(red: c.r, green: c.g, blue: c.b, alpha: alpha)
    }
    #endif
}
