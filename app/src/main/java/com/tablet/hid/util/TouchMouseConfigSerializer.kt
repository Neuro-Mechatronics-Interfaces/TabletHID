package com.tablet.hid.util

import com.tablet.hid.model.ButtonZoneConfig
import com.tablet.hid.model.ClickBehavior
import com.tablet.hid.model.KeyboardMacroButtonConfig
import com.tablet.hid.model.MacroHostDefaults
import com.tablet.hid.model.MouseButton
import com.tablet.hid.model.TouchMode
import com.tablet.hid.model.TouchMouseConfig
import com.tablet.hid.model.TouchMouseSubRegionConfig
import com.tablet.hid.model.ZoneType
import org.json.JSONArray
import org.json.JSONObject

object TouchMouseConfigSerializer {

    fun toCanonicalJson(config: TouchMouseConfig): JSONObject {
        val root = JSONObject()
        root.put("mode", config.mode.name)
        root.put("sensitivity", config.sensitivity)
        root.put("scrollEnabled", config.scrollEnabled)
        root.put("invertScroll", config.invertScroll)
        root.put("sharedDynamicZone", config.sharedDynamicZone)
        root.put("sharedDynamic", JSONObject().apply {
            put("offsetX", config.sharedDynamicOffsetX.toDouble())
            put("offsetY", config.sharedDynamicOffsetY.toDouble())
            put("radius",  config.sharedDynamicRadius.toDouble())
        })
        root.put("leftButton",  buttonZoneToJson(config.leftButton))
        root.put("rightButton", buttonZoneToJson(config.rightButton))
        root.put("sniper", JSONObject().apply {
            put("enabled", config.sniperEnabled)
            put("zone", JSONObject().apply {
                put("left",   config.sniperLeft.toDouble())
                put("top",    config.sniperTop.toDouble())
                put("right",  config.sniperRight.toDouble())
                put("bottom", config.sniperBottom.toDouble())
            })
            put("divisor", config.sniperDivisor.toDouble())
        })
        root.put("macroHostDefaults", config.macroHostDefaults.name)
        root.put("macroButtons", macrosToJson(config.macroButtons))
        return root
    }

    fun fromCanonicalJson(json: JSONObject): TouchMouseConfig {
        val defaults = TouchMouseConfig()
        val sharedDynamic = json.optJSONObject("sharedDynamic")
        val leftButton    = json.optJSONObject("leftButton")
        val rightButton   = json.optJSONObject("rightButton")
        val sniper        = json.optJSONObject("sniper")
        val sniperZone    = sniper?.optJSONObject("zone")
        return TouchMouseConfig(
            mode          = enumValueOrDefault(json.optString("mode"), defaults.mode),
            sensitivity   = json.optInt("sensitivity", defaults.sensitivity),
            scrollEnabled = json.optBoolean("scrollEnabled", defaults.scrollEnabled),
            invertScroll  = json.optBoolean("invertScroll", defaults.invertScroll),
            sharedDynamicZone = json.optBoolean("sharedDynamicZone", defaults.sharedDynamicZone),
            sharedDynamicOffsetX = sharedDynamic?.optDouble("offsetX", defaults.sharedDynamicOffsetX.toDouble())?.toFloat()
                                   ?: defaults.sharedDynamicOffsetX,
            sharedDynamicOffsetY = sharedDynamic?.optDouble("offsetY", defaults.sharedDynamicOffsetY.toDouble())?.toFloat()
                                   ?: defaults.sharedDynamicOffsetY,
            sharedDynamicRadius  = sharedDynamic?.optDouble("radius", defaults.sharedDynamicRadius.toDouble())?.toFloat()
                                   ?: defaults.sharedDynamicRadius,
            leftButton  = buttonZoneFromJson(leftButton, defaults.leftButton),
            rightButton = buttonZoneFromJson(rightButton, defaults.rightButton),
            sniperEnabled = sniper?.optBoolean("enabled", defaults.sniperEnabled) ?: defaults.sniperEnabled,
            sniperLeft    = sniperZone?.optDouble("left",   defaults.sniperLeft.toDouble())?.toFloat()   ?: defaults.sniperLeft,
            sniperTop     = sniperZone?.optDouble("top",    defaults.sniperTop.toDouble())?.toFloat()    ?: defaults.sniperTop,
            sniperRight   = sniperZone?.optDouble("right",  defaults.sniperRight.toDouble())?.toFloat()  ?: defaults.sniperRight,
            sniperBottom  = sniperZone?.optDouble("bottom", defaults.sniperBottom.toDouble())?.toFloat() ?: defaults.sniperBottom,
            sniperDivisor = sniper?.optDouble("divisor", defaults.sniperDivisor.toDouble())?.toFloat()   ?: defaults.sniperDivisor,
            macroHostDefaults = enumValueOrDefault(
                json.optString("macroHostDefaults"),
                defaults.macroHostDefaults,
            ),
            macroButtons = macrosFromJson(json.optJSONArray("macroButtons")),
        )
    }

