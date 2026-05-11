package com.tablet.hid.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Xml
import com.tablet.hid.model.ButtonConfig
import com.tablet.hid.model.ClickBehavior
import com.tablet.hid.model.GamepadConfig
import com.tablet.hid.model.JoystickConfig
import com.tablet.hid.model.JoystickSide
import com.tablet.hid.model.KeyboardMacroButtonConfig
import com.tablet.hid.model.MacroHostDefaults
import com.tablet.hid.model.Profile
import com.tablet.hid.model.TriggerDragAxis
import com.tablet.hid.model.VibrationIntensity

object GamepadConfigStore {

    // Returns the SharedPreferences file name for a given profile.
    fun prefsName(profile: Profile) = "gamepad_config_${profile.key}"

    fun save(context: Context, config: GamepadConfig, profile: Profile) {
        val p = context.getSharedPreferences(prefsName(profile), Context.MODE_PRIVATE).edit()
        p.putBoolean("__saved", true)   // sentinel so we know this profile has been configured
        btn(p, "a",      config.btnA)
        btn(p, "b",      config.btnB)
        btn(p, "x",      config.btnX)
        btn(p, "y",      config.btnY)
        btn(p, "lb",     config.btnLb)
        btn(p, "rb",     config.btnRb)
        btn(p, "lt",     config.btnLt)
        btn(p, "rt",     config.btnRt)
        btn(p, "back",   config.btnBack)
        btn(p, "start",  config.btnStart)
        btn(p, "dup",    config.dpadUp)
        btn(p, "ddown",  config.dpadDown)
        btn(p, "dleft",  config.dpadLeft)
        btn(p, "dright", config.dpadRight)
        joy(p, "left",   config.leftJoystick)
        joy(p, "right",  config.rightJoystick)
        p.putBoolean("single_joystick_mode", config.singleJoystickMode)
        p.putBoolean("single_joystick_side_toggle_enabled", config.singleJoystickSideToggleEnabled)
        p.putString("single_joystick_output_side", config.singleJoystickOutputSide.name)
        p.putString("macro_host_defaults", config.macroHostDefaults.name)
        macros(p, config.macroButtons)
        p.putString("vibration_intensity", config.vibrationIntensity.name)
        BUTTON_LABEL_KEYS.forEach { key ->
            val label = config.customButtonLabels[key]
            if (label != null) p.putString("label_$key", label) else p.remove("label_$key")
        }
        p.apply()
    }

    fun load(context: Context, profile: Profile): GamepadConfig {
        val p = context.getSharedPreferences(prefsName(profile), Context.MODE_PRIVATE)
        // Fall back to bundled raw-resource default if this profile has never been saved.
        // (DEV workflow: configure → export → place XML in res/raw/gamepad_config_<key>.xml)
        if (!p.getBoolean("__saved", false)) {
            loadRawDefault(context, profile)?.let { return it }
            return builtInDefault(profile)
        }
        return GamepadConfig(
            btnA     = btn(p, "a"),
            btnB     = btn(p, "b"),
            btnX     = btn(p, "x"),
            btnY     = btn(p, "y"),
            btnLb    = btn(p, "lb"),
            btnRb    = btn(p, "rb"),
            btnLt    = btn(p, "lt"),
            btnRt    = btn(p, "rt"),
            btnBack  = btn(p, "back"),
            btnStart = btn(p, "start"),
            dpadUp    = btn(p, "dup"),
            dpadDown  = btn(p, "ddown"),
            dpadLeft  = btn(p, "dleft"),
            dpadRight = btn(p, "dright"),
            leftJoystick  = joy(p, "left"),
            rightJoystick = joy(p, "right"),
            singleJoystickMode = p.getBoolean("single_joystick_mode", false),
            singleJoystickSideToggleEnabled = p.getBoolean("single_joystick_side_toggle_enabled", false),
            singleJoystickOutputSide = joystickSide(p.getString("single_joystick_output_side", null)),
            macroHostDefaults = macroHostDefaults(p.getString("macro_host_defaults", null)),
            macroButtons = macros(p),
            vibrationIntensity = vibrationIntensity(p.getString("vibration_intensity", null)),
            customButtonLabels = loadLabelMap(p),
        )
    }

