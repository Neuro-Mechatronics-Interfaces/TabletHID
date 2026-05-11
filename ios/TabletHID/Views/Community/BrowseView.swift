import SwiftUI

/// Browse tab: list of community configs with filter chips, sort toggle, and pull-to-refresh.
struct BrowseView: View {
    @Bindable var viewModel: CommunityViewModel
    @State private var selectedRecord: CommunityConfigRecord? = nil

    var body: some View {
        VStack(spacing: 0) {
            filterBar
            contentNotice
            Divider()
            if viewModel.isLoading && viewModel.configs.isEmpty {
                Spacer()
                ProgressView()
                Spacer()
            } else if let errorMsg = viewModel.error, viewModel.configs.isEmpty {
                Spacer()
                emptyErrorView(errorMsg)
                Spacer()
            } else {
                configList
            }
        }
        .task { await viewModel.syncIfStale() }
        .sheet(item: $selectedRecord) { record in
            ImportSheet(record: record, viewModel: viewModel)
        }
    }

    // MARK: - Filter bar

    private var filterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                // Mode filter
                FilterChip(
                    label: "All Modes",
                    isSelected: viewModel.filterMode == nil
                ) { viewModel.setFilterMode(nil) }

                FilterChip(
                    label: "Gamepad",
                    isSelected: viewModel.filterMode == "gamepad"
                ) { viewModel.setFilterMode("gamepad") }

                FilterChip(
                    label: "Touch Mouse",
                    isSelected: viewModel.filterMode == "touch_mouse"
                ) { viewModel.setFilterMode("touch_mouse") }

                Divider().frame(height: 20)

                // Platform filter
                FilterChip(
                    label: "All Platforms",
                    isSelected: viewModel.filterPlatform == nil
                ) { viewModel.setFilterPlatform(nil) }

                FilterChip(
                    label: "Android",
                    isSelected: viewModel.filterPlatform == "android"
                ) { viewModel.setFilterPlatform("android") }

                FilterChip(
                    label: "iOS",
                    isSelected: viewModel.filterPlatform == "ios"
                ) { viewModel.setFilterPlatform("ios") }

                Divider().frame(height: 20)

                // Sort toggle
                Button {
                    viewModel.setSortOrder(viewModel.sortOrder == "recent" ? "popular" : "recent")
                } label: {
                    Label(
                        viewModel.sortOrder == "recent" ? "Recent" : "Popular",
                        systemImage: viewModel.sortOrder == "recent" ? "clock" : "flame"
                    )
                    .font(.caption.weight(.medium))
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
        }
    }

    private var contentNotice: some View {
        Text("Community content is user-generated and may contain language we do not condone. We remove or correct inappropriate content when detected, but cannot guarantee every listing is clean.")
            .font(.caption)
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.leading)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
    }

    // MARK: - Config list

    private var configList: some View {
        List(viewModel.configs) { record in
            Button {
                selectedRecord = record
            } label: {
                ConfigRowView(record: record)
            }
            .buttonStyle(.plain)
        }
        .listStyle(.plain)
        .refreshable { await viewModel.refresh() }
        .overlay {
            if viewModel.configs.isEmpty {
                Text("No configs found.")
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func emptyErrorView(_ message: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle")
                .font(.largeTitle)
                .foregroundStyle(.orange)
            Text("Could not load configs")
                .font(.headline)
            Text(message)
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Button("Retry") {
                Task { await viewModel.refresh() }
            }
            .buttonStyle(.borderedProminent)
        }
    }
}

// MARK: - FilterChip

private struct FilterChip: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.caption.weight(.medium))
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(isSelected ? Color.accentColor : Color.secondary.opacity(0.15))
                .foregroundStyle(isSelected ? Color.white : Color.primary)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}
