import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

struct GamepadView: View {
    @EnvironmentObject private var appState: AppState
    @State private var leftStick = CGPoint.zero
    @State private var rightStick = CGPoint.zero
    @State private var leftTrigger = 0
    @State private var rightTrigger = 0
    @State private var buttonBits = 0
    @State private var dUp = false
    @State private var dDown = false
    @State private var dLeft = false
    @State private var dRight = false
    @State private var showSettings = false
    @State private var isEditingLayout = false
    @State private var dragStartOffsets: [GamepadLayoutItem: CGSize] = [:]
    @State private var scaleStartValues: [GamepadLayoutItem: CGSize] = [:]
    let onExit: () -> Void

    private var cfg: GamepadConfig { appState.gamepadConfig }

    var body: some View {
        GeometryReader { proxy in
            let isPortrait = proxy.size.height > proxy.size.width
            let metrics = GamepadMetrics(size: proxy.size, isPortrait: isPortrait)

            ZStack {
                appBackgroundColor.ignoresSafeArea()

                if isPortrait {
                    ScrollView {
                        VStack(spacing: metrics.spacing) {
                            topBar(metrics)
                            portraitControls(metrics)
                        }
                        .padding(metrics.padding)
                    }
                } else {
                    VStack(spacing: metrics.spacing) {
                        topBar(metrics)
                        Spacer(minLength: metrics.spacing)
                        landscapeControls(metrics)
                            .padding(.horizontal, max(24, proxy.size.width * 0.04))
                            .padding(.bottom, 28)
                    }
                    .padding(.top, metrics.padding)
                }

                if !cfg.macroButtons.isEmpty {
                    VStack {
                        Spacer()
                        HStack {
                            Spacer()
                            KeyboardMacroPanel(macros: cfg.macroButtons) { macro, pressed in
                                if pressed {
                                    appState.sendKeyboardReport(modifiers: macro.modifiers, keyUsages: macro.keyUsages)
                                } else {
                                    appState.sendKeyboardReport()
                                }
                            }
                            .padding(.trailing, metrics.padding)
                            .padding(.bottom, metrics.padding)
                        }
                    }
                }
            }
        }
        .navigationBarBackButtonHidden()
        .sheet(isPresented: $showSettings) {
            GamepadSettingsView(config: appState.gamepadConfig) { newConfig in
                appState.updateGamepadConfig(newConfig)
            }
        }
    }

    private var appBackgroundColor: Color {
        #if canImport(UIKit)
        Color(.systemBackground)
        #elseif canImport(AppKit)
        Color(nsColor: .windowBackgroundColor)
        #else
        Color.white
        #endif
    }

