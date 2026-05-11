import SwiftUI
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

enum CalibrationPhase: Equatable {
    case none, waitingPrimary, waitingLeft, waitingRight
}

struct TouchMouseView: View {
    @EnvironmentObject private var appState: AppState
    @State private var showingSettings = false
    @State private var calibrationPhase: CalibrationPhase = .none
    @State private var showingShortcutPanel = false
    let onExit: () -> Void

    var body: some View {
        ZStack(alignment: .top) {
            TouchMouseSurface(config: appState.touchMouseConfig) { buttons, dx, dy, wheel, horizontalWheel in
                appState.sendMouseReport(
                    buttons: buttons,
                    dx: dx,
                    dy: dy,
                    wheel: wheel,
                    horizontalWheel: horizontalWheel
                )
            }
            .ignoresSafeArea()

            #if canImport(UIKit)
            if calibrationPhase != .none {
                CalibrationSurface(phase: $calibrationPhase) { primary, left, right, size in
                    applyCalibration(primary: primary, left: left, right: right, size: size)
                }
                .ignoresSafeArea()
            }
            #endif

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

                #if canImport(UIKit)
                Button {
                    appState.setOrientationLock(appState.orientationLock.next)
                } label: {
                    Image(systemName: appState.orientationLock.symbolName)
                }
                .buttonStyle(.bordered)
                #endif

                if !appState.touchMouseConfig.macroButtons.isEmpty {
                    Button {
                        showingShortcutPanel.toggle()
                    } label: {
                        Image(systemName: "keyboard")
                    }
                    .buttonStyle(.bordered)
                }

                Button {
                    showingSettings = true
                } label: {
                    Image(systemName: "gearshape.fill")
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()

            if calibrationPhase != .none {
                VStack {
                    Spacer()
                    VStack(spacing: 12) {
                        Text(calibrationInstruction)
                            .foregroundColor(.white)
                            .multilineTextAlignment(.center)
                            .font(.body.weight(.medium))
                        Button("Cancel Calibration") {
                            calibrationPhase = .none
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                    }
                    .padding(20)
                    .background(Color.black.opacity(0.78))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .padding(.horizontal, 24)
                    .padding(.bottom, 48)
                }
            }

            if showingShortcutPanel {
                HStack {
                    Spacer()
                    KeyboardMacroPanel(macros: appState.touchMouseConfig.macroButtons) { macro, pressed in
                        if pressed {
                            appState.sendKeyboardReport(modifiers: macro.modifiers, keyUsages: macro.keyUsages)
                        } else {
                            appState.sendKeyboardReport()
                        }
                    }
                    .padding(.top, 72)
                    .padding(.trailing, 16)
                }
            }
        }
        .navigationBarBackButtonHidden()
        .onChange(of: appState.touchMouseConfig.macroButtons) { _, macros in
            if macros.isEmpty { showingShortcutPanel = false }
        }
        .sheet(isPresented: $showingSettings) {
            TouchMouseSettingsView(
                config: appState.touchMouseConfig,
                onSave: { config in appState.updateTouchMouseConfig(config) },
                onCalibrateRequested: {
                    showingSettings = false
                    calibrationPhase = .waitingPrimary
                }
            )
            #if os(iOS)
            .presentationDetents([.medium, .large])
            #else
            .frame(minWidth: 420, minHeight: 360)
            #endif
        }
    }

    private var calibrationInstruction: String {
        switch calibrationPhase {
        case .none: return ""
        case .waitingPrimary: return "Step 1 of 3\nPlace your pointer finger anywhere on the screen"
        case .waitingLeft:    return "Step 2 of 3\nKeep pointer finger down — now place your left-click finger where it feels comfortable"
        case .waitingRight:   return "Step 3 of 3\nKeep both fingers down — now place your right-click finger where it feels comfortable"
        }
    }

    #if canImport(UIKit)
    private func applyCalibration(primary: CGPoint, left: CGPoint, right: CGPoint, size: CGSize) {
        let minDim = min(size.width, size.height)
        guard minDim > 0 else { return }

        func derive(_ point: CGPoint) -> (ox: Double, oy: Double, r: Double) {
            let dx = point.x - primary.x
            let dy = point.y - primary.y
            let ox = max(-1, min(1, Double(dx / minDim)))
            let oy = max(-1, min(1, Double(dy / minDim)))
            let dist = sqrt(Double(dx * dx + dy * dy))
            let r = max(0.04, min(0.15, dist * 0.45 / Double(minDim)))
            return (ox, oy, r)
        }

        let l = derive(left)
        let r = derive(right)

        var config = appState.touchMouseConfig
        config.leftButton.enabled = true
        config.leftButton.zoneType = .dynamic
        config.leftButton.dynamicOffsetX = l.ox.snapped(to: 0.05).clamped(to: -1...1)
        config.leftButton.dynamicOffsetY = l.oy.snapped(to: 0.05).clamped(to: -1...1)
        config.leftButton.dynamicRadius = l.r.snapped(to: 0.01).clamped(to: 0.03...0.2)
        config.rightButton.enabled = true
        config.rightButton.zoneType = .dynamic
        config.rightButton.dynamicOffsetX = r.ox.snapped(to: 0.05).clamped(to: -1...1)
        config.rightButton.dynamicOffsetY = r.oy.snapped(to: 0.05).clamped(to: -1...1)
        config.rightButton.dynamicRadius = r.r.snapped(to: 0.01).clamped(to: 0.03...0.2)
        appState.updateTouchMouseConfig(config)
        calibrationPhase = .none
    }
    #endif
}

#if canImport(UIKit)
struct TouchMouseSurface: UIViewRepresentable {
    let config: TouchMouseConfig
    let sendReport: (Int, Int, Int, Int, Int) -> Void

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
    var sendReport: ((Int, Int, Int, Int, Int) -> Void)?

    private var primaryTouch: UITouch?
    private var lastPoint = CGPoint.zero
    private var accumDx = 0.0
    private var accumDy = 0.0
    private var primaryPoint: CGPoint?
    private var rightClickTouch: UITouch?
    private var zoneTouches: [UITouch: Int] = [:]
    private var leftPointers = Set<UITouch>()
    private var rightPointers = Set<UITouch>()
    private var leftLatched = false
    private var rightLatched = false
    private var lastTapTime: TimeInterval = 0
    private var threeFingerScrolling = false
    private var scrollCarryV = 0.0
    private var scrollCarryH = 0.0
    private var scrollLastPoint = CGPoint.zero

    private let leftBit = 1
    private let rightBit = 2
    private let rightZoneFraction = 0.82
    private let scrollPixelsPerTick = 50.0

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = UIColor.systemBackground
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let event else { return }
        if shouldStartThreeFingerScroll(event) {
            startThreeFingerScroll(event)
            return
        }
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
        if threeFingerScrolling {
            if let event { handleScrollMove(event) }
            return
        }
        guard let touch = primaryTouch, touches.contains(touch) else { return }
        let scale = config.mode == .touch ? 1.5 : Double(config.sensitivity) * 0.3
        let bits = currentButtonBits()
        for t in event?.coalescedTouches(for: touch) ?? [touch] {
            let point = t.location(in: self)
            let rawDx = (point.x - lastPoint.x) * scale + accumDx
            let rawDy = (point.y - lastPoint.y) * scale + accumDy
            let dx = Int(rawDx).clamped(to: -32768...32767)
            let dy = Int(rawDy).clamped(to: -32768...32767)
            accumDx = rawDx - Double(dx)
            accumDy = rawDy - Double(dy)
            lastPoint = point
            if dx != 0 || dy != 0 { sendReport?(bits, dx, dy, 0, 0) }
        }
        primaryPoint = touch.location(in: self)
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
            sendReport?(rightBit, 0, 0, 0, 0)
            return
        }
        primaryTouch = touch
        lastPoint = point
        primaryPoint = point
        accumDx = 0; accumDy = 0

        let now = touch.timestamp
        if now - lastTapTime < 0.32 {
            let button = rightClickTouch == nil ? leftBit : rightBit
            sendReport?(button, 0, 0, 0, 0)
            sendReport?(0, 0, 0, 0, 0)
            sendReport?(button, 0, 0, 0, 0)
            sendReport?(0, 0, 0, 0, 0)
        } else {
            sendReport?(rightClickTouch == nil ? leftBit : rightBit, 0, 0, 0, 0)
        }
        lastTapTime = now
    }

