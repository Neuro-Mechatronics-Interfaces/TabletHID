package com.tablet.hid.util

import android.content.Context
import android.util.Xml
import com.tablet.hid.model.ButtonZoneConfig
import com.tablet.hid.model.ClickBehavior
import com.tablet.hid.model.Profile
import com.tablet.hid.model.TouchMode
import com.tablet.hid.model.TouchMouseConfig
import com.tablet.hid.model.ZoneType

object TouchMouseConfigStore {

    fun prefsName(profile: Profile) = "touch_mouse_config_${profile.key}"

    fun save(context: Context, config: TouchMouseConfig, profile: Profile) {
        val prefs = context.getSharedPreferences(prefsName(profile), Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("__saved", true)
            putString("mode", config.mode.name)
            putInt("sensitivity", config.sensitivity)
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
            mode        = enumValueOrDefault(prefs.getString("mode", null), defaults.mode),
            sensitivity = prefs.getInt("sensitivity", defaults.sensitivity),
            leftButton  = loadButton(prefs, "l", defaults.leftButton),
            rightButton = loadButton(prefs, "r", defaults.rightButton),
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
                mode        = enumValueOrDefault(p.getString("mode", null), defaults.mode),
                sensitivity = p.getInt("sensitivity", defaults.sensitivity),
                leftButton  = loadButton(p, "l", defaults.leftButton),
                rightButton = loadButton(p, "r", defaults.rightButton),
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
    }

    private fun loadButton(
        prefs: android.content.SharedPreferences,
        prefix: String,
        defaults: ButtonZoneConfig,
    ) = ButtonZoneConfig(
        enabled        = prefs.getBoolean("${prefix}_enabled",   defaults.enabled),
        zoneType       = enumValueOrDefault(prefs.getString("${prefix}_zone_type", null), defaults.zoneType),
        behavior       = enumValueOrDefault(prefs.getString("${prefix}_behavior", null),  defaults.behavior),
        staticLeft     = prefs.getFloat("${prefix}_s_left",   defaults.staticLeft),
        staticTop      = prefs.getFloat("${prefix}_s_top",    defaults.staticTop),
        staticRight    = prefs.getFloat("${prefix}_s_right",  defaults.staticRight),
        staticBottom   = prefs.getFloat("${prefix}_s_bottom", defaults.staticBottom),
        dynamicOffsetX = prefs.getFloat("${prefix}_d_ox",     defaults.dynamicOffsetX),
        dynamicOffsetY = prefs.getFloat("${prefix}_d_oy",     defaults.dynamicOffsetY),
        dynamicRadius  = prefs.getFloat("${prefix}_d_radius", defaults.dynamicRadius),
    )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
        if (name != null) runCatching { enumValueOf<T>(name) }.getOrDefault(default) else default
}
