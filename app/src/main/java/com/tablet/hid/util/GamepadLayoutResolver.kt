package com.tablet.hid.util

import android.content.Context
import org.json.JSONObject

data class GamepadElementRect(val left: Float, val top: Float, val w: Float, val h: Float)

object GamepadLayoutResolver {

    private var cachedJson: JSONObject? = null

    fun resolveLayout(context: Context, canvasW: Float, canvasH: Float): Map<String, GamepadElementRect> {
        val json = cachedJson ?: run {
            val text = context.assets.open("gamepad_layout.json").bufferedReader().use { it.readText() }
            JSONObject(text).also { cachedJson = it }
        }

        val topOffset = json.optDouble("topReservedDp", 0.0).toFloat()
        val elsJson = json.getJSONObject("elements")
        val chainGroupsJson = json.optJSONObject("chainGroups")

        val elIds = elsJson.keys().asSequence().toList()
        val ws = mutableMapOf<String, Float>()
        val hs = mutableMapOf<String, Float>()
        val xPos = mutableMapOf<String, Float>()
        val yPos = mutableMapOf<String, Float>()

        for (id in elIds) {
            val el = elsJson.getJSONObject(id)
            ws[id] = el.optDouble("w", 0.0).toFloat()
            hs[id] = el.optDouble("h", 0.0).toFloat()
        }

        // Pre-pass: resolve chain group x positions
        if (chainGroupsJson != null) {
            for (groupId in chainGroupsJson.keys()) {
                val group = chainGroupsJson.getJSONObject(groupId)
                val groupEls = group.getJSONArray("elements")
                val gap = group.optDouble("gap", 0.0).toFloat()
                var totalW = 0f
                for (i in 0 until groupEls.length()) totalW += ws[groupEls.getString(i)] ?: 0f
                totalW += gap * (groupEls.length() - 1)
                var x = if (group.optString("horizontalAlign") == "center") (canvasW - totalW) / 2f else 0f
                for (i in 0 until groupEls.length()) {
                    val id = groupEls.getString(i)
                    xPos[id] = x
                    x += (ws[id] ?: 0f) + gap
                }
            }
        }

        fun getXDeps(id: String): List<String> {
            if (xPos.containsKey(id)) return emptyList()
            val el = elsJson.getJSONObject(id)
            val deps = mutableListOf<String>()
            el.optString("startToStart").takeIf { it.isNotEmpty() && it != "parent" }?.let { deps += it }
            el.optString("startToEnd").takeIf { it.isNotEmpty() && it != "parent" }?.let { deps += it }
            el.optString("endToEnd").takeIf { it.isNotEmpty() && it != "parent" }?.let { deps += it }
            el.optString("endToStart").takeIf { it.isNotEmpty() && it != "parent" }?.let { deps += it }
            return deps
        }

        fun getYDeps(id: String): List<String> {
            val el = elsJson.getJSONObject(id)
            val deps = mutableListOf<String>()
            el.optString("topToTop").takeIf { it.isNotEmpty() && it != "parent" }?.let { deps += it }
            el.optString("topToBottom").takeIf { it.isNotEmpty() && it != "parent" }?.let { deps += it }
            el.optString("bottomToBottom").takeIf { it.isNotEmpty() && it != "parent" }?.let { deps += it }
            el.optString("bottomToTop").takeIf { it.isNotEmpty() && it != "parent" }?.let { deps += it }
            return deps
        }

        fun topoSort(ids: List<String>, getDeps: (String) -> List<String>): List<String> {
            val visited = mutableSetOf<String>()
            val order = mutableListOf<String>()
            fun visit(id: String) {
                if (id in visited) return
                visited += id
                for (dep in getDeps(id)) visit(dep)
                order += id
            }
            ids.forEach { visit(it) }
            return order
        }

        val xOrder = topoSort(elIds, ::getXDeps)
        val yOrder = topoSort(elIds, ::getYDeps)

        for (id in xOrder) {
            if (xPos.containsKey(id)) continue
            val el = elsJson.getJSONObject(id)
            val sm = el.optDouble("startMargin", 0.0).toFloat()
            val em = el.optDouble("endMargin", 0.0).toFloat()
            val w = ws[id] ?: 0f
            val sTS = el.optString("startToStart")
            val sTE = el.optString("startToEnd")
            val eTE = el.optString("endToEnd")
            val eTS = el.optString("endToStart")
            xPos[id] = when {
                sTS == "parent"      -> sm
                sTS.isNotEmpty()     -> (xPos[sTS] ?: 0f) + sm
                sTE == "parent"      -> canvasW + sm
                sTE.isNotEmpty()     -> (xPos[sTE] ?: 0f) + (ws[sTE] ?: 0f) + sm
                eTE == "parent"      -> canvasW - w - em
                eTE.isNotEmpty()     -> (xPos[eTE] ?: 0f) + (ws[eTE] ?: 0f) - w - em
                eTS == "parent"      -> -w - em
                eTS.isNotEmpty()     -> (xPos[eTS] ?: 0f) - w - em
                else                 -> 0f
            }
        }

        for (id in yOrder) {
            val el = elsJson.getJSONObject(id)
            val tm = el.optDouble("topMargin", 0.0).toFloat()
            val bm = el.optDouble("bottomMargin", 0.0).toFloat()
            val h = hs[id] ?: 0f
            val tTT = el.optString("topToTop")
            val tTB = el.optString("topToBottom")
            val bTB = el.optString("bottomToBottom")
            val bTT = el.optString("bottomToTop")
            yPos[id] = when {
                tTT == "parent"      -> topOffset + tm
                tTT.isNotEmpty()     -> (yPos[tTT] ?: 0f) + tm
                tTB == "parent"      -> canvasH + tm
                tTB.isNotEmpty()     -> (yPos[tTB] ?: 0f) + (hs[tTB] ?: 0f) + tm
                bTB == "parent"      -> canvasH - h - bm
                bTB.isNotEmpty()     -> (yPos[bTB] ?: 0f) + (hs[bTB] ?: 0f) - h - bm
                bTT == "parent"      -> -h - bm
                bTT.isNotEmpty()     -> (yPos[bTT] ?: 0f) - h - bm
                else                 -> 0f
            }
        }

        return elIds.associateWith { id ->
            GamepadElementRect(
                left = xPos[id] ?: 0f,
                top = yPos[id] ?: 0f,
                w = ws[id] ?: 0f,
                h = hs[id] ?: 0f,
            )
        }
    }
}
