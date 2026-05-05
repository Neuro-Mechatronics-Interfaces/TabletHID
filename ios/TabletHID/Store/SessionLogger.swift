import Foundation

final class SessionLogger {
    private let writer: FileHandle
    private let queue = DispatchQueue(label: "hid-logger", qos: .utility)
    private var closed = false

    private let isoFmt: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }()

    init(mode: DeviceMode, profileName: String, touchConfig: TouchMouseConfig?) throws {
        let dir = Self.sessionDir()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

        let stampFmt = DateFormatter()
        stampFmt.dateFormat = "yyyyMMdd_HHmmss"
        stampFmt.locale = Locale(identifier: "en_US_POSIX")
        let stem = "\(stampFmt.string(from: Date()))_\(mode.rawValue.lowercased())"

        let configStr = Self.buildConfig(mode: mode, profileName: profileName, touch: touchConfig)
        try configStr.write(to: dir.appendingPathComponent("\(stem).config"), atomically: true, encoding: .utf8)

        let logURL = dir.appendingPathComponent("\(stem).log")
        FileManager.default.createFile(atPath: logURL.path, contents: nil)
        writer = try FileHandle(forWritingTo: logURL)

        let ts = now()
        queue.async { [weak self] in
            self?.writeLine("\(ts) SESSION_START mode=\(mode.rawValue.uppercased())")
        }
    }

    func logMouse(buttons: Int, dx: Int, dy: Int, wheel: Int) {
        guard !closed else { return }
        let ts = now()
        queue.async { [weak self] in
            self?.writeLine("\(ts) MOUSE buttons=\(buttons) dx=\(dx) dy=\(dy) wheel=\(wheel)")
        }
    }

    func logGamepad(lx: Int, ly: Int, rx: Int, ry: Int, lt: Int, rt: Int, buttons: Int, hat: Int) {
        guard !closed else { return }
        let ts = now()
        queue.async { [weak self] in
            self?.writeLine("\(ts) GAMEPAD lx=\(lx) ly=\(ly) rx=\(rx) ry=\(ry) lt=\(lt) rt=\(rt) buttons=\(buttons) hat=\(hat)")
        }
    }

    func close() {
        guard !closed else { return }
        closed = true
        let ts = now()
        queue.async { [weak self] in
            guard let self else { return }
            writeLine("\(ts) SESSION_END")
            try? writer.synchronize()
            try? writer.close()
        }
    }

    private func writeLine(_ line: String) {
        guard let data = (line + "\n").data(using: .utf8) else { return }
        try? writer.write(contentsOf: data)
    }

    private func now() -> String { isoFmt.string(from: Date()) }

    static func sessionDir() -> URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("sessions")
    }

    static var sessionDirDisplayPath: String { "Files app → TabletHID → sessions" }

    // MARK: - Config builder

    private static func buildConfig(mode: DeviceMode, profileName: String, touch: TouchMouseConfig?) -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS"
        fmt.locale = Locale(identifier: "en_US_POSIX")
        let ts = fmt.string(from: Date())

        var lines: [String] = [
            "# TabletHID Session Config",
            "# Generated: \(ts)",
            "# Mode: \(mode.title)",
            "",
            "[session]",
            "mode = \(mode.rawValue.uppercased())",
            "profile = \(profileName)",
            "generated = \(ts)",
            ""
        ]

        if let t = touch {
            lines += [
                "[touch_mouse]",
                "input_mode = \(t.mode.rawValue.uppercased())",
                "sensitivity = \(t.sensitivity)",
                ""
            ]
            lines += buttonZoneLines("left_button", b: t.leftButton)
            lines += buttonZoneLines("right_button", b: t.rightButton)
        }

        return lines.joined(separator: "\n") + "\n"
    }

    private static func buttonZoneLines(_ name: String, b: ButtonZoneConfig) -> [String] {
        [
            "[\(name)]",
            "enabled = \(b.enabled)",
            "zone_type = \(b.zoneType.rawValue)",
            "behavior = \(b.behavior.rawValue)",
            String(format: "static_left = %.3f", b.staticLeft),
            String(format: "static_top = %.3f", b.staticTop),
            String(format: "static_right = %.3f", b.staticRight),
            String(format: "static_bottom = %.3f", b.staticBottom),
            String(format: "dynamic_offset_x = %.3f", b.dynamicOffsetX),
            String(format: "dynamic_offset_y = %.3f", b.dynamicOffsetY),
            String(format: "dynamic_radius = %.3f", b.dynamicRadius),
            ""
        ]
    }
}
