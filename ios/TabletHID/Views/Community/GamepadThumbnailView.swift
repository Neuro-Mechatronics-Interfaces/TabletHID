import SwiftUI

/// Read-only preview of a gamepad layout, drawn onto a SwiftUI Canvas.
///
/// Mirrors Android's GamepadThumbnailView: reads gamepad_layout.json via GamepadLayoutResolver,
/// scales natural element rects to the view's pixel width, draws filled+stroked buttons and
/// joystick circles. The aspect ratio is always refH/refW (landscape or portrait).
struct GamepadThumbnailView: View {

    // MARK: - Configuration

    /// Resolved config to display. Mutually exclusive with configJson.
    var config: GamepadConfig?

    /// Raw canonical JSON string to display (used when importing without a local model).
    var configJson: String?

    /// True → 600×340 reference canvas (landscape). False → 340×600 (portrait).
    var isLandscape: Bool = true

    /// Optional source canvas long/short side (dp). When nil uses 600/340 defaults.
    var customCanvasLong: Double? = nil
    var customCanvasShort: Double? = nil

    // MARK: - Constants

    private static let refLong: Double = 600
    private static let refShort: Double = 340

    private static let buttonColors: [String: Color] = [
        "a": Color(red: 0x4a/255.0, green: 0xde/255.0, blue: 0x80/255.0),
        "b": Color(red: 0xf8/255.0, green: 0x71/255.0, blue: 0x71/255.0),
        "x": Color(red: 0x60/255.0, green: 0xa5/255.0, blue: 0xfa/255.0),
        "y": Color(red: 0xfb/255.0, green: 0xbf/255.0, blue: 0x24/255.0),
    ]
    private static let neutralColor = Color(red: 0xb0/255.0, green: 0xb8/255.0, blue: 0xc1/255.0)
    private static let bgColor      = Color(red: 0x0f/255.0, green: 0x0f/255.0, blue: 0x1a/255.0)

    private static let buttonKeys = [
        "a", "b", "x", "y", "lb", "rb", "lt", "rt",
        "back", "start", "dpadUp", "dpadDown", "dpadLeft", "dpadRight",
    ]

    // MARK: - Computed layout data

    private var refW: Double { isLandscape ? Self.refLong  : Self.refShort }
    private var refH: Double { isLandscape ? Self.refShort : Self.refLong  }

    private func effectiveRefW() -> Double {
        guard let l = customCanvasLong, let s = customCanvasShort else { return refW }
        return isLandscape ? l : s
    }
    private func effectiveRefH() -> Double {
        guard let l = customCanvasLong, let s = customCanvasShort else { return refH }
        return isLandscape ? s : l
    }

    // MARK: - Parsed state (computed from config/configJson)

    private struct LayoutState {
        var buttonEnabled  = [String: Bool]()
        var buttonOX       = [String: Double]()
        var buttonOY       = [String: Double]()
        var buttonSX       = [String: Double]()
        var buttonSY       = [String: Double]()
        var leftEnabled    = true
        var rightEnabled   = true
        var singleMode     = false
        var leftOX: Double = 0; var leftOY: Double = 0;  var leftSX:  Double = 1
        var rightOX: Double = 0; var rightOY: Double = 0; var rightSX: Double = 1
    }

    private var layoutState: LayoutState {
        var state = LayoutState()
        if let cfg = config {
            let buttons: [(String, ButtonConfig)] = [
                ("a", cfg.btnA), ("b", cfg.btnB), ("x", cfg.btnX), ("y", cfg.btnY),
                ("lb", cfg.btnLB), ("rb", cfg.btnRB), ("lt", cfg.btnLT), ("rt", cfg.btnRT),
                ("back", cfg.btnBack), ("start", cfg.btnStart),
                ("dpadUp", cfg.dpadUp), ("dpadDown", cfg.dpadDown),
                ("dpadLeft", cfg.dpadLeft), ("dpadRight", cfg.dpadRight),
            ]
            for (key, btn) in buttons {
                state.buttonEnabled[key] = btn.enabled
                state.buttonOX[key] = btn.offsetX
                state.buttonOY[key] = btn.offsetY
                state.buttonSX[key] = btn.scaleX
                state.buttonSY[key] = btn.scaleY
            }
            state.leftEnabled  = cfg.leftJoystick.enabled
            state.rightEnabled = cfg.rightJoystick.enabled
            state.singleMode   = cfg.singleJoystickMode
            state.leftOX  = cfg.leftJoystick.offsetX;  state.leftOY  = cfg.leftJoystick.offsetY
            state.leftSX  = cfg.leftJoystick.scaleX
            state.rightOX = cfg.rightJoystick.offsetX; state.rightOY = cfg.rightJoystick.offsetY
            state.rightSX = cfg.rightJoystick.scaleX
        } else if let jsonStr = configJson,
                  let data = jsonStr.data(using: .utf8),
                  let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        {
            let buttons = root["buttons"] as? [String: Any] ?? [:]
            for key in Self.buttonKeys {
                let btn = buttons[key] as? [String: Any]
                state.buttonEnabled[key] = btn?["enabled"] as? Bool ?? true
                state.buttonOX[key] = btn?["offsetX"] as? Double ?? 0
                state.buttonOY[key] = btn?["offsetY"] as? Double ?? 0
                state.buttonSX[key] = btn?["scaleX"]  as? Double ?? 1
                state.buttonSY[key] = btn?["scaleY"]  as? Double ?? 1
            }
            let leftJs  = root["leftJoystick"]  as? [String: Any]
            let rightJs = root["rightJoystick"] as? [String: Any]
            state.leftEnabled  = leftJs?["enabled"]  as? Bool ?? true
            state.rightEnabled = rightJs?["enabled"] as? Bool ?? true
            state.singleMode   = root["singleJoystickMode"] as? Bool ?? false
            state.leftOX  = leftJs?["offsetX"]  as? Double ?? 0
            state.leftOY  = leftJs?["offsetY"]  as? Double ?? 0
            state.leftSX  = leftJs?["scaleX"]   as? Double ?? 1
            state.rightOX = rightJs?["offsetX"] as? Double ?? 0
            state.rightOY = rightJs?["offsetY"] as? Double ?? 0
            state.rightSX = rightJs?["scaleX"]  as? Double ?? 1
        }
        return state
    }

