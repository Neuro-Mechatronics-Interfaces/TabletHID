import Foundation

struct GamepadElementRect {
    let left: Double
    let top: Double
    let w: Double
    let h: Double
}

/// Resolves the natural (un-offset) position of every gamepad element on a given canvas.
///
/// Mirrors GamepadLayoutResolver.kt exactly — reads gamepad_layout.json from the app bundle,
/// then performs the same dependency-sorted constraint pass to produce absolute rects.
enum GamepadLayoutResolver {

    private static var cachedJson: [String: Any]?

    /// Returns a map from element ID to its natural rect on a canvas of size (canvasW × canvasH).
    static func resolveLayout(canvasW: Double, canvasH: Double) -> [String: GamepadElementRect] {
        let json: [String: Any]
        if let cached = cachedJson {
            json = cached
        } else if
            let url  = Bundle.main.url(forResource: "gamepad_layout", withExtension: "json"),
            let data = try? Data(contentsOf: url),
            let parsed = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        {
            cachedJson = parsed
            json = parsed
        } else {
            return [:]
        }

        let topOffset = json["topReservedDp"] as? Double ?? 0.0
        guard let elsJson = json["elements"] as? [String: [String: Any]] else { return [:] }
        let chainGroupsJson = json["chainGroups"] as? [String: [String: Any]]

        let elIds = Array(elsJson.keys)
        var ws   = [String: Double]()
        var hs   = [String: Double]()
        var xPos = [String: Double]()
        var yPos = [String: Double]()

        for id in elIds {
            let el = elsJson[id]!
            ws[id] = el["w"] as? Double ?? 0.0
            hs[id] = el["h"] as? Double ?? 0.0
        }

        // Pre-pass: resolve chain-group horizontal positions.
        if let chainGroupsJson = chainGroupsJson {
            for (_, group) in chainGroupsJson {
                guard let groupEls = group["elements"] as? [String] else { continue }
                let gap = group["gap"] as? Double ?? 0.0
                var totalW = 0.0
                for id in groupEls { totalW += ws[id] ?? 0.0 }
                totalW += gap * Double(groupEls.count - 1)
                var x = (group["horizontalAlign"] as? String == "center") ? (canvasW - totalW) / 2.0 : 0.0
                for id in groupEls {
                    xPos[id] = x
                    x += (ws[id] ?? 0.0) + gap
                }
            }
        }

        func xDeps(of id: String) -> [String] {
            if xPos[id] != nil { return [] }
            guard let el = elsJson[id] else { return [] }
            return ["startToStart", "startToEnd", "endToEnd", "endToStart"].compactMap { key in
                let v = el[key] as? String ?? ""
                return (!v.isEmpty && v != "parent") ? v : nil
            }
        }

        func yDeps(of id: String) -> [String] {
            guard let el = elsJson[id] else { return [] }
            return ["topToTop", "topToBottom", "bottomToBottom", "bottomToTop"].compactMap { key in
                let v = el[key] as? String ?? ""
                return (!v.isEmpty && v != "parent") ? v : nil
            }
        }

        func topoSort(_ ids: [String], deps: (String) -> [String]) -> [String] {
            var visited = Set<String>()
            var order   = [String]()
            func visit(_ id: String) {
                guard !visited.contains(id) else { return }
                visited.insert(id)
                for dep in deps(id) { visit(dep) }
                order.append(id)
            }
            ids.forEach { visit($0) }
            return order
        }

        for id in topoSort(elIds, deps: xDeps) {
            if xPos[id] != nil { continue }
            guard let el = elsJson[id] else { continue }
            let sm = el["startMargin"] as? Double ?? 0.0
            let em = el["endMargin"]   as? Double ?? 0.0
            let w  = ws[id] ?? 0.0
            let sTS = el["startToStart"] as? String ?? ""
            let sTE = el["startToEnd"]   as? String ?? ""
            let eTE = el["endToEnd"]     as? String ?? ""
            let eTS = el["endToStart"]   as? String ?? ""
            if      sTS == "parent" { xPos[id] = sm }
            else if !sTS.isEmpty    { xPos[id] = (xPos[sTS] ?? 0.0) + sm }
            else if sTE == "parent" { xPos[id] = canvasW + sm }
            else if !sTE.isEmpty    { xPos[id] = (xPos[sTE] ?? 0.0) + (ws[sTE] ?? 0.0) + sm }
            else if eTE == "parent" { xPos[id] = canvasW - w - em }
            else if !eTE.isEmpty    { xPos[id] = (xPos[eTE] ?? 0.0) + (ws[eTE] ?? 0.0) - w - em }
            else if eTS == "parent" { xPos[id] = -w - em }
            else if !eTS.isEmpty    { xPos[id] = (xPos[eTS] ?? 0.0) - w - em }
            else                    { xPos[id] = 0.0 }
        }

        for id in topoSort(elIds, deps: yDeps) {
            guard let el = elsJson[id] else { continue }
            let tm = el["topMargin"]    as? Double ?? 0.0
            let bm = el["bottomMargin"] as? Double ?? 0.0
            let h  = hs[id] ?? 0.0
            let tTT = el["topToTop"]       as? String ?? ""
            let tTB = el["topToBottom"]    as? String ?? ""
            let bTB = el["bottomToBottom"] as? String ?? ""
            let bTT = el["bottomToTop"]    as? String ?? ""
            if      tTT == "parent" { yPos[id] = topOffset + tm }
            else if !tTT.isEmpty    { yPos[id] = (yPos[tTT] ?? 0.0) + tm }
            else if tTB == "parent" { yPos[id] = canvasH + tm }
            else if !tTB.isEmpty    { yPos[id] = (yPos[tTB] ?? 0.0) + (hs[tTB] ?? 0.0) + tm }
            else if bTB == "parent" { yPos[id] = canvasH - h - bm }
            else if !bTB.isEmpty    { yPos[id] = (yPos[bTB] ?? 0.0) + (hs[bTB] ?? 0.0) - h - bm }
            else if bTT == "parent" { yPos[id] = -h - bm }
            else if !bTT.isEmpty    { yPos[id] = (yPos[bTT] ?? 0.0) - h - bm }
            else                    { yPos[id] = 0.0 }
        }

        return Dictionary(uniqueKeysWithValues: elIds.map { id in
            (id, GamepadElementRect(
                left: xPos[id] ?? 0.0,
                top:  yPos[id] ?? 0.0,
                w:    ws[id]   ?? 0.0,
                h:    hs[id]   ?? 0.0
            ))
        })
    }
}