    private func topBar(_ metrics: GamepadMetrics) -> some View {
        Group {
            if metrics.isPortrait {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        backButton
                        Spacer()
                        #if canImport(UIKit)
                        orientationButton
                        #endif
                        layoutEditButton
                        settingsButton
                        profileLabel
                    }
                    ConnectionPill(text: appState.connectionState.label, connected: appState.connectionState.isConnected)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                }
            } else {
                HStack {
                    backButton
                    ConnectionPill(text: appState.connectionState.label, connected: appState.connectionState.isConnected)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                    Spacer()
                    #if canImport(UIKit)
                    orientationButton
                    #endif
                    layoutEditButton
                    settingsButton
                    profileLabel
                }
            }
        }
        .font(metrics.topBarFont)
    }

    private var backButton: some View {
        Button(action: onExit) {
            Label("Back", systemImage: "chevron.left")
        }
        .buttonStyle(.borderedProminent)
    }

    private var settingsButton: some View {
        Button { showSettings = true } label: {
            Image(systemName: "gearshape")
        }
        .buttonStyle(.bordered)
    }

    private var layoutEditButton: some View {
        Button {
            isEditingLayout.toggle()
            if !isEditingLayout {
                dragStartOffsets.removeAll()
                scaleStartValues.removeAll()
            }
        } label: {
            Image(systemName: isEditingLayout ? "checkmark" : "pencil.and.outline")
        }
        .buttonStyle(.bordered)
        .tint(isEditingLayout ? Color.accentColor : nil)
    }

    #if canImport(UIKit)
    private var orientationButton: some View {
        Button {
            appState.setOrientationLock(appState.orientationLock.next)
        } label: {
            Image(systemName: appState.orientationLock.symbolName)
        }
        .buttonStyle(.bordered)
    }
    #endif

    private var profileLabel: some View {
        Text(appState.activeProfile.name)
            .foregroundStyle(.secondary)
            .lineLimit(1)
            .minimumScaleFactor(0.75)
    }

    private func landscapeControls(_ metrics: GamepadMetrics) -> some View {
        HStack(alignment: .bottom, spacing: metrics.spacing) {
            leftControls(metrics)
            Spacer(minLength: metrics.spacing)
            centerControls(metrics)
            Spacer(minLength: metrics.spacing)
            rightControls(metrics)
        }
    }

    private func portraitControls(_ metrics: GamepadMetrics) -> some View {
        VStack(spacing: metrics.spacing) {
            HStack(spacing: metrics.spacing) {
                shoulderControls(metrics, side: .left)
                Spacer(minLength: metrics.spacing)
                shoulderControls(metrics, side: .right)
            }

            HStack(alignment: .center, spacing: metrics.spacing) {
                VStack(spacing: metrics.spacing) {
                    visibleJoystick(metrics)
                    singleJoystickToggle(metrics)
                    dpad(metrics)
                }

                Spacer(minLength: metrics.spacing)

                VStack(spacing: metrics.spacing) {
                    faceButtons(metrics)
                    if !cfg.singleJoystickMode {
                        layoutEditable(item: .rightJoystick, offsetX: cfg.rightJoystick.offsetX, offsetY: cfg.rightJoystick.offsetY, scaleX: cfg.rightJoystick.scaleX, scaleY: cfg.rightJoystick.scaleY) {
                            Joystick(label: "R", size: metrics.joystickSize, config: cfg.rightJoystick) { x, y in
                                guard !isEditingLayout else { return }
                                rightStick = CGPoint(x: x, y: y)
                                sendReport()
                            }
                        }
                    }
                }
            }

            centerControls(metrics)
        }
    }

    private func leftControls(_ metrics: GamepadMetrics) -> some View {
        VStack(spacing: metrics.sectionSpacing) {
            shoulderControls(metrics, side: .left)
            visibleJoystick(metrics)
            singleJoystickToggle(metrics)
            dpad(metrics)
        }
    }

    private func visibleJoystick(_ metrics: GamepadMetrics) -> some View {
        let side = cfg.singleJoystickMode ? cfg.singleJoystickOutputSide : .left
        let label = side == .left ? "L" : "R"
        return layoutEditable(item: .leftJoystick, offsetX: cfg.leftJoystick.offsetX, offsetY: cfg.leftJoystick.offsetY, scaleX: cfg.leftJoystick.scaleX, scaleY: cfg.leftJoystick.scaleY) {
            Joystick(label: label, size: metrics.joystickSize, config: cfg.leftJoystick) { x, y in
                guard !isEditingLayout else { return }
                        leftStick = CGPoint(x: x, y: y)
                        sendReport()
            }
        }
    }

    @ViewBuilder
    private func singleJoystickToggle(_ metrics: GamepadMetrics) -> some View {
        if cfg.singleJoystickMode && cfg.singleJoystickSideToggleEnabled && cfg.leftJoystick.enabled {
            Button {
                var next = cfg
                next.singleJoystickOutputSide = cfg.singleJoystickOutputSide == .left ? .right : .left
                appState.updateGamepadConfig(next)
                sendReport()
            } label: {
                Text(cfg.singleJoystickOutputSide == .left ? "L" : "R")
                    .font(metrics.topBarFont.weight(.bold))
                    .frame(width: 44, height: 34)
            }
            .buttonStyle(.borderedProminent)
        }
    }

    private enum ShoulderSide { case left; case right }

    private func shoulderControls(_ metrics: GamepadMetrics, side: ShoulderSide) -> some View {
        HStack(spacing: metrics.buttonSpacing) {
            switch side {
            case .left:
                gamepadButton(item: .btnLT, label: label(for: "lt", fallback: "LT"), size: metrics.buttonSize, config: cfg.btnLT, vibration: .off) { pressed in
                    leftTrigger = pressed ? 255 : 0
                    sendReport()
                }
                gamepadButton(item: .btnLB, label: label(for: "lb", fallback: "LB"), size: metrics.buttonSize, config: cfg.btnLB, vibration: cfg.vibrationIntensity) { pressed in
                    setButton(.btnLB, pressed: pressed)
                }
            case .right:
                gamepadButton(item: .btnRB, label: label(for: "rb", fallback: "RB"), size: metrics.buttonSize, config: cfg.btnRB, vibration: cfg.vibrationIntensity) { pressed in
                    setButton(.btnRB, pressed: pressed)
                }
                gamepadButton(item: .btnRT, label: label(for: "rt", fallback: "RT"), size: metrics.buttonSize, config: cfg.btnRT, vibration: .off) { pressed in
                    rightTrigger = pressed ? 255 : 0
                    sendReport()
                }
            }
        }
    }

    private func centerControls(_ metrics: GamepadMetrics) -> some View {
        HStack(spacing: metrics.buttonSpacing) {
            gamepadButton(item: .btnBack, label: label(for: "back", fallback: "Back"),  size: metrics.buttonSize, config: cfg.btnBack, vibration: cfg.vibrationIntensity)  { pressed in setButton(.btnBack,  pressed: pressed) }
            gamepadButton(item: .btnStart, label: label(for: "start", fallback: "Start"), size: metrics.buttonSize, config: cfg.btnStart, vibration: cfg.vibrationIntensity) { pressed in setButton(.btnStart, pressed: pressed) }
        }
    }

    private func rightControls(_ metrics: GamepadMetrics) -> some View {
        VStack(spacing: metrics.sectionSpacing) {
            shoulderControls(metrics, side: .right)
            faceButtons(metrics)
            if !cfg.singleJoystickMode {
                layoutEditable(item: .rightJoystick, offsetX: cfg.rightJoystick.offsetX, offsetY: cfg.rightJoystick.offsetY, scaleX: cfg.rightJoystick.scaleX, scaleY: cfg.rightJoystick.scaleY) {
                    Joystick(label: "R", size: metrics.joystickSize, config: cfg.rightJoystick) { x, y in
                        guard !isEditingLayout else { return }
                        rightStick = CGPoint(x: x, y: y)
                        sendReport()
                    }
                }
            }
        }
    }

    private func faceButtons(_ metrics: GamepadMetrics) -> some View {
        VStack(spacing: metrics.smallSpacing) {
            gamepadButton(item: .btnY, label: label(for: "y", fallback: "Y"), tint: .yellow, size: metrics.buttonSize, config: cfg.btnY, vibration: cfg.vibrationIntensity) { pressed in setButton(.btnY, pressed: pressed) }
            HStack(spacing: metrics.crossSpacing) {
                gamepadButton(item: .btnX, label: label(for: "x", fallback: "X"), tint: .blue,  size: metrics.buttonSize, config: cfg.btnX, vibration: cfg.vibrationIntensity) { pressed in setButton(.btnX, pressed: pressed) }
                gamepadButton(item: .btnB, label: label(for: "b", fallback: "B"), tint: .red,   size: metrics.buttonSize, config: cfg.btnB, vibration: cfg.vibrationIntensity) { pressed in setButton(.btnB, pressed: pressed) }
            }
            gamepadButton(item: .btnA, label: label(for: "a", fallback: "A"), tint: .green, size: metrics.buttonSize, config: cfg.btnA, vibration: cfg.vibrationIntensity) { pressed in setButton(.btnA, pressed: pressed) }
        }
    }

    private func dpad(_ metrics: GamepadMetrics) -> some View {
        VStack(spacing: metrics.smallSpacing) {
            gamepadButton(item: .dpadUp, label: label(for: "dup", fallback: "Up"),    size: metrics.buttonSize, config: cfg.dpadUp, vibration: cfg.vibrationIntensity)    { pressed in dUp    = pressed; sendReport() }
            HStack(spacing: metrics.crossSpacing) {
                gamepadButton(item: .dpadLeft, label: label(for: "dleft", fallback: "Left"),  size: metrics.buttonSize, config: cfg.dpadLeft, vibration: cfg.vibrationIntensity)  { pressed in dLeft  = pressed; sendReport() }
                gamepadButton(item: .dpadRight, label: label(for: "dright", fallback: "Right"), size: metrics.buttonSize, config: cfg.dpadRight, vibration: cfg.vibrationIntensity) { pressed in dRight = pressed; sendReport() }
            }
            gamepadButton(item: .dpadDown, label: label(for: "ddown", fallback: "Down"),  size: metrics.buttonSize, config: cfg.dpadDown, vibration: cfg.vibrationIntensity)  { pressed in dDown  = pressed; sendReport() }
        }
        .font(.caption)
    }

    private func gamepadButton(
        item: GamepadLayoutItem,
        label: String,
        tint: Color = .gray,
        size: CGSize,
        config: ButtonConfig,
        vibration: VibrationIntensity,
        onChanged: @escaping (Bool) -> Void
    ) -> some View {
        layoutEditable(item: item, offsetX: config.offsetX, offsetY: config.offsetY, scaleX: config.scaleX, scaleY: config.scaleY) {
            GamepadButton(label: label, tint: tint, size: size, config: config, vibration: vibration) { pressed in
                guard !isEditingLayout else { return }
                onChanged(pressed)
            }
        }
    }

    private func layoutEditable<Content: View>(
        item: GamepadLayoutItem,
        offsetX: Double,
        offsetY: Double,
        scaleX: Double,
        scaleY: Double,
        @ViewBuilder content: () -> Content
    ) -> some View {
        content()
            .allowsHitTesting(!isEditingLayout)
            .scaleEffect(x: CGFloat(scaleX), y: CGFloat(scaleY))
            .offset(x: CGFloat(offsetX), y: CGFloat(offsetY))
            .overlay {
                if isEditingLayout {
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color.accentColor, style: StrokeStyle(lineWidth: 2, dash: [5, 4]))
                        .background(Color.accentColor.opacity(0.08))
                        .contentShape(Rectangle())
                        .gesture(layoutDragGesture(for: item))
                        .simultaneousGesture(layoutMagnifyGesture(for: item))
                }
            }
    }

    private func layoutDragGesture(for item: GamepadLayoutItem) -> some Gesture {
        DragGesture()
            .onChanged { value in
                if dragStartOffsets[item] == nil {
                    dragStartOffsets[item] = layoutValues(for: item).offset
                }
                let start = dragStartOffsets[item] ?? layoutValues(for: item).offset
                let values = layoutValues(for: item)
                let offset = CGSize(
                    width: (start.width + value.translation.width).clamped(to: -500...500),
                    height: (start.height + value.translation.height).clamped(to: -500...500)
                )
                setLayout(item, offset: offset, scale: values.scale)
            }
            .onEnded { _ in
                dragStartOffsets[item] = nil
            }
    }

    private func layoutMagnifyGesture(for item: GamepadLayoutItem) -> some Gesture {
        MagnifyGesture()
            .onChanged { value in
                if scaleStartValues[item] == nil {
                    scaleStartValues[item] = layoutValues(for: item).scale
                }
                let start = scaleStartValues[item] ?? layoutValues(for: item).scale
                let values = layoutValues(for: item)
                let scale = CGSize(
                    width: (start.width * value.magnification).clamped(to: 0.3...3.0),
                    height: (start.height * value.magnification).clamped(to: 0.3...3.0)
                )
                setLayout(item, offset: values.offset, scale: scale)
            }
            .onEnded { _ in
                scaleStartValues[item] = nil
            }
    }

    private func layoutValues(for item: GamepadLayoutItem) -> (offset: CGSize, scale: CGSize) {
        switch item {
        case .btnA: return values(cfg.btnA)
        case .btnB: return values(cfg.btnB)
        case .btnX: return values(cfg.btnX)
        case .btnY: return values(cfg.btnY)
        case .btnLB: return values(cfg.btnLB)
        case .btnRB: return values(cfg.btnRB)
        case .btnLT: return values(cfg.btnLT)
        case .btnRT: return values(cfg.btnRT)
        case .btnBack: return values(cfg.btnBack)
        case .btnStart: return values(cfg.btnStart)
        case .dpadUp: return values(cfg.dpadUp)
        case .dpadDown: return values(cfg.dpadDown)
        case .dpadLeft: return values(cfg.dpadLeft)
        case .dpadRight: return values(cfg.dpadRight)
        case .leftJoystick: return values(cfg.leftJoystick)
        case .rightJoystick: return values(cfg.rightJoystick)
        }
    }

    private func values(_ config: ButtonConfig) -> (offset: CGSize, scale: CGSize) {
        (
            CGSize(width: CGFloat(config.offsetX), height: CGFloat(config.offsetY)),
            CGSize(width: CGFloat(config.scaleX), height: CGFloat(config.scaleY))
        )
    }

    private func values(_ config: JoystickConfig) -> (offset: CGSize, scale: CGSize) {
        (
            CGSize(width: CGFloat(config.offsetX), height: CGFloat(config.offsetY)),
            CGSize(width: CGFloat(config.scaleX), height: CGFloat(config.scaleY))
        )
    }

    private func setLayout(_ item: GamepadLayoutItem, offset: CGSize, scale: CGSize) {
        var next = cfg
        func apply(_ button: inout ButtonConfig) {
            button.offsetX = Double(offset.width)
            button.offsetY = Double(offset.height)
            button.scaleX = Double(scale.width)
            button.scaleY = Double(scale.height)
        }
        func apply(_ joystick: inout JoystickConfig) {
            joystick.offsetX = Double(offset.width)
            joystick.offsetY = Double(offset.height)
            joystick.scaleX = Double(scale.width)
            joystick.scaleY = Double(scale.height)
        }
        switch item {
        case .btnA: apply(&next.btnA)
        case .btnB: apply(&next.btnB)
        case .btnX: apply(&next.btnX)
        case .btnY: apply(&next.btnY)
        case .btnLB: apply(&next.btnLB)
        case .btnRB: apply(&next.btnRB)
        case .btnLT: apply(&next.btnLT)
        case .btnRT: apply(&next.btnRT)
        case .btnBack: apply(&next.btnBack)
        case .btnStart: apply(&next.btnStart)
        case .dpadUp: apply(&next.dpadUp)
        case .dpadDown: apply(&next.dpadDown)
        case .dpadLeft: apply(&next.dpadLeft)
        case .dpadRight: apply(&next.dpadRight)
        case .leftJoystick: apply(&next.leftJoystick)
        case .rightJoystick: apply(&next.rightJoystick)
        }
        appState.updateGamepadConfig(next)
    }

    private func label(for key: String, fallback: String) -> String {
        cfg.customButtonLabels[key]?.nilIfBlank ?? fallback
    }

    private func setButton(_ bit: GamepadButtonBit, pressed: Bool) {
        if pressed {
            buttonBits |= 1 << bit.rawValue
        } else {
            buttonBits &= ~(1 << bit.rawValue)
        }
        sendReport()
    }

    private func sendReport() {
        let reportLeftStick: CGPoint
        let reportRightStick: CGPoint
        if cfg.singleJoystickMode {
            if cfg.singleJoystickOutputSide == .left {
                reportLeftStick = leftStick
                reportRightStick = .zero
            } else {
                reportLeftStick = .zero
                reportRightStick = leftStick
            }
        } else {
            reportLeftStick = leftStick
            reportRightStick = rightStick
        }
        appState.sendGamepadReport(
            leftX: Int(reportLeftStick.x * 32767),
            leftY: Int(reportLeftStick.y * 32767),
            rightX: Int(reportRightStick.x * 32767),
            rightY: Int(reportRightStick.y * 32767),
            leftTrigger: leftTrigger,
            rightTrigger: rightTrigger,
            buttons: buttonBits,
            hat: currentHat
        )
    }

    private var currentHat: Int {
        switch (dUp, dDown, dLeft, dRight) {
        case (true, _, _, true): HIDReportDescriptors.hatNE
        case (_, true, _, true): HIDReportDescriptors.hatSE
        case (_, true, true, _): HIDReportDescriptors.hatSW
        case (true, _, true, _): HIDReportDescriptors.hatNW
        case (true, _, _, _):   HIDReportDescriptors.hatN
        case (_, true, _, _):   HIDReportDescriptors.hatS
        case (_, _, true, _):   HIDReportDescriptors.hatW
        case (_, _, _, true):   HIDReportDescriptors.hatE
        default:                HIDReportDescriptors.hatNone
        }
    }
}

