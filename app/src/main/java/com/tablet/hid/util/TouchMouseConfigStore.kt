package com.tablet.hid.util

import android.content.Context
import android.util.Xml
import com.tablet.hid.model.ButtonZoneConfig
import com.tablet.hid.model.ClickBehavior
import com.tablet.hid.model.KeyboardMacroButtonConfig
import com.tablet.hid.model.MacroHostDefaults
import com.tablet.hid.model.Profile
import com.tablet.hid.model.TouchMode
import com.tablet.hid.model.TouchMouseConfig
import com.tablet.hid.model.TouchMouseSubRegionConfig
import com.tablet.hid.model.ZoneType

object TouchMouseConfigStore {

    fun prefsName(profile: Profile) = "touch_mouse_config_${profile.key}"

    fun save(context: Context, config: TouchMouseConfig, profile: Profile) {
        val prefs = context.getSharedPreferences(prefsName(profile), Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("__saved", true)
            putString("mode", config.mode.name)
            putInt("sensitivity", config.sensitivity)
            putBoolean("scroll_enabled", config.scrollEnabled)
            putBoolean("invert_scroll", config.invertScroll)
            putBoolean("shared_dynamic_zone", config.sharedDynamicZone)
            putFloat("shared_dynamic_ox", config.sharedDynamicOffsetX)
            putFloat("shared_dynamic_oy", config.sharedDynamicOffsetY)
            putFloat("shared_dynamic_radius", config.sharedDynamicRadius)
            putBoolean("sniper_enabled", config.sniperEnabled)
            putFloat("sniper_left",    config.sniperLeft)
            putFloat("sniper_top",     config.sniperTop)
            putFloat("sniper_right",   config.sniperRight)
            putFloat("sniper_bottom",  config.sniperBottom)
            putFloat("sniper_divisor", config.sniperDivisor)
            putString("macro_host_defaults", config.macroHostDefaults.name)
            saveMacros(this, config.macroButtons)
            saveButton(this, "l", config.leftButton)
            saveButton(this, "r", config.rightButton)
            apply()
        }
    }

    fun load(context: Context, profile: Profile): TouchMouseConfig {
        val prefs = context.getSharedPreferences(prefsName(profile), Context.MODE_PRIVATE)
        if (!prefs.getBoolean("__saved", false)) {
            loadRawDefault(context, profile)?.let { return it }
            return builtInDefault(profile)
        }
        val defaults = TouchMouseConfig()
        return TouchMouseConfig(
            mode         = enumValueOrDefault(prefs.getString("mode", null), defaults.mode),
            sensitivity  = prefs.getInt("sensitivity", defaults.sensitivity),
            scrollEnabled = prefs.getBoolean("scroll_enabled", defaults.scrollEnabled),
            invertScroll  = prefs.getBoolean("invert_scroll", defaults.invertScroll),
            sharedDynamicZone = prefs.getBoolean("shared_dynamic_zone", defaults.sharedDynamicZone),
            sharedDynamicOffsetX = prefs.getFloat("shared_dynamic_ox", defaults.sharedDynamicOffsetX),
            sharedDynamicOffsetY = prefs.getFloat("shared_dynamic_oy", defaults.sharedDynamicOffsetY),
            sharedDynamicRadius = prefs.getFloat("shared_dynamic_radius", defaults.sharedDynamicRadius),
            leftButton   = loadButton(prefs, "l", defaults.leftButton),
            rightButton  = loadButton(prefs, "r", defaults.rightButton),
            sniperEnabled = prefs.getBoolean("sniper_enabled", defaults.sniperEnabled),
            sniperLeft    = prefs.getFloat("sniper_left",    defaults.sniperLeft),
            sniperTop     = prefs.getFloat("sniper_top",     defaults.sniperTop),
            sniperRight   = prefs.getFloat("sniper_right",   defaults.sniperRight),
            sniperBottom  = prefs.getFloat("sniper_bottom",  defaults.sniperBottom),
            sniperDivisor = prefs.getFloat("sniper_divisor", defaults.sniperDivisor),
            macroHostDefaults = enumValueOrDefault(
                prefs.getString("macro_host_defaults", null),
                defaults.macroHostDefaults,
            ),
            macroButtons = loadMacros(prefs, defaults.macroButtons),
        )
    }