    // MARK: - View body

    var body: some View {
        let rw = effectiveRefW()
        let rh = effectiveRefH()
        let layout = GamepadLayoutResolver.resolveLayout(canvasW: rw, canvasH: rh)
        let state  = layoutState

        GeometryReader { geo in
            let scale = geo.size.width / rw
            Canvas { ctx, size in
                // Background
                ctx.fill(
                    Path(roundedRect: CGRect(origin: .zero, size: size), cornerRadius: 12 * scale),
                    with: .color(Self.bgColor)
                )

                // Buttons
                for key in Self.buttonKeys {
                    guard state.buttonEnabled[key] != false,
                          let nat = layout[key] else { continue }
                    let ox = state.buttonOX[key] ?? 0
                    let oy = state.buttonOY[key] ?? 0
                    let sx = state.buttonSX[key] ?? 1
                    let sy = state.buttonSY[key] ?? 1
                    let cx  = (nat.left + nat.w / 2.0 + ox) * scale
                    let cy  = (nat.top  + nat.h / 2.0 + oy) * scale
                    let hw  = nat.w * sx * scale / 2.0
                    let hh  = nat.h * sy * scale / 2.0
                    let base = Self.buttonColors[key] ?? Self.neutralColor
                    let isTrigger = key == "lt" || key == "rt"
                    let isDpad    = key.hasPrefix("dpad")
                    let corner: Double = isTrigger ? 4 * scale : (isDpad ? 3 * scale : 14 * scale)
                    let rect = CGRect(x: cx - hw, y: cy - hh, width: hw * 2, height: hh * 2)
                    ctx.fill(
                        Path(roundedRect: rect, cornerRadius: corner),
                        with: .color(base.opacity(0.2))
                    )
                    ctx.stroke(
                        Path(roundedRect: rect, cornerRadius: corner),
                        with: .color(base.opacity(0.6)),
                        lineWidth: 1.5
                    )
                }

                // Left joystick
                if state.leftEnabled, let nat = layout["leftJoystick"] {
                    drawJoystick(ctx: ctx, nat: nat, ox: state.leftOX, oy: state.leftOY,
                                 sx: state.leftSX, scale: scale)
                }

                // Right joystick (hidden in single-mode)
                if state.rightEnabled && !state.singleMode, let nat = layout["rightJoystick"] {
                    drawJoystick(ctx: ctx, nat: nat, ox: state.rightOX, oy: state.rightOY,
                                 sx: state.rightSX, scale: scale)
                }
            }
        }
        .aspectRatio(rw / rh, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    // MARK: - Drawing helper

    private func drawJoystick(
        ctx: GraphicsContext,
        nat: GamepadElementRect,
        ox: Double, oy: Double, sx: Double,
        scale: Double
    ) {
        let cx     = (nat.left + nat.w / 2.0 + ox) * scale
        let cy     = (nat.top  + nat.h / 2.0 + oy) * scale
        let radius = nat.w * sx * scale / 2.0
        let outer  = Path(ellipseIn: CGRect(x: cx - radius, y: cy - radius,
                                            width: radius * 2, height: radius * 2))
        ctx.fill(outer,   with: .color(.white.opacity(0.10)))
        ctx.stroke(outer, with: .color(.white.opacity(0.38)), lineWidth: 1.5)

        let kr     = radius * 0.38
        let knob   = Path(ellipseIn: CGRect(x: cx - kr, y: cy - kr, width: kr * 2, height: kr * 2))
        ctx.fill(knob, with: .color(.white.opacity(0.75)))
    }
}