    // ── Per-profile code-level defaults ─────────────────────────────────────────
    // Customize these to pre-configure each built-in profile.
    // Custom profiles always get the data-class defaults.
    private fun builtInDefault(profile: Profile): GamepadConfig = when (profile.key) {
        Profile.ACCESS_BASIC.key -> GamepadConfig(
            // Simpler layout: disable triggers and D-pad by default
            btnLt = ButtonConfig(enabled = false),
            btnRt = ButtonConfig(enabled = false),
            dpadUp    = ButtonConfig(enabled = false),
            dpadDown  = ButtonConfig(enabled = false),
            dpadLeft  = ButtonConfig(enabled = false),
            dpadRight = ButtonConfig(enabled = false),
        )
        Profile.ACCESS_ADVANCED.key -> GamepadConfig(
            // All face/shoulder buttons default to latching for reduced hold effort
            btnA     = ButtonConfig(behavior = ClickBehavior.LATCHING),
            btnB     = ButtonConfig(behavior = ClickBehavior.LATCHING),
            btnX     = ButtonConfig(behavior = ClickBehavior.LATCHING),
            btnY     = ButtonConfig(behavior = ClickBehavior.LATCHING),
            btnLb    = ButtonConfig(behavior = ClickBehavior.LATCHING),
            btnRb    = ButtonConfig(behavior = ClickBehavior.LATCHING),
        )
        else -> GamepadConfig()
    }