enum GamepadButtonBit: Int {
    case btnA = 0
    case btnB = 1
    case btnX = 2
    case btnY = 3
    case btnLB = 4
    case btnRB = 5
    case btnBack = 6
    case btnStart = 7
}

private enum GamepadLayoutItem: Hashable {
    case btnA
    case btnB
    case btnX
    case btnY
    case btnLB
    case btnRB
    case btnLT
    case btnRT
    case btnBack
    case btnStart
    case dpadUp
    case dpadDown
    case dpadLeft
    case dpadRight
    case leftJoystick
    case rightJoystick
}

private extension CGFloat {
    func clamped(to range: ClosedRange<CGFloat>) -> CGFloat {
        Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
    }
}

private struct GamepadMetrics {
    let isPortrait: Bool
    let buttonSize: CGSize
    let joystickSize: CGFloat
    let padding: CGFloat
    let spacing: CGFloat
    let sectionSpacing: CGFloat
    let buttonSpacing: CGFloat
    let smallSpacing: CGFloat
    let crossSpacing: CGFloat
    let topBarFont: Font

    init(size: CGSize, isPortrait: Bool) {
        self.isPortrait = isPortrait
        let shortSide = min(size.width, size.height)
        let longSide = max(size.width, size.height)

        if isPortrait {
            let widthBudget = max(320, size.width)
            let buttonWidth = min(64, max(48, widthBudget * 0.145))
            let buttonHeight = min(48, max(40, longSide * 0.055))

            buttonSize = CGSize(width: buttonWidth, height: buttonHeight)
            joystickSize = min(150, max(110, widthBudget * 0.34))
            padding = max(12, min(18, widthBudget * 0.04))
            spacing = max(10, min(16, widthBudget * 0.035))
            sectionSpacing = spacing
            buttonSpacing = max(6, min(10, widthBudget * 0.02))
            smallSpacing = max(4, min(7, widthBudget * 0.014))
            crossSpacing = max(24, min(42, widthBudget * 0.095))
            topBarFont = .callout
        } else {
            let buttonWidth = min(68, max(54, shortSide * 0.105))
            let buttonHeight = min(52, max(42, shortSide * 0.08))

            buttonSize = CGSize(width: buttonWidth, height: buttonHeight)
            joystickSize = min(178, max(126, shortSide * 0.28))
            padding = 16
            spacing = max(14, min(24, shortSide * 0.035))
            sectionSpacing = max(16, min(24, shortSide * 0.04))
            buttonSpacing = max(8, min(12, shortSide * 0.02))
            smallSpacing = max(4, min(8, shortSide * 0.012))
            crossSpacing = max(34, min(54, shortSide * 0.08))
            topBarFont = .body
        }
    }
}

