import SwiftUI
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

struct TouchMouseView: View {
    @EnvironmentObject private var appState: AppState
    @State private var showingSettings = false
    let onExit: () -> Void

    var body: some View {
        ZStack(alignment: .top) {
            TouchMouseSurface(config: appState.touchMouseConfig) { buttons, dx, dy, wheel in
                appState.sendMouseReport(buttons: buttons, dx: dx, dy: dy, wheel: wheel)
            }
            .ignoresSafeArea()

            HStack(spacing: 10) {
                Button(action: onExit) {
                    Label("Back", systemImage: "chevron.left")
                }
                .buttonStyle(.borderedProminent)
                .lineLimit(1)

                Spacer()

                ConnectionPill(text: appState.connectionState.label, connected: appState.connectionState.isConnected)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)

                Button {
                    showingSettings = true
                } label: {
                    Image(systemName: "gearshape.fill")
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
        }
        .navigationBarBackButtonHidden()
        .sheet(isPresented: $showingSettings) {
            TouchMouseSettingsView(config: appState.touchMouseConfig) { config in
                appState.updateTouchMouseConfig(config)
            }
            #if os(iOS)
            .presentationDetents([.medium, .large])
            #else
            .frame(minWidth: 420, minHeight: 360)
            #endif
        }
    }
}

#if canImport(UIKit)
struct TouchMouseSurface: UIViewRepresentable {
    let config: TouchMouseConfig
    let sendReport: (Int, Int, Int, Int) -> Void

    func makeUIView(context: Context) -> TouchMouseSurfaceView {
        let view = TouchMouseSurfaceView()
        view.isMultipleTouchEnabled = true
        view.sendReport = sendReport
        view.config = config
        return view
    }

    func updateUIView(_ uiView: TouchMouseSurfaceView, context: Context) {
        uiView.config = config
        uiView.sendReport = sendReport
    }
}

final class TouchMouseSurfaceView: UIView {
    var config = TouchMouseConfig() { didSet { setNeedsDisplay() } }
    var sendReport: ((Int, Int, Int, Int) -> Void)?

    private var primaryTouch: UITouch?
    private var lastPoint = CGPoint.zero
    private var primaryPoint: CGPoint?
    private var rightClickTouch: UITouch?
    private var zoneTouches: [UITouch: Int] = [:]
    private var leftPointers = Set<UITouch>()
    private var rightPointers = Set<UITouch>()
    private var leftLatched = false
    private var rightLatched = false
    private var lastTapTime: TimeInterval = 0

    private let leftBit = 1
    private let rightBit = 2
    private let rightZoneFraction = 0.82

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = UIColor.systemBackground
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let event else { return }
        for touch in touches {
            let point = touch.location(in: self)
            if config.mode == .touch {
                beginTouchMode(touch, at: point)
            } else {
                beginMouseMode(touch, at: point, event: event)
            }
        }
        setNeedsDisplay()
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = primaryTouch, touches.contains(touch) else { return }
        let point = touch.location(in: self)
        let scale = config.mode == .touch ? 1.5 : Double(config.sensitivity) * 0.3
        let dx = Int((point.x - lastPoint.x) * scale).clamped(to: -32768...32767)
        let dy = Int((point.y - lastPoint.y) * scale).clamped(to: -32768...32767)
        lastPoint = point
        primaryPoint = point
        sendReport?(currentButtonBits(), dx, dy, 0)
        setNeedsDisplay()
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        endTouches(touches)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        endTouches(touches)
    }

    private func beginTouchMode(_ touch: UITouch, at point: CGPoint) {
        let threshold = bounds.height * rightZoneFraction
        if point.y >= threshold {
            rightClickTouch = touch
            sendReport?(rightBit, 0, 0, 0)
            return
        }
        primaryTouch = touch
        lastPoint = point
        primaryPoint = point

        let now = touch.timestamp
        if now - lastTapTime < 0.32 {
            let button = rightClickTouch == nil ? leftBit : rightBit
            sendReport?(button, 0, 0, 0)
            sendReport?(0, 0, 0, 0)
            sendReport?(button, 0, 0, 0)
            sendReport?(0, 0, 0, 0)
        } else {
            sendReport?(rightClickTouch == nil ? leftBit : rightBit, 0, 0, 0)
        }
        lastTapTime = now
    }

