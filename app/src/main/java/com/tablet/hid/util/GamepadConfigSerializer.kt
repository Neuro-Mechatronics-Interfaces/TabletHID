package com.tablet.hid.util

import com.tablet.hid.model.ButtonConfig
import com.tablet.hid.model.ClickBehavior
import com.tablet.hid.model.GamepadConfig
import com.tablet.hid.model.JoystickConfig
import com.tablet.hid.model.JoystickSide
import com.tablet.hid.model.KeyboardMacroButtonConfig
import com.tablet.hid.model.MacroHostDefaults
import com.tablet.hid.model.TriggerDragAxis
import com.tablet.hid.model.VibrationIntensity
import org.json.JSONArray
import org.json.JSONObject

object GamepadConfigSerializer {

    private val BUTTON_KEYS = listOf(
        "a", "b", "x", "y", "lb", "rb", "lt", "rt",
        "back", "start", "dpadUp", "dpadDown", "dpadLeft", "dpadRight",
    )

    private val TRIGGER_KEYS = setOf("lt", "rt")

    fun toCanonicalJson(config: GamepadConfig): JSONObject {
        val root = JSONObject()
        val buttons = JSONObject()
        buttons.put("a",         buttonToJson(config.btnA))
        buttons.put("b",         buttonToJson(config.btnB))
        buttons.put("x",         buttonToJson(config.btnX))
        buttons.put("y",         buttonToJson(config.btnY))
        buttons.put("lb",        buttonToJson(config.btnLb))
        buttons.put("rb",        buttonToJson(config.btnRb))
        buttons.put("lt",        buttonToJson(config.btnLt))
        buttons.put("rt",        buttonToJson(config.btnRt))
        buttons.put("back",      buttonToJson(config.btnBack))
        buttons.put("start",     buttonToJson(config.btnStart))
        buttons.put("dpadUp",    buttonToJson(config.dpadUp))
        buttons.put("dpadDown",  buttonToJson(config.dpadDown))
        buttons.put("dpadLeft",  buttonToJson(config.dpadLeft))
        buttons.put("dpadRight", buttonToJson(config.dpadRight))
        root.put("buttons", buttons)
        root.put("leftJoystick",  joystickToJson(config.leftJoystick))
        root.put("rightJoystick", joystickToJson(config.rightJoystick))
        root.put("singleJoystickMode", config.singleJoystickMode)
        root.put("singleJoystickSideToggleEnabled", config.singleJoystickSideToggleEnabled)
        root.put("singleJoystickOutputSide", config.singleJoystickOutputSide.name)
        root.put("macroHostDefaults", config.macroHostDefaults.name)
        root.put("macroButtons", macrosToJson(config.macroButtons))
        root.put("vibrationIntensity", config.vibrationIntensity.name)
        val labels = JSONObject()
        config.customButtonLabels.forEach { (k, v) -> labels.put(k, v) }
        root.put("customButtonLabels", labels)
        return root
    }

    fun fromCanonicalJson(json: JSONObject): GamepadConfig {
        val buttons = json.optJSONObject("buttons") ?: JSONObject()
        val leftJs  = json.optJSONObject("leftJoystick")
        val rightJs = json.optJSONObject("rightJoystick")
        return GamepadConfig(
            btnA     = buttonFromJson(buttons.optJSONObject("a"),         isTrigger = false),
            btnB     = buttonFromJson(buttons.optJSONObject("b"),         isTrigger = false),
            btnX     = buttonFromJson(buttons.optJSONObject("x"),         isTrigger = false),
            btnY     = buttonFromJson(buttons.optJSONObject("y"),         isTrigger = false),
            btnLb    = buttonFromJson(buttons.optJSONObject("lb"),        isTrigger = false),
            btnRb    = buttonFromJson(buttons.optJSONObject("rb"),        isTrigger = false),
            btnLt    = buttonFromJson(buttons.optJSONObject("lt"),        isTrigger = true),
            btnRt    = buttonFromJson(buttons.optJSONObject("rt"),        isTrigger = true),
            btnBack  = buttonFromJson(buttons.optJSONObject("back"),      isTrigger = false),
            btnStart = buttonFromJson(buttons.optJSONObject("start"),     isTrigger = false),
            dpadUp    = buttonFromJson(buttons.optJSONObject("dpadUp"),    isTrigger = false),
            dpadDown  = buttonFromJson(buttons.optJSONObject("dpadDown"),  isTrigger = false),
            dpadLeft  = buttonFromJson(buttons.optJSONObject("dpadLeft"),  isTrigger = false),
            dpadRight = buttonFromJson(buttons.optJSONObject("dpadRight"), isTrigger = false),
            leftJoystick  = joystickFromJson(leftJs),
            rightJoystick = joystickFromJson(rightJs),
            singleJoystickMode = json.optBoolean("singleJoystickMode", false),
            singleJoystickSideToggleEnabled = json.optBoolean("singleJoystickSideToggleEnabled", false),
            singleJoystickOutputSide = enumValueOrDefault(
                json.optString("singleJoystickOutputSide"),
                JoystickSide.LEFT,
            ),
            macroHostDefaults = enumValueOrDefault(
                json.optString("macroHostDefaults"),
                MacroHostDefaults.WINDOWS,
            ),
            macroButtons = macrosFromJson(json.optJSONArray("macroButtons")),
            vibrationIntensity = enumValueOrDefault(
                json.optString("vibrationIntensity"),
                VibrationIntensity.OFF,
            ),
            customButtonLabels = labelsFromJson(json.optJSONObject("customButtonLabels")),
        )
    }

