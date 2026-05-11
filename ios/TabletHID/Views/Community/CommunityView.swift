import SwiftUI

/// Root community screen. Hosts Browse and Share tabs with a persistent profile-name header.
struct CommunityView: View {
    @EnvironmentObject private var appState: AppState
    @State private var viewModel = CommunityViewModel()
    @State private var selectedTab = 0

    var body: some View {
        VStack(spacing: 0) {
            profileHeader
            Divider()
            TabView(selection: $selectedTab) {
                BrowseView(viewModel: viewModel)
                    .tabItem { Label("Browse", systemImage: "square.grid.2x2") }
                    .tag(0)

                ShareView(viewModel: viewModel)
                    .tabItem { Label("Share", systemImage: "square.and.arrow.up") }
                    .tag(1)
            }
        }
        .navigationTitle("Community")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var profileHeader: some View {
        HStack {
            Image(systemName: "person.crop.circle.fill")
                .foregroundStyle(.secondary)
            Text(appState.activeProfile.name)
                .font(.subheadline.weight(.medium))
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(profileHeaderBackground)
    }

    private var profileHeaderBackground: Color {
        #if canImport(UIKit)
        Color(uiColor: .secondarySystemGroupedBackground)
        #else
        Color(nsColor: .controlBackgroundColor)
        #endif
    }
}