struct GamepadButton: View {
    let label: String
    var tint: Color = .gray
    var size = CGSize(width: 68, height: 52)
    var config: ButtonConfig = ButtonConfig()
    var vibration: VibrationIntensity = .off
    let onChanged: (Bool) -> Void

    @State private var pressed = false
    @State private var latched = false
    @State private var turboTask: Task<Void, Never>?

    var body: some View {
        Text(label)
            .font(.headline)
            .minimumScaleFactor(0.65)
            .lineLimit(1)
            .frame(width: size.width, height: size.height)
            .background(pressed || latched ? tint.opacity(0.85) : tint.opacity(0.32))
            .foregroundStyle(.primary)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(.white.opacity(0.5), lineWidth: 1))
            .opacity(config.enabled ? 1 : 0)
            .allowsHitTesting(config.enabled)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        guard !pressed else { return }
                        pressed = true
                        if config.behavior != .latching {
                            playHaptic()
                            onChanged(true)
                            if config.turbo { startTurbo() }
                        }
                    }
                    .onEnded { _ in
                        guard pressed else { return }
                        pressed = false
                        if config.behavior == .latching {
                            latched.toggle()
                            if latched { playHaptic() }
                            onChanged(latched)
                        } else {
                            onChanged(false)
                            stopTurbo()
                        }
                    }
            )
            .onDisappear {
                turboTask?.cancel()
                turboTask = nil
            }
    }

    private func startTurbo() {
        turboTask?.cancel()
        let interval = config.turboIntervalMs
        let duration = config.turboDurationMs
        turboTask = Task { @MainActor in
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(interval))
                guard !Task.isCancelled else { break }
                onChanged(false)
                try? await Task.sleep(for: .milliseconds(duration))
                guard !Task.isCancelled else { break }
                onChanged(true)
            }
        }
    }

    private func stopTurbo() {
        turboTask?.cancel()
        turboTask = nil
    }

    private func playHaptic() {
        #if canImport(UIKit)
        switch vibration {
        case .off:
            return
        case .light:
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        case .medium:
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        case .strong:
            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
        }
        #endif
    }
}