    private func beginMouseMode(_ touch: UITouch, at point: CGPoint, event: UIEvent) {
        let zone = hitTestZone(point)
        if zone != 0 {
            zoneTouches[touch] = zone
            zoneDown(zone, touch: touch)
        } else if primaryTouch == nil {
            primaryTouch = touch
            lastPoint = point
            primaryPoint = point
            accumDx = 0; accumDy = 0
            zoneTouches[touch] = 0
        } else {
            zoneTouches[touch] = 0
        }
    }

    private func endTouches(_ touches: Set<UITouch>) {
        if threeFingerScrolling {
            stopThreeFingerScroll()
            return
        }
        for touch in touches {
            if touch == primaryTouch {
                primaryTouch = nil
                primaryPoint = nil
                accumDx = 0; accumDy = 0
            }
            if touch == rightClickTouch {
                rightClickTouch = nil
            }
            let zone = zoneTouches.removeValue(forKey: touch) ?? 0
            if zone != 0 { zoneUp(zone, touch: touch) }
            leftPointers.remove(touch)
            rightPointers.remove(touch)
        }
        sendReport?(currentButtonBits(), 0, 0, 0, 0)
        setNeedsDisplay()
    }

    private func zoneDown(_ zone: Int, touch: UITouch) {
        if zone & leftBit != 0 {
            if config.leftButton.behavior == .latching {
                leftLatched.toggle()
            } else {
                leftPointers.insert(touch)
            }
        }
        if zone & rightBit != 0 {
            if config.rightButton.behavior == .latching {
                rightLatched.toggle()
            } else {
                rightPointers.insert(touch)
            }
        }
        sendReport?(currentButtonBits(), 0, 0, 0, 0)
    }

