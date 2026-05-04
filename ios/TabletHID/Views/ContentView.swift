import SwiftUI

struct ContentView: View {
    enum Screen: Hashable {
        case setup(DeviceMode)
        case touchMouse
        case gamepad
    }

    @EnvironmentObject private var appState: AppState
    @State private var path: [Screen] = []

    var body: some View {
        NavigationStack(path: $path) {
            HomeView { mode in
                path.append(.setup(mode))
            }
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

    private func popControlScreen() {
        guard !path.isEmpty else { return }
        path.removeLast()
    }
}