    private fun buttonZoneToJson(btn: ButtonZoneConfig): JSONObject {
        val j = JSONObject()
        j.put("enabled", btn.enabled)
        j.put("zoneType", btn.zoneType.name)
        j.put("behavior", btn.behavior.name)
        j.put("staticZone", JSONObject().apply {
            put("left",   btn.staticLeft.toDouble())
            put("top",    btn.staticTop.toDouble())
            put("right",  btn.staticRight.toDouble())
            put("bottom", btn.staticBottom.toDouble())
        })
        j.put("dynamicZone", JSONObject().apply {
            put("offsetX", btn.dynamicOffsetX.toDouble())
            put("offsetY", btn.dynamicOffsetY.toDouble())
            put("radius",  btn.dynamicRadius.toDouble())
        })
        val subArr = JSONArray()
        btn.subRegions.forEach { subArr.put(subRegionToJson(it)) }
        j.put("subRegions", subArr)
        return j
    }

    private fun buttonZoneFromJson(j: JSONObject?, defaults: ButtonZoneConfig): ButtonZoneConfig {
        if (j == null) return defaults
        val staticZone  = j.optJSONObject("staticZone")
        val dynamicZone = j.optJSONObject("dynamicZone")
        val subArr      = j.optJSONArray("subRegions")
        return ButtonZoneConfig(
            enabled        = j.optBoolean("enabled", defaults.enabled),
            zoneType       = enumValueOrDefault(j.optString("zoneType"), defaults.zoneType),
            behavior       = enumValueOrDefault(j.optString("behavior"), defaults.behavior),
            staticLeft     = staticZone?.optDouble("left",   defaults.staticLeft.toDouble())?.toFloat()   ?: defaults.staticLeft,
            staticTop      = staticZone?.optDouble("top",    defaults.staticTop.toDouble())?.toFloat()    ?: defaults.staticTop,
            staticRight    = staticZone?.optDouble("right",  defaults.staticRight.toDouble())?.toFloat()  ?: defaults.staticRight,
            staticBottom   = staticZone?.optDouble("bottom", defaults.staticBottom.toDouble())?.toFloat() ?: defaults.staticBottom,
            dynamicOffsetX = dynamicZone?.optDouble("offsetX", defaults.dynamicOffsetX.toDouble())?.toFloat() ?: defaults.dynamicOffsetX,
            dynamicOffsetY = dynamicZone?.optDouble("offsetY", defaults.dynamicOffsetY.toDouble())?.toFloat() ?: defaults.dynamicOffsetY,
            dynamicRadius  = dynamicZone?.optDouble("radius",  defaults.dynamicRadius.toDouble())?.toFloat()  ?: defaults.dynamicRadius,
            subRegions     = if (subArr != null) List(subArr.length()) { i -> subRegionFromJson(subArr.getJSONObject(i)) } else defaults.subRegions,
        )
    }

    private fun subRegionToJson(sub: TouchMouseSubRegionConfig): JSONObject {
        val j = JSONObject()
        j.put("enabled", sub.enabled)
        j.put("zoneType", sub.zoneType.name)
        j.put("staticZone", JSONObject().apply {
            put("left",   sub.staticLeft.toDouble())
            put("top",    sub.staticTop.toDouble())
            put("right",  sub.staticRight.toDouble())
            put("bottom", sub.staticBottom.toDouble())
        })
        j.put("dynamicZone", JSONObject().apply {
            put("offsetX", sub.dynamicOffsetX.toDouble())
            put("offsetY", sub.dynamicOffsetY.toDouble())
            put("radius",  sub.dynamicRadius.toDouble())
        })
        j.put("keyboardModifiers", sub.keyboardModifiers)
        if (sub.alternateMouseButton != null) {
            j.put("alternateMouseButton", sub.alternateMouseButton.name)
        } else {
            j.put("alternateMouseButton", JSONObject.NULL)
        }
        return j
    }