    private func zoneUp(_ zone: Int, touch: UITouch) {
        if zone & leftBit != 0 {
            leftPointers.remove(touch)
        }
        if zone & rightBit != 0 {
            rightPointers.remove(touch)
        }
        sendReport?(currentButtonBits(), 0, 0, 0, 0)
    }

    private func shouldStartThreeFingerScroll(_ event: UIEvent) -> Bool {
        guard config.scrollEnabled,
              event.allTouches?.filter({ $0.phase != .ended && $0.phase != .cancelled }).count == 3 else {
            return false
        }
        return !threeFingerScrolling
    }

    private func startThreeFingerScroll(_ event: UIEvent) {
        threeFingerScrolling = true
        scrollCarryV = 0
        scrollCarryH = 0
        scrollLastPoint = scrollCentroid(event) ?? .zero
        primaryTouch = nil
        rightClickTouch = nil
        zoneTouches.removeAll()
        leftPointers.removeAll()
        rightPointers.removeAll()
        primaryPoint = nil
        accumDx = 0
        accumDy = 0
        sendReport?(0, 0, 0, 0, 0)
        setNeedsDisplay()
    }

    private func stopThreeFingerScroll() {
        threeFingerScrolling = false
        scrollCarryV = 0
        scrollCarryH = 0
        sendReport?(currentButtonBits(), 0, 0, 0, 0)
        setNeedsDisplay()
    }

    private func handleScrollMove(_ event: UIEvent) {
        guard let point = scrollCentroid(event) else { return }
        let invert = config.invertScroll
        let vSign = invert ? 1.0 : -1.0
        let hSign = invert ? -1.0 : 1.0
        scrollCarryV += vSign * Double(point.y - scrollLastPoint.y) / scrollPixelsPerTick
        scrollCarryH += hSign * Double(point.x - scrollLastPoint.x) / scrollPixelsPerTick
        scrollLastPoint = point

        let vTicks = Int(scrollCarryV).clamped(to: -127...127)
        let hTicks = Int(scrollCarryH).clamped(to: -127...127)
        if vTicks != 0 { scrollCarryV -= Double(vTicks) }
        if hTicks != 0 { scrollCarryH -= Double(hTicks) }
        if vTicks != 0 || hTicks != 0 {
            sendReport?(0, 0, 0, vTicks, hTicks)
        }
    }