    private func beginMouseMode(_ touch: UITouch, at point: CGPoint, event: UIEvent) {
        if primaryTouch == nil {
            primaryTouch = touch
            lastPoint = point
            primaryPoint = point
            return
        }
        let zone = hitTestZone(point)
        zoneTouches[touch] = zone
        if zone != 0 { zoneDown(zone, touch: touch) }
    }

    private func endTouches(_ touches: Set<UITouch>) {
        for touch in touches {
            if touch == primaryTouch {
                primaryTouch = nil
                primaryPoint = nil
            }
            if touch == rightClickTouch {
                rightClickTouch = nil
            }
            let zone = zoneTouches.removeValue(forKey: touch) ?? 0
            if zone != 0 { zoneUp(zone, touch: touch) }
            leftPointers.remove(touch)
            rightPointers.remove(touch)
        }
        sendReport?(currentButtonBits(), 0, 0, 0)
        setNeedsDisplay()
    }

    private func zoneDown(_ zone: Int, touch: UITouch) {
        if zone == leftBit {
            if config.leftButton.behavior == .latching {
                leftLatched.toggle()
            } else {
                leftPointers.insert(touch)
            }
        } else {
            if config.rightButton.behavior == .latching {
                rightLatched.toggle()
            } else {
                rightPointers.insert(touch)
            }
        }
        sendReport?(currentButtonBits(), 0, 0, 0)
    }

    private func zoneUp(_ zone: Int, touch: UITouch) {
        if zone == leftBit {
            leftPointers.remove(touch)
        } else {
            rightPointers.remove(touch)
        }
        sendReport?(currentButtonBits(), 0, 0, 0)
    }

    private func currentButtonBits() -> Int {
        if config.mode == .touch {
            if rightClickTouch != nil { return rightBit }
            return primaryTouch == nil ? 0 : leftBit
        }

        var bits = 0
        if config.leftButton.enabled && (leftLatched || !leftPointers.isEmpty) { bits |= leftBit }
        if config.rightButton.enabled && (rightLatched || !rightPointers.isEmpty) { bits |= rightBit }
        return bits
    }

    private func hitTestZone(_ point: CGPoint) -> Int {
        if config.leftButton.enabled && contains(point, button: config.leftButton) { return leftBit }
        if config.rightButton.enabled && contains(point, button: config.rightButton) { return rightBit }
        return 0
    }

    private func contains(_ point: CGPoint, button: ButtonZoneConfig) -> Bool {
        switch button.zoneType {
        case .staticZone:
            return staticRect(button).contains(point)
        case .dynamic:
            guard let primaryPoint else { return false }
            let circle = dynamicCircle(button, primary: primaryPoint)
            let dx = point.x - circle.center.x
            let dy = point.y - circle.center.y
            return dx * dx + dy * dy <= circle.radius * circle.radius
        }
    }

    private func staticRect(_ button: ButtonZoneConfig) -> CGRect {
        CGRect(
            x: bounds.width * button.staticLeft,
            y: bounds.height * button.staticTop,
            width: bounds.width * (button.staticRight - button.staticLeft),
            height: bounds.height * (button.staticBottom - button.staticTop)
        )
    }

    private func dynamicCircle(_ button: ButtonZoneConfig, primary: CGPoint) -> (center: CGPoint, radius: CGFloat) {
        let minDim = min(bounds.width, bounds.height)
        return (
            CGPoint(x: primary.x + minDim * button.dynamicOffsetX, y: primary.y + minDim * button.dynamicOffsetY),
            minDim * button.dynamicRadius
        )
    }

    override func draw(_ rect: CGRect) {
        UIColor.systemBackground.setFill()
        UIRectFill(bounds)
        if config.mode == .touch {
            drawTouchModeZone()
        } else {
            if config.leftButton.enabled { drawZone(config.leftButton, label: "L", color: .systemBlue, active: leftLatched || !leftPointers.isEmpty) }
            if config.rightButton.enabled { drawZone(config.rightButton, label: "R", color: .systemOrange, active: rightLatched || !rightPointers.isEmpty) }
        }
    }

    private func drawTouchModeZone() {
        let zone = CGRect(x: 0, y: bounds.height * rightZoneFraction, width: bounds.width, height: bounds.height * (1 - rightZoneFraction))
        UIColor.systemRed.withAlphaComponent(rightClickTouch == nil ? 0.10 : 0.28).setFill()
        UIBezierPath(rect: zone).fill()
        drawLabel("RIGHT CLICK ZONE", in: zone, color: UIColor.label.withAlphaComponent(0.45))
    }