    private fun buttonToJson(btn: ButtonConfig): JSONObject {
        val j = JSONObject()
        j.put("enabled", btn.enabled)
        j.put("behavior", btn.behavior.name)
        j.put("turbo", btn.turbo)
        j.put("turboDurationMs", btn.turboDurationMs)
        j.put("turboIntervalMs", btn.turboIntervalMs)
        j.put("offsetX", btn.offsetX.toDouble())
        j.put("offsetY", btn.offsetY.toDouble())
        j.put("scaleX", btn.scaleX.toDouble())
        j.put("scaleY", btn.scaleY.toDouble())
        j.put("triggerTravelDp", btn.triggerTravelDp.toDouble())
        j.put("triggerAxis", btn.triggerAxis.name)
        return j
    }

    private fun buttonFromJson(j: JSONObject?, isTrigger: Boolean): ButtonConfig {
        val defaults = ButtonConfig()
        if (j == null) return defaults
        return ButtonConfig(
            enabled         = j.optBoolean("enabled", defaults.enabled),
            behavior        = enumValueOrDefault(j.optString("behavior"), defaults.behavior),
            turbo           = j.optBoolean("turbo", defaults.turbo),
            turboDurationMs = j.optInt("turboDurationMs", defaults.turboDurationMs),
            turboIntervalMs = j.optInt("turboIntervalMs", defaults.turboIntervalMs),
            offsetX         = j.optDouble("offsetX", defaults.offsetX.toDouble()).toFloat(),
            offsetY         = j.optDouble("offsetY", defaults.offsetY.toDouble()).toFloat(),
            scaleX          = j.optDouble("scaleX", defaults.scaleX.toDouble()).toFloat(),
            scaleY          = j.optDouble("scaleY", defaults.scaleY.toDouble()).toFloat(),
            triggerTravelDp = if (isTrigger) j.optDouble("triggerTravelDp", defaults.triggerTravelDp.toDouble()).toFloat()
                              else defaults.triggerTravelDp,
            triggerAxis     = if (isTrigger) enumValueOrDefault(j.optString("triggerAxis"), defaults.triggerAxis)
                              else defaults.triggerAxis,
        )
    }

    private fun joystickToJson(js: JoystickConfig): JSONObject {
        val j = JSONObject()
        j.put("enabled", js.enabled)
        j.put("deadzone", js.deadzone.toDouble())
        j.put("gain", js.gain.toDouble())
        j.put("offsetX", js.offsetX.toDouble())
        j.put("offsetY", js.offsetY.toDouble())
        j.put("scaleX", js.scaleX.toDouble())
        j.put("scaleY", js.scaleY.toDouble())
        return j
    }

    private fun joystickFromJson(j: JSONObject?): JoystickConfig {
        val defaults = JoystickConfig()
        if (j == null) return defaults
        return JoystickConfig(
            enabled  = j.optBoolean("enabled", defaults.enabled),
            deadzone = j.optDouble("deadzone", defaults.deadzone.toDouble()).toFloat(),
            gain     = j.optDouble("gain", defaults.gain.toDouble()).toFloat(),
            offsetX  = j.optDouble("offsetX", defaults.offsetX.toDouble()).toFloat(),
            offsetY  = j.optDouble("offsetY", defaults.offsetY.toDouble()).toFloat(),
            scaleX   = j.optDouble("scaleX", defaults.scaleX.toDouble()).toFloat(),
            scaleY   = j.optDouble("scaleY", defaults.scaleY.toDouble()).toFloat(),
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

    private fun labelsFromJson(j: JSONObject?): Map<String, String> {
        if (j == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        j.keys().forEach { key -> map[key] = j.getString(key) }
        return map
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
        if (!name.isNullOrEmpty()) runCatching { enumValueOf<T>(name) }.getOrDefault(default) else default
}
