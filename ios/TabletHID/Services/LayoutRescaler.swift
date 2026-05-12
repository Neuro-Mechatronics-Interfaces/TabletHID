import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Mirrors LayoutRescaler.kt — rescales gamepad layout offsets from one canvas size to another.
///
/// Canvas dimensions are measured in density-independent points (dp / UIKit points).
/// For cross-platform configs from Android devices, use canvasDimsFromScreenPx to convert
/// the uploaded pixel + dpi metadata into the same dp-equivalent scale used by the resolver.
enum LayoutRescaler {

    /// Converts physical screen pixels + densityDpi to (longSide, shortSide) in dp.
    /// Mirrors the Android formula: density = densityDpi / 160, dp = px / density.
    static func canvasDimsFromScreenPx(widthPx: Int, heightPx: Int, densityDpi: Int) -> (Double, Double) {
        let density = Double(densityDpi) / 160.0
        let wDp = Double(widthPx) / density
        let hDp = Double(heightPx) / density
        return (max(wDp, hDp), min(wDp, hDp))
    }

    #if canImport(UIKit)
    /// Returns (longSide, shortSide) for the current iOS screen in dp-equivalent units,
    /// using the same pixel+dpi formula as canvasDimsFromScreenPx so that configs uploaded
    /// from this device round-trip without rescaling.
    static func canvasDimsFromScreen() -> (Double, Double) {
        let screen = UIScreen.main
        let scale  = screen.scale
        // iOS approximation: 163 pt/in base PPI × scale = effective DPI.
        let dpi    = Int(163 * scale)
        let wPx    = Int(screen.bounds.width  * scale)
        let hPx    = Int(screen.bounds.height * scale)
        return canvasDimsFromScreenPx(widthPx: wPx, heightPx: hPx, densityDpi: dpi)
    }
    #endif

    /// Returns a rescaled copy of `config` whose layout offsets fit the tgtW × tgtH canvas.
    /// Returns `config` unchanged when the canvases are within 1 dp of each other.
    /// scaleX/Y are not touched — they are device-independent element-size multipliers.
    static func rescaleGamepad(
        config: GamepadConfig,
        srcW: Double, srcH: Double,
        tgtW: Double, tgtH: Double
    ) -> GamepadConfig {
        guard abs(srcW - tgtW) > 1.0 || abs(srcH - tgtH) > 1.0 else { return config }

        let srcLayout = GamepadLayoutResolver.resolveLayout(canvasW: srcW, canvasH: srcH)
        let tgtLayout = GamepadLayoutResolver.resolveLayout(canvasW: tgtW, canvasH: tgtH)
        let xRatio = tgtW / srcW
        let yRatio = tgtH / srcH

        func rescaleButton(_ key: String, _ btn: ButtonConfig) -> ButtonConfig {
            guard let src = srcLayout[key], let tgt = tgtLayout[key] else { return btn }
            let cx = (src.left + src.w / 2.0 + btn.offsetX) * xRatio
            let cy = (src.top  + src.h / 2.0 + btn.offsetY) * yRatio
            var result = btn
            result.offsetX = cx - (tgt.left + tgt.w / 2.0)
            result.offsetY = cy - (tgt.top  + tgt.h / 2.0)
            return result
        }

        func rescaleJoystick(_ key: String, _ joy: JoystickConfig) -> JoystickConfig {
            guard let src = srcLayout[key], let tgt = tgtLayout[key] else { return joy }
            let cx = (src.left + src.w / 2.0 + joy.offsetX) * xRatio
            let cy = (src.top  + src.h / 2.0 + joy.offsetY) * yRatio
            var result = joy
            result.offsetX = cx - (tgt.left + tgt.w / 2.0)
            result.offsetY = cy - (tgt.top  + tgt.h / 2.0)
            return result
        }

        var result = config
        result.btnA      = rescaleButton("a",           config.btnA)
        result.btnB      = rescaleButton("b",           config.btnB)
        result.btnX      = rescaleButton("x",           config.btnX)
        result.btnY      = rescaleButton("y",           config.btnY)
        result.btnLB     = rescaleButton("lb",          config.btnLB)
        result.btnRB     = rescaleButton("rb",          config.btnRB)
        result.btnLT     = rescaleButton("lt",          config.btnLT)
        result.btnRT     = rescaleButton("rt",          config.btnRT)
        result.btnBack   = rescaleButton("back",        config.btnBack)
        result.btnStart  = rescaleButton("start",       config.btnStart)
        result.dpadUp    = rescaleButton("dpadUp",      config.dpadUp)
        result.dpadDown  = rescaleButton("dpadDown",    config.dpadDown)
        result.dpadLeft  = rescaleButton("dpadLeft",    config.dpadLeft)
        result.dpadRight = rescaleButton("dpadRight",   config.dpadRight)
        result.leftJoystick  = rescaleJoystick("leftJoystick",  config.leftJoystick)
        result.rightJoystick = rescaleJoystick("rightJoystick", config.rightJoystick)
        result.macroButtons = config.macroButtons.map { macro in
            var m = macro
            m.layoutOffsetX = macro.layoutOffsetX * xRatio
            m.layoutOffsetY = macro.layoutOffsetY * yRatio
            return m
        }
        return result
    }
}