    private func scrollCentroid(_ event: UIEvent) -> CGPoint? {
        let touches = event.allTouches?.filter { $0.phase != .ended && $0.phase != .cancelled } ?? []
        guard touches.count >= 3 else { return nil }
        let firstThree = touches.prefix(3)
        let sum = firstThree.reduce(CGPoint.zero) { partial, touch in
            let point = touch.location(in: self)
            return CGPoint(x: partial.x + point.x, y: partial.y + point.y)
        }
        return CGPoint(x: sum.x / 3, y: sum.y / 3)
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
        var bits = 0
        if config.leftButton.enabled && contains(point, button: config.leftButton) { bits |= leftBit }
        if config.rightButton.enabled && contains(point, button: config.rightButton) { bits |= rightBit }
        return bits
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
        if config.sharedDynamicZone {
            return (
                CGPoint(
                    x: primary.x + minDim * config.sharedDynamicOffsetX,
                    y: primary.y + minDim * config.sharedDynamicOffsetY
                ),
                minDim * config.sharedDynamicRadius
            )
        }
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

// MARK: - Calibration surface (UIKit)

final class CalibrationSurfaceView: UIView {
    var onPhaseChanged: ((CalibrationPhase) -> Void)?
    var onComplete: ((CGPoint, CGPoint, CGPoint, CGSize) -> Void)?

    private var calPhase: CalibrationPhase = .waitingPrimary
    private var primaryTouch: UITouch?
    private var leftTouch: UITouch?
    private var primaryPoint = CGPoint.zero
    private var leftPoint = CGPoint.zero

    override init(frame: CGRect) {
        super.init(frame: frame)
        isMultipleTouchEnabled = true
        backgroundColor = .clear
    }

    required init?(coder: NSCoder) { fatalError() }

    func reset() {
        calPhase = .waitingPrimary
        primaryTouch = nil
        leftTouch = nil
        primaryPoint = .zero
        leftPoint = .zero
        setNeedsDisplay()
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches {
            switch calPhase {
            case .waitingPrimary:
                primaryTouch = touch
                primaryPoint = touch.location(in: self)
                calPhase = .waitingLeft
                setNeedsDisplay()
                DispatchQueue.main.async { self.onPhaseChanged?(.waitingLeft) }
            case .waitingLeft:
                guard touch != primaryTouch else { continue }
                leftTouch = touch
                leftPoint = touch.location(in: self)
                calPhase = .waitingRight
                setNeedsDisplay()
                DispatchQueue.main.async { self.onPhaseChanged?(.waitingRight) }
            case .waitingRight:
                guard touch != primaryTouch, touch != leftTouch else { continue }
                let rightPoint = touch.location(in: self)
                DispatchQueue.main.async {
                    self.onComplete?(self.primaryPoint, self.leftPoint, rightPoint, self.bounds.size)
                }
            case .none: break
            }
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { handleLift(touches) }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { handleLift(touches) }

    private func handleLift(_ touches: Set<UITouch>) {
        guard calPhase == .waitingLeft || calPhase == .waitingRight else { return }
        let liftedKey = (primaryTouch != nil && touches.contains(primaryTouch!))
                     || (leftTouch != nil && touches.contains(leftTouch!))
        if liftedKey {
            reset()
            DispatchQueue.main.async { self.onPhaseChanged?(.waitingPrimary) }
        }
    }

    override func draw(_ rect: CGRect) {
        guard calPhase != .waitingPrimary else { return }
        drawDot(at: primaryPoint, color: UIColor.systemYellow)
        if calPhase == .waitingRight { drawDot(at: leftPoint, color: UIColor.systemBlue) }
    }

    private func drawDot(at point: CGPoint, color: UIColor) {
        let r: CGFloat = 24
        let path = UIBezierPath(ovalIn: CGRect(x: point.x - r, y: point.y - r, width: r * 2, height: r * 2))
        color.withAlphaComponent(0.85).setFill()
        path.fill()
        UIColor.white.withAlphaComponent(0.6).setStroke()
        path.lineWidth = 2
        path.stroke()
    }
}

struct CalibrationSurface: UIViewRepresentable {
    @Binding var phase: CalibrationPhase
    let onComplete: (CGPoint, CGPoint, CGPoint, CGSize) -> Void

    class Coordinator {
        var phase: Binding<CalibrationPhase>
        var onComplete: (CGPoint, CGPoint, CGPoint, CGSize) -> Void
        init(phase: Binding<CalibrationPhase>, onComplete: @escaping (CGPoint, CGPoint, CGPoint, CGSize) -> Void) {
            self.phase = phase
            self.onComplete = onComplete
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(phase: $phase, onComplete: onComplete)
    }

    func makeUIView(context: Context) -> CalibrationSurfaceView {
        let coordinator = context.coordinator
        let view = CalibrationSurfaceView()
        view.onPhaseChanged = { newPhase in coordinator.phase.wrappedValue = newPhase }
        view.onComplete = { p, l, r, size in coordinator.onComplete(p, l, r, size) }
        return view
    }

    func updateUIView(_ uiView: CalibrationSurfaceView, context: Context) {
        context.coordinator.phase = $phase
        context.coordinator.onComplete = onComplete
        if phase == .waitingPrimary { uiView.reset() }
    }
}

#elseif canImport(AppKit)
struct TouchMouseSurface: NSViewRepresentable {
    let config: TouchMouseConfig
    let sendReport: (Int, Int, Int, Int, Int) -> Void

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
    var sendReport: ((Int, Int, Int, Int, Int) -> Void)?

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
        sendReport?(currentButtonBits(), dx, dy, 0, 0)
        needsDisplay = true
    }

    override func mouseUp(with event: NSEvent) {
        primaryDown = false
        sendReport?(currentButtonBits(), 0, 0, 0, 0)
        needsDisplay = true
    }

    override func rightMouseDown(with event: NSEvent) {
        rightDown = true
        sendReport?(currentButtonBits(), 0, 0, 0, 0)
        needsDisplay = true
    }

    override func rightMouseUp(with event: NSEvent) {
        rightDown = false
        sendReport?(currentButtonBits(), 0, 0, 0, 0)
        needsDisplay = true
    }

    override func scrollWheel(with event: NSEvent) {
        let wheel = Int(event.scrollingDeltaY).clamped(to: -127...127)
        let horizontalWheel = Int(event.scrollingDeltaX).clamped(to: -127...127)
        if wheel != 0 || horizontalWheel != 0 {
            sendReport?(currentButtonBits(), 0, 0, wheel, horizontalWheel)
        }
    }

    private func beginTouchMode(at point: CGPoint, timestamp: TimeInterval) {
        let threshold = bounds.height * rightZoneFraction
        if point.y >= threshold {
            rightDown = true
            sendReport?(rightBit, 0, 0, 0, 0)
            return
        }

        primaryDown = true
        lastPoint = point

        if timestamp - lastTapTime < 0.32 {
            sendReport?(leftBit, 0, 0, 0, 0)
            sendReport?(0, 0, 0, 0, 0)
            sendReport?(leftBit, 0, 0, 0, 0)
            sendReport?(0, 0, 0, 0, 0)
        } else {
            sendReport?(leftBit, 0, 0, 0, 0)
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
            sendReport?(currentButtonBits(), 0, 0, 0, 0)
        } else if zone == rightBit {
            if config.rightButton.behavior == .latching {
                rightLatched.toggle()
            } else {
                rightDown = true
            }
            sendReport?(currentButtonBits(), 0, 0, 0, 0)
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