    private fun subRegionFromJson(j: JSONObject): TouchMouseSubRegionConfig {
        val defaults    = TouchMouseSubRegionConfig()
        val staticZone  = j.optJSONObject("staticZone")
        val dynamicZone = j.optJSONObject("dynamicZone")
        val altBtn      = j.optString("alternateMouseButton").takeIf { it.isNotEmpty() }
        return TouchMouseSubRegionConfig(
            enabled  = j.optBoolean("enabled", defaults.enabled),
            zoneType = enumValueOrDefault(j.optString("zoneType"), defaults.zoneType),
            staticLeft     = staticZone?.optDouble("left",   defaults.staticLeft.toDouble())?.toFloat()   ?: defaults.staticLeft,
            staticTop      = staticZone?.optDouble("top",    defaults.staticTop.toDouble())?.toFloat()    ?: defaults.staticTop,
            staticRight    = staticZone?.optDouble("right",  defaults.staticRight.toDouble())?.toFloat()  ?: defaults.staticRight,
            staticBottom   = staticZone?.optDouble("bottom", defaults.staticBottom.toDouble())?.toFloat() ?: defaults.staticBottom,
            dynamicOffsetX = dynamicZone?.optDouble("offsetX", defaults.dynamicOffsetX.toDouble())?.toFloat() ?: defaults.dynamicOffsetX,
            dynamicOffsetY = dynamicZone?.optDouble("offsetY", defaults.dynamicOffsetY.toDouble())?.toFloat() ?: defaults.dynamicOffsetY,
            dynamicRadius  = dynamicZone?.optDouble("radius",  defaults.dynamicRadius.toDouble())?.toFloat()  ?: defaults.dynamicRadius,
            keyboardModifiers = j.optInt("keyboardModifiers", defaults.keyboardModifiers),
            alternateMouseButton = if (!altBtn.isNullOrEmpty() && altBtn != "null")
                runCatching { MouseButton.valueOf(altBtn) }.getOrNull()
            else null,
        )
    }

    private fun macrosToJson(macros: List<KeyboardMacroButtonConfig>): JSONArray {
        val arr = JSONArray()
        macros.forEach { macro ->
            val j = JSONObject()
            j.put("label", macro.label)
            j.put("modifiers", macro.modifiers)
            val keys = JSONArray()
            macro.keyUsages.forEach { keys.put(it) }
            j.put("keyUsages", keys)
            j.put("layoutOffsetX", macro.layoutOffsetX.toDouble())
            j.put("layoutOffsetY", macro.layoutOffsetY.toDouble())
            j.put("layoutScaleX", macro.layoutScaleX.toDouble())
            j.put("layoutScaleY", macro.layoutScaleY.toDouble())
            arr.put(j)
        }
        return arr
    }

    private fun macrosFromJson(arr: JSONArray?): List<KeyboardMacroButtonConfig> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i ->
            val j = arr.getJSONObject(i)
            val keysArr = j.optJSONArray("keyUsages")
            val keys = if (keysArr != null) List(keysArr.length()) { k -> keysArr.getInt(k) } else emptyList()
            KeyboardMacroButtonConfig(
                label         = j.optString("label", ""),
                modifiers     = j.optInt("modifiers", 0),
                keyUsages     = keys,
                layoutOffsetX = j.optDouble("layoutOffsetX", 0.0).toFloat(),
                layoutOffsetY = j.optDouble("layoutOffsetY", 0.0).toFloat(),
                layoutScaleX  = j.optDouble("layoutScaleX", 1.0).toFloat(),
                layoutScaleY  = j.optDouble("layoutScaleY", 1.0).toFloat(),
            )
        }.filter { it.label.isNotBlank() && it.keyUsages.isNotEmpty() }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
        if (!name.isNullOrEmpty()) runCatching { enumValueOf<T>(name) }.getOrDefault(default) else default
}