struct KeyboardMacroPanel: View {
    let macros: [KeyboardMacroButtonConfig]
    let onChanged: (KeyboardMacroButtonConfig, Bool) -> Void

    var body: some View {
        VStack(alignment: .trailing, spacing: 8) {
            ForEach(macros) { macro in
                KeyboardMacroButton(macro: macro, onChanged: onChanged)
            }
        }
        .padding(10)
        .background(.thinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

private struct KeyboardMacroButton: View {
    let macro: KeyboardMacroButtonConfig
    let onChanged: (KeyboardMacroButtonConfig, Bool) -> Void
    @State private var pressed = false

    var body: some View {
        Text(macro.label)
            .font(.callout.weight(.semibold))
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .frame(minWidth: 84, minHeight: 42)
            .padding(.horizontal, 10)
            .background(pressed ? Color.accentColor.opacity(0.85) : Color.secondary.opacity(0.22))
            .foregroundStyle(pressed ? .white : .primary)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        guard !pressed else { return }
                        pressed = true
                        onChanged(macro, true)
                    }
                    .onEnded { _ in
                        guard pressed else { return }
                        pressed = false
                        onChanged(macro, false)
                    }
            )
            .onDisappear {
                if pressed {
                    pressed = false
                    onChanged(macro, false)
                }
            }
    }
}

struct Joystick: View {
    let label: String
    var size: CGFloat = 178
    var config: JoystickConfig = JoystickConfig()
    let onChanged: (Double, Double) -> Void
    @State private var knob = CGSize.zero