    // ── Per-profile code-level defaults ─────────────────────────────────────────

    private fun builtInDefault(profile: Profile): TouchMouseConfig = when (profile.key) {
        Profile.ACCESS_ADVANCED.key -> TouchMouseConfig(sensitivity = 3)
        else -> TouchMouseConfig()
    }

    // ── Raw-resource default loader ──────────────────────────────────────────────

    private fun loadRawDefault(context: Context, profile: Profile): TouchMouseConfig? {
        val resName = "touch_mouse_config_${profile.key}"
        val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
        if (resId == 0) return null
        return try {
            val tmpPrefsName = "__raw_tm_tmp_${profile.key}"
            val editor = context.getSharedPreferences(tmpPrefsName, Context.MODE_PRIVATE).edit().clear()
            context.resources.openRawResource(resId).use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, "UTF-8")
                var tag = parser.next()
                while (tag != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (tag == org.xmlpull.v1.XmlPullParser.START_TAG) {
                        val k = parser.getAttributeValue(null, "name") ?: ""
                        when (parser.name) {
                            "string"  -> editor.putString(k, parser.nextText())
                            "boolean" -> editor.putBoolean(k, parser.getAttributeValue(null, "value") == "true")
                            "int"     -> editor.putInt(k,     parser.getAttributeValue(null, "value").toIntOrNull() ?: 0)
                            "float"   -> editor.putFloat(k,   parser.getAttributeValue(null, "value").toFloatOrNull() ?: 0f)
                        }
                    }
                    tag = parser.next()
                }
            }
            editor.apply()
            val p = context.getSharedPreferences(tmpPrefsName, Context.MODE_PRIVATE)
            val defaults = TouchMouseConfig()
            TouchMouseConfig(
                mode         = enumValueOrDefault(p.getString("mode", null), defaults.mode),
                sensitivity  = p.getInt("sensitivity", defaults.sensitivity),
                scrollEnabled = p.getBoolean("scroll_enabled", defaults.scrollEnabled),
                invertScroll = p.getBoolean("invert_scroll", defaults.invertScroll),
                sharedDynamicZone = p.getBoolean("shared_dynamic_zone", defaults.sharedDynamicZone),
                sharedDynamicOffsetX = p.getFloat("shared_dynamic_ox", defaults.sharedDynamicOffsetX),
                sharedDynamicOffsetY = p.getFloat("shared_dynamic_oy", defaults.sharedDynamicOffsetY),
                sharedDynamicRadius = p.getFloat("shared_dynamic_radius", defaults.sharedDynamicRadius),
                leftButton   = loadButton(p, "l", defaults.leftButton),
                rightButton  = loadButton(p, "r", defaults.rightButton),
                sniperEnabled = p.getBoolean("sniper_enabled", defaults.sniperEnabled),
                sniperLeft    = p.getFloat("sniper_left",    defaults.sniperLeft),
                sniperTop     = p.getFloat("sniper_top",     defaults.sniperTop),
                sniperRight   = p.getFloat("sniper_right",   defaults.sniperRight),
                sniperBottom  = p.getFloat("sniper_bottom",  defaults.sniperBottom),
                sniperDivisor = p.getFloat("sniper_divisor", defaults.sniperDivisor),
                macroHostDefaults = enumValueOrDefault(
                    p.getString("macro_host_defaults", null),
                    defaults.macroHostDefaults,
                ),
                macroButtons = loadMacros(p, defaults.macroButtons),
            )
        } catch (_: Exception) { null }
    }

    // ── SharedPreferences helpers ────────────────────────────────────────────────

    private fun saveButton(
        editor: android.content.SharedPreferences.Editor,
        prefix: String,
        btn: ButtonZoneConfig,
    ) {
        editor.putBoolean("${prefix}_enabled", btn.enabled)
        editor.putString("${prefix}_zone_type", btn.zoneType.name)
        editor.putString("${prefix}_behavior", btn.behavior.name)
        editor.putFloat("${prefix}_s_left",   btn.staticLeft)
        editor.putFloat("${prefix}_s_top",    btn.staticTop)
        editor.putFloat("${prefix}_s_right",  btn.staticRight)
        editor.putFloat("${prefix}_s_bottom", btn.staticBottom)
        editor.putFloat("${prefix}_d_ox",     btn.dynamicOffsetX)
        editor.putFloat("${prefix}_d_oy",     btn.dynamicOffsetY)
        editor.putFloat("${prefix}_d_radius", btn.dynamicRadius)
        editor.putInt("${prefix}_subregion_count", btn.subRegions.size)
        btn.subRegions.forEachIndexed { index, subRegion ->
            saveSubRegion(editor, "${prefix}_sub_$index", subRegion)
        }
    }

    private fun loadButton(
        prefs: android.content.SharedPreferences,
        prefix: String,
        defaults: ButtonZoneConfig,
    ) = ButtonZoneConfig(
        enabled        = prefs.getBoolean("${prefix}_enabled", defaults.enabled),
        zoneType       = enumValueOrDefault(prefs.getString("${prefix}_zone_type", null), defaults.zoneType),
        behavior       = enumValueOrDefault(prefs.getString("${prefix}_behavior", null), defaults.behavior),
        staticLeft     = prefs.getFloat("${prefix}_s_left", defaults.staticLeft),
        staticTop      = prefs.getFloat("${prefix}_s_top", defaults.staticTop),
        staticRight    = prefs.getFloat("${prefix}_s_right", defaults.staticRight),
        staticBottom   = prefs.getFloat("${prefix}_s_bottom", defaults.staticBottom),
        dynamicOffsetX = prefs.getFloat("${prefix}_d_ox", defaults.dynamicOffsetX),
        dynamicOffsetY = prefs.getFloat("${prefix}_d_oy", defaults.dynamicOffsetY),
        dynamicRadius  = prefs.getFloat("${prefix}_d_radius", defaults.dynamicRadius),
        subRegions     = loadSubRegions(prefs, prefix, defaults.subRegions),
    )

    private fun saveSubRegion(
        editor: android.content.SharedPreferences.Editor,
        prefix: String,
        subRegion: TouchMouseSubRegionConfig,
    ) {
        editor.putBoolean("${prefix}_enabled", subRegion.enabled)
        editor.putString("${prefix}_zone_type", subRegion.zoneType.name)
        editor.putFloat("${prefix}_s_left", subRegion.staticLeft)
        editor.putFloat("${prefix}_s_top", subRegion.staticTop)
        editor.putFloat("${prefix}_s_right", subRegion.staticRight)
        editor.putFloat("${prefix}_s_bottom", subRegion.staticBottom)
        editor.putFloat("${prefix}_d_ox", subRegion.dynamicOffsetX)
        editor.putFloat("${prefix}_d_oy", subRegion.dynamicOffsetY)
        editor.putFloat("${prefix}_d_radius", subRegion.dynamicRadius)
        editor.putInt("${prefix}_keyboard_modifiers", subRegion.keyboardModifiers)
        editor.putString("${prefix}_alternate_mouse_button", subRegion.alternateMouseButton?.name)
    }

    private fun loadSubRegions(
        prefs: android.content.SharedPreferences,
        prefix: String,
        defaults: List<TouchMouseSubRegionConfig>,
    ): List<TouchMouseSubRegionConfig> {
        val count = prefs.getInt("${prefix}_subregion_count", defaults.size)
        if (count <= 0) return emptyList()
        return List(count) { index ->
            val subPrefix = "${prefix}_sub_$index"
            val default = defaults.getOrNull(index) ?: TouchMouseSubRegionConfig()
            TouchMouseSubRegionConfig(
                enabled = prefs.getBoolean("${subPrefix}_enabled", default.enabled),
                zoneType = enumValueOrDefault(
                    prefs.getString("${subPrefix}_zone_type", null),
                    default.zoneType,
                ),
                staticLeft = prefs.getFloat("${subPrefix}_s_left", default.staticLeft),
                staticTop = prefs.getFloat("${subPrefix}_s_top", default.staticTop),
                staticRight = prefs.getFloat("${subPrefix}_s_right", default.staticRight),
                staticBottom = prefs.getFloat("${subPrefix}_s_bottom", default.staticBottom),
                dynamicOffsetX = prefs.getFloat("${subPrefix}_d_ox", default.dynamicOffsetX),
                dynamicOffsetY = prefs.getFloat("${subPrefix}_d_oy", default.dynamicOffsetY),
                dynamicRadius = prefs.getFloat("${subPrefix}_d_radius", default.dynamicRadius),
                keyboardModifiers = prefs.getInt(
                    "${subPrefix}_keyboard_modifiers",
                    default.keyboardModifiers,
                ),
                alternateMouseButton = nullableEnumValueOrDefault(
                    prefs.getString("${subPrefix}_alternate_mouse_button", null),
                    default.alternateMouseButton,
                ),
            )
        }
    }

    private fun saveMacros(
        editor: android.content.SharedPreferences.Editor,
        macros: List<KeyboardMacroButtonConfig>,
    ) {
        editor.putInt("macro_count", macros.size)
        macros.forEachIndexed { index, macro ->
            val prefix = "macro_$index"
            editor.putString("${prefix}_label", macro.label)
            editor.putInt("${prefix}_modifiers", macro.modifiers)
            editor.putString("${prefix}_keys", macro.keyUsages.joinToString(","))
            editor.putFloat("${prefix}_lox", macro.layoutOffsetX)
            editor.putFloat("${prefix}_loy", macro.layoutOffsetY)
            editor.putFloat("${prefix}_lsx", macro.layoutScaleX)
            editor.putFloat("${prefix}_lsy", macro.layoutScaleY)
        }
    }

    private fun loadMacros(
        prefs: android.content.SharedPreferences,
        defaults: List<KeyboardMacroButtonConfig>,
    ): List<KeyboardMacroButtonConfig> {
        val count = prefs.getInt("macro_count", defaults.size)
        if (count <= 0) return emptyList()
        return List(count) { index ->
            val prefix = "macro_$index"
            val default = defaults.getOrNull(index) ?: KeyboardMacroButtonConfig("", 0, emptyList())
            KeyboardMacroButtonConfig(
                label = prefs.getString("${prefix}_label", default.label) ?: default.label,
                modifiers = prefs.getInt("${prefix}_modifiers", default.modifiers),
                keyUsages = prefs.getString("${prefix}_keys", null)
                    ?.split(',')
                    ?.mapNotNull { it.toIntOrNull() }
                    ?: default.keyUsages,
                layoutOffsetX = prefs.getFloat("${prefix}_lox", default.layoutOffsetX),
                layoutOffsetY = prefs.getFloat("${prefix}_loy", default.layoutOffsetY),
                layoutScaleX  = prefs.getFloat("${prefix}_lsx", default.layoutScaleX),
                layoutScaleY  = prefs.getFloat("${prefix}_lsy", default.layoutScaleY),
            )
        }.filter { it.label.isNotBlank() && it.keyUsages.isNotEmpty() }
    }

    private inline fun <reified T : Enum<T>> nullableEnumValueOrDefault(name: String?, default: T?): T? =
        if (name != null) runCatching { enumValueOf<T>(name) }.getOrDefault(default) else default

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
        if (name != null) runCatching { enumValueOf<T>(name) }.getOrDefault(default) else default
}
