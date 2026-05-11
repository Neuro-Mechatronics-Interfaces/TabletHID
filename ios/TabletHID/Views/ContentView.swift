import SwiftUI

struct ContentView: View {
    enum Screen: Hashable {
        case setup(DeviceMode)
        case touchMouse
        case gamepad
    }

    @EnvironmentObject private var appState: AppState
    @State private var path: [Screen] = []
    @State private var attemptedAutoReconnect = false

    var body: some View {
        Group {
            if appState.onboardingCompleted {
                mainNavigation
            } else {
                OnboardingView()
            }
        }
        .task(id: appState.onboardingCompleted) {
            guard appState.onboardingCompleted, !attemptedAutoReconnect else { return }
            attemptedAutoReconnect = true
            appState.maybeAutoReconnectOnLaunch()
        }
    }

    private var mainNavigation: some View {
        NavigationStack(path: $path) {
            HomeView(onSelectMode: handleModeTapped, onShowSetup: showSetup)
            .navigationDestination(for: Screen.self) { screen in
                switch screen {
                case .setup(let mode):
                    SetupView(mode: mode) {
                        path.append(mode == .touchMouse ? .touchMouse : .gamepad)
                    }
                case .touchMouse:
                    TouchMouseView {
                        appState.disconnect()
                        popControlScreen()
                    }
                case .gamepad:
                    GamepadView {
                        appState.disconnect()
                        popControlScreen()
                    }
                }
            }
        }
    }

    private func handleModeTapped(_ mode: DeviceMode) {
        switch appState.connectionState {
        case .connected:
            appState.initialize(mode: mode)
            path.append(mode == .touchMouse ? .touchMouse : .gamepad)
        case .idle, .error, .unavailable:
            if let host = appState.lastHost {
                appState.reconnect(mode: mode, host: host)
                path.append(mode == .touchMouse ? .touchMouse : .gamepad)
            } else {
                path.append(.setup(mode))
            }
        case .registering, .waitingForConnection, .reconnecting:
            appState.initialize(mode: mode)
            path.append(mode == .touchMouse ? .touchMouse : .gamepad)
        }
    }

    private func showSetup(_ mode: DeviceMode) {
        path.append(.setup(mode))
    }

    private func popControlScreen() {
        guard !path.isEmpty else { return }
        path.removeLast()
    }
}

private struct OnboardingView: View {
    @EnvironmentObject private var appState: AppState
    @State private var pageIndex = 0
    @State private var draftDeviceName = ""

    private let pages: [OnboardingPage] = [
        OnboardingPage(
            symbolName: "hand.raised.fill",
            title: "Welcome to TabletHID",
            body: "Turn your iPad into a wireless Bluetooth mouse or gamepad with controls built for touch."
        ),
        OnboardingPage(
            symbolName: "laptopcomputer.and.iphone",
            title: "Pair with Your Computer",
            body: "Use Make Discoverable from Home or Setup, then add a Bluetooth device on your computer and choose TabletHID from the list. No PIN required."
        ),
        OnboardingPage(
            symbolName: "gamecontroller.fill",
            title: "Choose Your Mode",
            body: "Start in Touch Mouse for pointer control, or Gamepad for sticks, triggers, buttons, and keyboard macro shortcuts."
        ),
        OnboardingPage(
            symbolName: "person.crop.circle.fill",
            title: "Name Your HoG Server",
            body: "Choose the Bluetooth HID-over-GATT server name your computer will see when pairing. You can change it later in Settings."
        )
    ]

    private var isLastPage: Bool {
        pageIndex == pages.count - 1
    }

    var body: some View {
        VStack(spacing: 28) {
            Spacer(minLength: 24)

            pageContent

            Spacer(minLength: 16)

            dotIndicator
            footer
        }
        .padding(.horizontal, 28)
        .padding(.vertical, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
        .onAppear {
            guard draftDeviceName.isEmpty else { return }
            draftDeviceName = appState.deviceName
        }
    }

    private var pageContent: some View {
        VStack(spacing: 24) {
            Image(systemName: pages[pageIndex].symbolName)
                .font(.system(size: 68, weight: .semibold))
                .foregroundStyle(.tint)
                .frame(width: 108, height: 108)
                .accessibilityHidden(true)

            VStack(spacing: 12) {
                Text(pages[pageIndex].title)
                    .font(.largeTitle.weight(.bold))
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)

                Text(pages[pageIndex].body)
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: 620)
            }

            if isLastPage {
                TextField("Device name, e.g. TabletHID - Max", text: $draftDeviceName)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                    .font(.title3)
                    .padding(16)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                    .frame(maxWidth: 460)
                    .submitLabel(.done)
                    .onSubmit(finish)
            }
        }
    }

    private var dotIndicator: some View {
        HStack(spacing: 8) {
            ForEach(pages.indices, id: \.self) { index in
                Circle()
                    .fill(index == pageIndex ? Color.accentColor : Color.secondary.opacity(0.35))
                    .frame(width: 8, height: 8)
            }
        }
        .accessibilityLabel("Page \(pageIndex + 1) of \(pages.count)")
    }

    private var footer: some View {
        HStack(spacing: 16) {
            Button("Back") {
                pageIndex = max(pageIndex - 1, 0)
            }
            .buttonStyle(.bordered)
            .disabled(pageIndex == 0)

            Spacer()

            if !isLastPage {
                Button("Skip", action: finish)
                    .buttonStyle(.bordered)
            }

            Button(isLastPage ? "Get Started" : "Next") {
                if isLastPage {
                    finish()
                } else {
                    pageIndex = min(pageIndex + 1, pages.count - 1)
                }
            }
            .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: 620)
    }

    private func finish() {
        appState.completeOnboarding(deviceName: draftDeviceName)
    }
}

private struct OnboardingPage {
    let symbolName: String
    let title: String
    let body: String
}