    var body: some View {
        let radius = size * 0.34
        let knobSize = max(42, size * 0.33)

        ZStack {
            Circle()
                .fill(.blue.opacity(0.16))
                .overlay(Circle().stroke(.blue.opacity(0.45), lineWidth: 2))
            Circle()
                .fill(.blue.opacity(0.72))
                .frame(width: knobSize, height: knobSize)
                .overlay(Text(label).font(.headline).foregroundStyle(.white))
                .offset(knob)
        }
        .frame(width: size, height: size)
        .opacity(config.enabled ? 1 : 0.35)
        .allowsHitTesting(config.enabled)
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    let dx = value.translation.width
                    let dy = value.translation.height
                    let distance = sqrt(dx * dx + dy * dy)
                    let scale = distance > radius ? radius / distance : 1
                    knob = CGSize(width: dx * scale, height: dy * scale)

                    var nx = Double(knob.width / radius)
                    var ny = Double(knob.height / radius)
                    let mag = sqrt(nx * nx + ny * ny)
                    if mag < config.deadzone {
                        nx = 0; ny = 0
                    } else {
                        let norm = min(1.0, (mag - config.deadzone) / (1.0 - config.deadzone) * config.gain)
                        let factor = mag > 0 ? norm / mag : 0
                        nx *= factor; ny *= factor
                    }
                    onChanged(nx, ny)
                }
                .onEnded { _ in
                    knob = .zero
                    onChanged(0, 0)
                }
        )
    }
}

struct ConnectionPill: View {
    let text: String
    let connected: Bool

    var body: some View {
        Label(text, systemImage: connected ? "checkmark.circle.fill" : "circle.fill")
            .font(.callout.weight(.semibold))
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(.thinMaterial)
            .clipShape(Capsule())
            .foregroundStyle(connected ? .green : .red)
    }
}

private extension String {
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