    private func drawZone(_ button: ButtonZoneConfig, label: String, color: UIColor, active: Bool) {
        if button.zoneType == .dynamic, primaryPoint == nil { return }
        let path: UIBezierPath
        let labelRect: CGRect
        if button.zoneType == .staticZone {
            let rect = staticRect(button)
            path = UIBezierPath(roundedRect: rect, cornerRadius: 16)
            labelRect = rect
        } else {
            let circle = dynamicCircle(button, primary: primaryPoint ?? .zero)
            let rect = CGRect(x: circle.center.x - circle.radius, y: circle.center.y - circle.radius, width: circle.radius * 2, height: circle.radius * 2)
            path = UIBezierPath(ovalIn: rect)
            labelRect = rect
        }
        color.withAlphaComponent(active ? 0.78 : 0.34).setFill()
        path.fill()
        UIColor.white.withAlphaComponent(0.85).setStroke()
        path.lineWidth = 2
        path.stroke()
        drawLabel(label, in: labelRect, color: .white)
    }

    private func drawLabel(_ label: String, in rect: CGRect, color: UIColor) {
        let font = UIFont.boldSystemFont(ofSize: min(max(rect.height * 0.28, 12), 28))
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let size = label.size(withAttributes: attributes)
        let origin = CGPoint(x: rect.midX - size.width / 2, y: rect.midY - size.height / 2)
        label.draw(at: origin, withAttributes: attributes)
    }
}
#elseif canImport(AppKit)
struct TouchMouseSurface: NSViewRepresentable {
    let config: TouchMouseConfig
    let sendReport: (Int, Int, Int, Int) -> Void

    func makeNSView(context: Context) -> TouchMouseSurfaceView {
        let view = TouchMouseSurfaceView()
        view.sendReport = sendReport
        view.config = config
        return view
    }

    func updateNSView(_ nsView: TouchMouseSurfaceView, context: Context) {
        nsView.config = config
        nsView.sendReport = sendReport
    }
}

final class TouchMouseSurfaceView: NSView {
    var config = TouchMouseConfig() { didSet { needsDisplay = true } }
    var sendReport: ((Int, Int, Int, Int) -> Void)?

    private var lastPoint = CGPoint.zero
    private var primaryDown = false
    private var rightDown = false
    private var leftLatched = false
    private var rightLatched = false
    private var lastTapTime: TimeInterval = 0

    private let leftBit = 1
    private let rightBit = 2
    private let rightZoneFraction = 0.82

    override var acceptsFirstResponder: Bool { true }
    override var isFlipped: Bool { true }

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        layer?.backgroundColor = NSColor.windowBackgroundColor.cgColor
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func mouseDown(with event: NSEvent) {
        let point = convert(event.locationInWindow, from: nil)
        if config.mode == .touch {
            beginTouchMode(at: point, timestamp: event.timestamp)
        } else {
            beginMouseMode(at: point)
        }
        needsDisplay = true
    }

    override func mouseDragged(with event: NSEvent) {
        guard primaryDown else { return }
        let point = convert(event.locationInWindow, from: nil)
        let scale = config.mode == .touch ? 1.5 : Double(config.sensitivity) * 0.3
        let dx = Int((point.x - lastPoint.x) * scale).clamped(to: -32768...32767)
        let dy = Int((point.y - lastPoint.y) * scale).clamped(to: -32768...32767)
        lastPoint = point
        sendReport?(currentButtonBits(), dx, dy, 0)
        needsDisplay = true
    }

    override func mouseUp(with event: NSEvent) {
        primaryDown = false
        sendReport?(currentButtonBits(), 0, 0, 0)
        needsDisplay = true
    }

    override func rightMouseDown(with event: NSEvent) {
        rightDown = true
        sendReport?(currentButtonBits(), 0, 0, 0)
        needsDisplay = true
    }

    override func rightMouseUp(with event: NSEvent) {
        rightDown = false
        sendReport?(currentButtonBits(), 0, 0, 0)
        needsDisplay = true
    }

    override func scrollWheel(with event: NSEvent) {
        let wheel = Int(event.scrollingDeltaY).clamped(to: -127...127)
        if wheel != 0 {
            sendReport?(currentButtonBits(), 0, 0, wheel)
        }
    }

