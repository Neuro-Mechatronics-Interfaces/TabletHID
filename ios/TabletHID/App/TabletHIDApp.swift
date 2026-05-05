import SwiftUI

@main
struct TabletHIDApp: App {
    #if canImport(UIKit)
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    #endif
    @StateObject private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
                .preferredColorScheme(appState.appearanceMode.colorScheme)
                .tabletHIDLargeText(appState.largeTextEnabled)
                .tabletHIDHighContrast(appState.highContrastEnabled)
        }
    }
}

private extension View {
    @ViewBuilder
    func tabletHIDLargeText(_ enabled: Bool) -> some View {
        if enabled {
            dynamicTypeSize(.accessibility1)
        } else {
            self
        }
    }

    @ViewBuilder
    func tabletHIDHighContrast(_ enabled: Bool) -> some View {
        if enabled {
            contrast(1.25)
                .tint(.primary)
        } else {
            self
        }
    }
}
