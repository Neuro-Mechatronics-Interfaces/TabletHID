import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
#if canImport(Darwin)
import Darwin
#endif

/// Sheet for uploading the current profile's config to the community server.
///
/// Mirrors the Android UploadSheet:
/// - Editable profile name, description, tags, category.
/// - Auto-filled read-only device info.
/// - Privacy notice.
/// - Cancel / Upload with loading state.
struct UploadSheet: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.dismiss) private var dismiss

    let mode: String   // "gamepad" or "touch_mouse"
    let viewModel: CommunityViewModel

    @State private var profileName: String = ""
    @State private var description: String = ""
    @State private var tagsText: String = ""
    @State private var category: String = ""
    @State private var isUploading = false
    @State private var uploadError: String? = nil
    @State private var uploadSuccessId: String? = nil

    private let deviceInfo = DeviceInfo.current

    var body: some View {
        NavigationStack {
            Form {
                Section("Config Info") {
                    LabeledContent("Profile name") {
                        TextField("Name", text: $profileName)
                            .multilineTextAlignment(.trailing)
                    }
                    LabeledContent("Description") {
                        TextField("Optional", text: $description, axis: .vertical)
                            .multilineTextAlignment(.trailing)
                            .lineLimit(3)
                    }
                    LabeledContent("Tags") {
                        TextField("e.g. gaming, accessibility", text: $tagsText)
                            .multilineTextAlignment(.trailing)
                    }
                    LabeledContent("Category") {
                        TextField("e.g. gaming", text: $category)
                            .multilineTextAlignment(.trailing)
                    }
                }

                Section("Device (auto-filled)") {
                    LabeledContent("Model", value: deviceInfo.name)
                    LabeledContent("OS", value: "iOS \(deviceInfo.osVersion)")
                    if let diag = deviceInfo.screenDiagonalInches {
                        LabeledContent("Screen", value: String(format: "%.1f\"", diag))
                    }
                }

                Section {
                    Text("Uploads are public. This sends the selected config, profile name, description, tags, category, app version, device model/hardware identifier, OS version, and screen size/density. Do not include personal information in text fields. Community content is user-generated; inappropriate language may be removed when detected.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                if let err = uploadError {
                    Section {
                        Text(err)
                            .font(.caption)
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Share Config")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Upload") { performUpload() }
                        .disabled(isUploading || profileName.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .overlay {
                if isUploading {
                    Color.black.opacity(0.2).ignoresSafeArea()
                    ProgressView("Uploading…")
                        .padding(24)
                        .background(.regularMaterial)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
        }
        .onAppear {
            profileName = appState.activeProfile.name
        }
    }

    // MARK: - Upload

    private func performUpload() {
        isUploading = true
        uploadError = nil

        let configJson: [String: Any]
        if mode == "gamepad" {
            configJson = GamepadConfigSerializer.toCanonicalJson(appState.gamepadConfig)
        } else {
            configJson = TouchMouseConfigSerializer.toCanonicalJson(appState.touchMouseConfig)
        }

        let tags = tagsText
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }

        let body = CommunityUploadBody(
            platform:     "ios",
            mode:         mode,
            profileName:  profileName.trimmingCharacters(in: .whitespaces),
            configJson:   configJson,
            description:  description.trimmingCharacters(in: .whitespaces).nilIfEmpty,
            tags:         tags,
            category:     category.trimmingCharacters(in: .whitespaces).nilIfEmpty,
            appVersion:   Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
            deviceName:   deviceInfo.name.nilIfEmpty,
            deviceHwId:   deviceInfo.hwMachine.nilIfEmpty,
            deviceOsVersion: deviceInfo.osVersion.nilIfEmpty,
            deviceScreenWidthPx:   deviceInfo.screenWidthPx,
            deviceScreenHeightPx:  deviceInfo.screenHeightPx,
            deviceScreenDensityDpi: deviceInfo.screenDensityDpi
        )

        Task {
            do {
                let id = try await viewModel.uploadConfig(body)
                await MainActor.run {
                    isUploading = false
                    uploadSuccessId = id
                    dismiss()
                }
            } catch {
                await MainActor.run {
                    isUploading = false
                    uploadError = error.localizedDescription
                }
            }
        }
    }
}

// MARK: - DeviceInfo

private struct DeviceInfo {
    let name: String
    let osVersion: String
    let hwMachine: String
    let screenWidthPx: Int?
    let screenHeightPx: Int?
    let screenDensityDpi: Int?
    let screenDiagonalInches: Double?

    static let current: DeviceInfo = {
        #if canImport(UIKit)
        let device = UIDevice.current
        let modelName = device.model   // e.g. "iPad"
        let hwMachine = Self.sysctlHwMachine()
        let osVersion = device.systemVersion
        let screen = UIScreen.main
        let scale = screen.scale
        let widthPx  = Int(screen.bounds.width  * scale)
        let heightPx = Int(screen.bounds.height * scale)
        let dpi: Int = {
            // UIScreen.main.nativeBounds is in points*nativeScale.
            // Approximate density from scale and known point DPI (163 pt/in for non-retina).
            Int(163 * scale)
        }()
        let diagIn: Double? = {
            let w = screen.bounds.width  * scale / (163.0 * scale)
            let h = screen.bounds.height * scale / (163.0 * scale)
            let d = sqrt(w * w + h * h)
            return d > 1 ? d : nil
        }()
        return DeviceInfo(
            name: modelName,
            osVersion: osVersion,
            hwMachine: hwMachine,
            screenWidthPx: widthPx,
            screenHeightPx: heightPx,
            screenDensityDpi: dpi,
            screenDiagonalInches: diagIn
        )
        #else
        return DeviceInfo(name: "Mac", osVersion: ProcessInfo.processInfo.operatingSystemVersionString,
                          hwMachine: "", screenWidthPx: nil, screenHeightPx: nil,
                          screenDensityDpi: nil, screenDiagonalInches: nil)
        #endif
    }()

    private static func sysctlHwMachine() -> String {
        var size = 0
        sysctlbyname("hw.machine", nil, &size, nil, 0)
        var machine = [CChar](repeating: 0, count: size)
        sysctlbyname("hw.machine", &machine, &size, nil, 0)
        return String(cString: machine)
    }
}

// MARK: - String extension

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