    private func beginTouchMode(at point: CGPoint, timestamp: TimeInterval) {
        let threshold = bounds.height * rightZoneFraction
        if point.y >= threshold {
            rightDown = true
            sendReport?(rightBit, 0, 0, 0)
            return
        }

        primaryDown = true
        lastPoint = point

        if timestamp - lastTapTime < 0.32 {
            sendReport?(leftBit, 0, 0, 0)
            sendReport?(0, 0, 0, 0)
            sendReport?(leftBit, 0, 0, 0)
            sendReport?(0, 0, 0, 0)
        } else {
            sendReport?(leftBit, 0, 0, 0)
        }
        lastTapTime = timestamp
    }

    private func beginMouseMode(at point: CGPoint) {
        let zone = hitTestZone(point)
        if zone == leftBit {
            if config.leftButton.behavior == .latching {
                leftLatched.toggle()
            } else {
                primaryDown = true
            }
            sendReport?(currentButtonBits(), 0, 0, 0)
        } else if zone == rightBit {
            if config.rightButton.behavior == .latching {
                rightLatched.toggle()
            } else {
                rightDown = true
            }
            sendReport?(currentButtonBits(), 0, 0, 0)
        } else {
            primaryDown = true
            lastPoint = point
        }
    }

    private func currentButtonBits() -> Int {
        if config.mode == .touch {
            if rightDown { return rightBit }
            return primaryDown ? leftBit : 0
        }

        var bits = 0
        if config.leftButton.enabled && (leftLatched || primaryDown) { bits |= leftBit }
        if config.rightButton.enabled && (rightLatched || rightDown) { bits |= rightBit }
        return bits
    }

    private func hitTestZone(_ point: CGPoint) -> Int {
        if config.leftButton.enabled && contains(point, button: config.leftButton) { return leftBit }
        if config.rightButton.enabled && contains(point, button: config.rightButton) { return rightBit }
        return 0
    }

    private func contains(_ point: CGPoint, button: ButtonZoneConfig) -> Bool {
        guard button.zoneType == .staticZone else { return false }
        return staticRect(button).contains(point)
    }

    private func staticRect(_ button: ButtonZoneConfig) -> CGRect {
        CGRect(
            x: bounds.width * button.staticLeft,
            y: bounds.height * button.staticTop,
            width: bounds.width * (button.staticRight - button.staticLeft),
            height: bounds.height * (button.staticBottom - button.staticTop)
        )
    }

    override func draw(_ dirtyRect: NSRect) {
        NSColor.windowBackgroundColor.setFill()
        bounds.fill()
        if config.mode == .touch {
            drawTouchModeZone()
        } else {
            if config.leftButton.enabled { drawZone(config.leftButton, label: "L", color: .systemBlue, active: leftLatched || primaryDown) }
            if config.rightButton.enabled { drawZone(config.rightButton, label: "R", color: .systemOrange, active: rightLatched || rightDown) }
        }
    }

    private func drawTouchModeZone() {
        let zone = CGRect(x: 0, y: bounds.height * rightZoneFraction, width: bounds.width, height: bounds.height * (1 - rightZoneFraction))
        NSColor.systemRed.withAlphaComponent(rightDown ? 0.28 : 0.10).setFill()
        NSBezierPath(rect: zone).fill()
        drawLabel("RIGHT CLICK ZONE", in: zone, color: NSColor.labelColor.withAlphaComponent(0.45))
    }

    private func drawZone(_ button: ButtonZoneConfig, label: String, color: NSColor, active: Bool) {
        guard button.zoneType == .staticZone else { return }
        let rect = staticRect(button)
        let path = NSBezierPath(roundedRect: rect, xRadius: 16, yRadius: 16)
        color.withAlphaComponent(active ? 0.78 : 0.34).setFill()
        path.fill()
        NSColor.white.withAlphaComponent(0.85).setStroke()
        path.lineWidth = 2
        path.stroke()
        drawLabel(label, in: rect, color: .white)
    }

    private func drawLabel(_ label: String, in rect: CGRect, color: NSColor) {
        let font = NSFont.boldSystemFont(ofSize: min(max(rect.height * 0.28, 12), 28))
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let size = label.size(withAttributes: attributes)
        let origin = CGPoint(x: rect.midX - size.width / 2, y: rect.midY - size.height / 2)
        label.draw(at: origin, withAttributes: attributes)
    }
}
#endif

private extension Int {
    func clamped(to range: ClosedRange<Int>) -> Int {
        Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
    }
}
