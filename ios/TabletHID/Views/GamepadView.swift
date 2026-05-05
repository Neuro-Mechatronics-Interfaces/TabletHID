import SwiftUI

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
                    Joystick(label: "L", size: metrics.joystickSize, config: cfg.leftJoystick) { x, y in
                        leftStick = CGPoint(x: x, y: y)
                        sendReport()
                    }
                    dpad(metrics)
                }

                Spacer(minLength: metrics.spacing)

                VStack(spacing: metrics.spacing) {
                    faceButtons(metrics)
                    Joystick(label: "R", size: metrics.joystickSize, config: cfg.rightJoystick) { x, y in
                        rightStick = CGPoint(x: x, y: y)
                        sendReport()
                    }
                }
            }

            centerControls(metrics)
        }
    }

    private func leftControls(_ metrics: GamepadMetrics) -> some View {
        VStack(spacing: metrics.sectionSpacing) {
            shoulderControls(metrics, side: .left)
            Joystick(label: "L", size: metrics.joystickSize, config: cfg.leftJoystick) { x, y in
                leftStick = CGPoint(x: x, y: y)
                sendReport()
            }
            dpad(metrics)
        }
    }

    private enum ShoulderSide { case left; case right }

    private func shoulderControls(_ metrics: GamepadMetrics, side: ShoulderSide) -> some View {
        HStack(spacing: metrics.buttonSpacing) {
            switch side {
            case .left:
                GamepadButton(label: "LT", size: metrics.buttonSize, config: cfg.btnLT) { pressed in
                    leftTrigger = pressed ? 255 : 0
                    sendReport()
                }
                GamepadButton(label: "LB", size: metrics.buttonSize, config: cfg.btnLB) { pressed in
                    setButton(.btnLB, pressed: pressed)
                }
            case .right:
                GamepadButton(label: "RB", size: metrics.buttonSize, config: cfg.btnRB) { pressed in
                    setButton(.btnRB, pressed: pressed)
                }
                GamepadButton(label: "RT", size: metrics.buttonSize, config: cfg.btnRT) { pressed in
                    rightTrigger = pressed ? 255 : 0
                    sendReport()
                }
            }
        }
    }

    private func centerControls(_ metrics: GamepadMetrics) -> some View {
        HStack(spacing: metrics.buttonSpacing) {
            GamepadButton(label: "Back",  size: metrics.buttonSize, config: cfg.btnBack)  { pressed in setButton(.btnBack,  pressed: pressed) }
            GamepadButton(label: "Start", size: metrics.buttonSize, config: cfg.btnStart) { pressed in setButton(.btnStart, pressed: pressed) }
        }
    }

    private func rightControls(_ metrics: GamepadMetrics) -> some View {
        VStack(spacing: metrics.sectionSpacing) {
            shoulderControls(metrics, side: .right)
            faceButtons(metrics)
            Joystick(label: "R", size: metrics.joystickSize, config: cfg.rightJoystick) { x, y in
                rightStick = CGPoint(x: x, y: y)
                sendReport()
            }
        }
    }

    private func faceButtons(_ metrics: GamepadMetrics) -> some View {
        VStack(spacing: metrics.smallSpacing) {
            GamepadButton(label: "Y", tint: .yellow, size: metrics.buttonSize, config: cfg.btnY) { pressed in setButton(.btnY, pressed: pressed) }
            HStack(spacing: metrics.crossSpacing) {
                GamepadButton(label: "X", tint: .blue,  size: metrics.buttonSize, config: cfg.btnX) { pressed in setButton(.btnX, pressed: pressed) }
                GamepadButton(label: "B", tint: .red,   size: metrics.buttonSize, config: cfg.btnB) { pressed in setButton(.btnB, pressed: pressed) }
            }
            GamepadButton(label: "A", tint: .green, size: metrics.buttonSize, config: cfg.btnA) { pressed in setButton(.btnA, pressed: pressed) }
        }
    }

    private func dpad(_ metrics: GamepadMetrics) -> some View {
        VStack(spacing: metrics.smallSpacing) {
            GamepadButton(label: "Up",    size: metrics.buttonSize, config: cfg.dpadUp)    { pressed in dUp    = pressed; sendReport() }
            HStack(spacing: metrics.crossSpacing) {
                GamepadButton(label: "Left",  size: metrics.buttonSize, config: cfg.dpadLeft)  { pressed in dLeft  = pressed; sendReport() }
                GamepadButton(label: "Right", size: metrics.buttonSize, config: cfg.dpadRight) { pressed in dRight = pressed; sendReport() }
            }
            GamepadButton(label: "Down",  size: metrics.buttonSize, config: cfg.dpadDown)  { pressed in dDown  = pressed; sendReport() }
        }
        .font(.caption)
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
        appState.sendGamepadReport(
            leftX: Int(leftStick.x * 32767),
            leftY: Int(leftStick.y * 32767),
            rightX: Int(rightStick.x * 32767),
            rightY: Int(rightStick.y * 32767),
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
                            onChanged(true)
                            if config.turbo { startTurbo() }
                        }
                    }
                    .onEnded { _ in
                        guard pressed else { return }
                        pressed = false
                        if config.behavior == .latching {
                            latched.toggle()
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