    // ── Raw-resource default loader ──────────────────────────────────────────────
    // Reads a SharedPreferences-format XML from res/raw/gamepad_config_<key>.xml
    // and populates a GamepadConfig from it. Returns null if the resource doesn't exist.
    private fun loadRawDefault(context: Context, profile: Profile): GamepadConfig? {
        val resName = "gamepad_config_${profile.key}"
        val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
        if (resId == 0) return null
        return try {
            val prefs = context.getSharedPreferences("__raw_tmp_${profile.key}", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            val editor = prefs.edit()
            context.resources.openRawResource(resId).use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, "UTF-8")
                var tag = parser.next()
                while (tag != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (tag == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "string") {
                        val k = parser.getAttributeValue(null, "name")
                        val v = parser.nextText()
                        editor.putString(k, v)
                    } else if (tag == org.xmlpull.v1.XmlPullParser.START_TAG &&
                               (parser.name == "boolean" || parser.name == "int" || parser.name == "float")) {
                        val k = parser.getAttributeValue(null, "name")
                        val v = parser.getAttributeValue(null, "value")
                        when (parser.name) {
                            "boolean" -> editor.putBoolean(k, v == "true")
                            "int"     -> editor.putInt(k, v.toIntOrNull() ?: 0)
                            "float"   -> editor.putFloat(k, v.toFloatOrNull() ?: 0f)
                        }
                    }
                    tag = parser.next()
                }
            }
            editor.apply()
            val p = context.getSharedPreferences("__raw_tmp_${profile.key}", Context.MODE_PRIVATE)
            GamepadConfig(
                btnA  = btn(p, "a"),   btnB  = btn(p, "b"),
                btnX  = btn(p, "x"),   btnY  = btn(p, "y"),
                btnLb = btn(p, "lb"),  btnRb = btn(p, "rb"),
                btnLt = btn(p, "lt"),  btnRt = btn(p, "rt"),
                btnBack  = btn(p, "back"), btnStart = btn(p, "start"),
                dpadUp    = btn(p, "dup"),   dpadDown  = btn(p, "ddown"),
                dpadLeft  = btn(p, "dleft"), dpadRight = btn(p, "dright"),
                leftJoystick  = joy(p, "left"),
                rightJoystick = joy(p, "right"),
                singleJoystickMode = p.getBoolean("single_joystick_mode", false),
                singleJoystickSideToggleEnabled = p.getBoolean("single_joystick_side_toggle_enabled", false),
                singleJoystickOutputSide = joystickSide(p.getString("single_joystick_output_side", null)),
                macroHostDefaults = macroHostDefaults(p.getString("macro_host_defaults", null)),
                macroButtons = macros(p),
                vibrationIntensity = vibrationIntensity(p.getString("vibration_intensity", null)),
                customButtonLabels = loadLabelMap(p),
            )
        } catch (_: Exception) { null }
    }

    // ── SharedPreferences helpers ────────────────────────────────────────────────

    private fun btn(p: SharedPreferences.Editor, k: String, c: ButtonConfig) {
        p.putBoolean("${k}_en",  c.enabled)
        p.putString("${k}_beh",  c.behavior.name)
        p.putBoolean("${k}_trb", c.turbo)
        p.putInt("${k}_trd",     c.turboDurationMs)
        p.putInt("${k}_tri",     c.turboIntervalMs)
        p.putFloat("${k}_ox",    c.offsetX)
        p.putFloat("${k}_oy",    c.offsetY)
        p.putFloat("${k}_scx",   c.scaleX)
        p.putFloat("${k}_scy",   c.scaleY)
        p.putFloat("${k}_ttd",   c.triggerTravelDp)
        p.putString("${k}_tax",  c.triggerAxis.name)
    }

    private fun btn(p: SharedPreferences, k: String): ButtonConfig = ButtonConfig(
        enabled         = p.getBoolean("${k}_en",  true),
        behavior        = runCatching { ClickBehavior.valueOf(p.getString("${k}_beh", "") ?: "") }
                            .getOrDefault(ClickBehavior.MOMENTARY),
        turbo           = p.getBoolean("${k}_trb", false),
        turboDurationMs = p.getInt("${k}_trd",    50),
        turboIntervalMs = p.getInt("${k}_tri",    100),
        offsetX         = p.getFloat("${k}_ox",    0f),
        offsetY         = p.getFloat("${k}_oy",    0f),
        // Backward compat: fall back to old uniform _sc if new split keys absent
        scaleX          = p.getFloat("${k}_scx",   p.getFloat("${k}_sc", 1f)),
        scaleY          = p.getFloat("${k}_scy",   p.getFloat("${k}_sc", 1f)),
        triggerTravelDp = p.getFloat("${k}_ttd",   150f),
        triggerAxis     = runCatching { TriggerDragAxis.valueOf(p.getString("${k}_tax", "") ?: "") }
                            .getOrDefault(TriggerDragAxis.UP),
    )

    private fun joy(p: SharedPreferences.Editor, k: String, c: JoystickConfig) {
        p.putBoolean("${k}_en",  c.enabled)
        p.putFloat("${k}_dz",   c.deadzone)
        p.putFloat("${k}_gn",   c.gain)
        p.putFloat("${k}_ox",   c.offsetX)
        p.putFloat("${k}_oy",   c.offsetY)
        p.putFloat("${k}_scx",  c.scaleX)
        p.putFloat("${k}_scy",  c.scaleY)
    }

    private fun joy(p: SharedPreferences, k: String): JoystickConfig = JoystickConfig(
        enabled  = p.getBoolean("${k}_en",  true),
        deadzone = p.getFloat("${k}_dz",   0.08f),
        gain     = p.getFloat("${k}_gn",   1.0f),
        offsetX  = p.getFloat("${k}_ox",   0f),
        offsetY  = p.getFloat("${k}_oy",   0f),
        scaleX   = p.getFloat("${k}_scx",  p.getFloat("${k}_sc", 1f)),
        scaleY   = p.getFloat("${k}_scy",  p.getFloat("${k}_sc", 1f)),
    )

    private fun joystickSide(value: String?): JoystickSide =
        runCatching { JoystickSide.valueOf(value ?: "") }.getOrDefault(JoystickSide.LEFT)

    private fun macroHostDefaults(value: String?): MacroHostDefaults =
        runCatching { MacroHostDefaults.valueOf(value ?: "") }.getOrDefault(MacroHostDefaults.WINDOWS)

    private fun vibrationIntensity(value: String?): VibrationIntensity =
        runCatching { VibrationIntensity.valueOf(value ?: "") }.getOrDefault(VibrationIntensity.OFF)

    private fun macros(p: SharedPreferences.Editor, macros: List<KeyboardMacroButtonConfig>) {
        p.putInt("macro_count", macros.size)
        macros.forEachIndexed { index, macro ->
            val k = "macro_$index"
            p.putString("${k}_label", macro.label)
            p.putInt("${k}_modifiers", macro.modifiers)
            p.putString("${k}_keys", macro.keyUsages.joinToString(","))
            p.putFloat("${k}_lox", macro.layoutOffsetX)
            p.putFloat("${k}_loy", macro.layoutOffsetY)
            p.putFloat("${k}_lsx", macro.layoutScaleX)
            p.putFloat("${k}_lsy", macro.layoutScaleY)
        }
    }

    val BUTTON_LABEL_KEYS = listOf(
        "a", "b", "x", "y", "lb", "rb", "lt", "rt", "back", "start",
        "dup", "ddown", "dleft", "dright"
    )

    private fun loadLabelMap(p: SharedPreferences): Map<String, String> =
        BUTTON_LABEL_KEYS.mapNotNull { key ->
            val v = p.getString("label_$key", null)
            if (!v.isNullOrBlank()) key to v else null
        }.toMap()

    private fun macros(p: SharedPreferences): List<KeyboardMacroButtonConfig> {
        val count = p.getInt("macro_count", 0)
        if (count <= 0) return emptyList()
        return List(count) { index ->
            val k = "macro_$index"
            KeyboardMacroButtonConfig(
                label = p.getString("${k}_label", "") ?: "",
                modifiers = p.getInt("${k}_modifiers", 0),
                keyUsages = p.getString("${k}_keys", null)
                    ?.split(',')
                    ?.mapNotNull { it.toIntOrNull() }
                    ?: emptyList(),
                layoutOffsetX = p.getFloat("${k}_lox", 0f),
                layoutOffsetY = p.getFloat("${k}_loy", 0f),
                layoutScaleX  = p.getFloat("${k}_lsx", 1f),
                layoutScaleY  = p.getFloat("${k}_lsy", 1f),
            )
        }.filter { it.label.isNotBlank() && it.keyUsages.isNotEmpty() }
    }
}
