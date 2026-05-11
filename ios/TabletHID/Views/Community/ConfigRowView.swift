import SwiftUI

/// A single row in the Browse config list. Shows mode icon, profile name,
/// device/OS/diagonal info, and download count.
struct ConfigRowView: View {
    let record: CommunityConfigRecord

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: modeSymbol)
                .font(.title2)
                .foregroundStyle(.blue)
                .frame(width: 32)

            VStack(alignment: .leading, spacing: 4) {
                Text(record.profileName)
                    .font(.headline)
                    .lineLimit(1)

                if let deviceName = record.deviceName {
                    Text(deviceName)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                HStack(spacing: 6) {
                    Text(platformLabel)
                        .font(.caption2)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.accentColor.opacity(0.12))
                        .clipShape(Capsule())

                    if let os = record.deviceOsVersion {
                        Text(osLabel(os))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }

                    if let diag = record.deviceScreenDiagonalIn {
                        Text(String(format: "%.1f\"", diag))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }

                    Spacer()

                    Label("\(record.downloadCount)", systemImage: "arrow.down.circle")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(.vertical, 4)
    }

    private var modeSymbol: String {
        record.mode == "gamepad" ? "gamecontroller" : "cursorarrow.click.2"
    }

    private var platformLabel: String {
        record.platform == "ios" ? "iOS" : "Android"
    }

    private func osLabel(_ version: String) -> String {
        record.platform == "ios" ? "iOS \(version)" : "Android \(version)"
    }
}
