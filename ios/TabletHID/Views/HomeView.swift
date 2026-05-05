import SwiftUI
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

struct HomeView: View {
    @EnvironmentObject private var appState: AppState
    @State private var newProfileName = ""
    @State private var showingNewProfile = false

    let onSelectMode: (DeviceMode) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                header
                profilePicker
                modeGrid
                appearancePicker
            }
            .padding(24)
            .frame(maxWidth: 900, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .background(groupedBackgroundColor)
        .navigationTitle("TabletHID")
        .toolbar {
            Button {
                showingNewProfile = true
            } label: {
                Label("Add Profile", systemImage: "plus")
            }
        }
        .alert("New Profile", isPresented: $showingNewProfile) {
            TextField("Profile name", text: $newProfileName)
            Button("Create") {
                let name = newProfileName.trimmingCharacters(in: .whitespacesAndNewlines)
                if !name.isEmpty { appState.addCustomProfile(named: name) }
                newProfileName = ""
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Use this iPhone or iPad as a virtual HID control surface.")
                .font(.title2.weight(.semibold))
            Text("The iOS UI and report builders are ready for development; Bluetooth HID peripheral transport is still pending a supported platform path.")
                .foregroundStyle(.secondary)
        }
    }

    private var profilePicker: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Profile")
                .font(.headline)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(appState.allProfiles) { profile in
                        Button {
                            appState.setProfile(profile)
                        } label: {
                            Label(profile.name, systemImage: profile.key == appState.activeProfile.key ? "checkmark.circle.fill" : "person.crop.circle")
                                .labelStyle(.titleAndIcon)
                        }
                        .buttonStyle(.bordered)
                        .tint(profile.key == appState.activeProfile.key ? .blue : .gray)
                    }
                }
            }
        }
    }

    private var modeGrid: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 260), spacing: 16)], spacing: 16) {
            ForEach(DeviceMode.allCases) { mode in
                Button {
                    onSelectMode(mode)
                } label: {
                    VStack(alignment: .leading, spacing: 16) {
                        Image(systemName: mode.symbolName)
                            .font(.system(size: 42, weight: .semibold))
                            .foregroundStyle(.blue)
                        Text(mode.title)
                            .font(.title2.weight(.bold))
                        Text(mode == .touchMouse ? "Relative mouse movement with click zones." : "Virtual Xbox-style controller layout.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                    .background(cardBackgroundColor)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var appearancePicker: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Appearance")
                .font(.headline)
            Picker("Appearance", selection: Binding(
                get: { appState.appearanceMode },
                set: { appState.setAppearanceMode($0) }
            )) {
                ForEach(AppearanceMode.allCases) { mode in
                    Text(mode.label).tag(mode)
                }
            }
            .pickerStyle(.segmented)
        }
    }

    private var groupedBackgroundColor: Color {
        #if canImport(UIKit)
        Color(uiColor: .systemGroupedBackground)
        #elseif canImport(AppKit)
        Color(nsColor: .windowBackgroundColor)
        #else
        Color.white
        #endif
    }

    private var cardBackgroundColor: Color {
        #if canImport(UIKit)
        Color(uiColor: .secondarySystemGroupedBackground)
        #elseif canImport(AppKit)
        Color(nsColor: .controlBackgroundColor)
        #else
        Color.white
        #endif
    }
}
