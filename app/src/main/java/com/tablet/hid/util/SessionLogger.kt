package com.tablet.hid.util

import android.content.Context
import com.tablet.hid.model.ButtonZoneConfig
import com.tablet.hid.model.ButtonConfig
import com.tablet.hid.model.DeviceMode
import com.tablet.hid.model.GamepadConfig
import com.tablet.hid.model.JoystickConfig
import com.tablet.hid.model.TouchMouseConfig
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Creates a .config + .log file pair at session start.
 * Config is a one-shot INI-style snapshot of all parameters.
 * Log receives one timestamped line per HID report via a background thread.
 */
class SessionLogger(
    context: Context,
    mode: DeviceMode,
    profileName: String,
    touchConfig: TouchMouseConfig?,
    gamepadConfig: GamepadConfig?,
) {
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "hid-logger")
    }
    private val writer: BufferedWriter
    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)

    @Volatile private var closed = false

    init {
        val dir = LoggingStore.sessionDir(context).also { it.mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val stem = "${stamp}_${mode.name.lowercase()}"

        File(dir, "$stem.config").bufferedWriter().use { w ->
            w.write(buildConfig(mode, profileName, touchConfig, gamepadConfig))
        }

        writer = File(dir, "$stem.log").bufferedWriter()
        val ts = now()
        executor.execute { writer.write("$ts SESSION_START mode=${mode.name}\n") }
    }

    fun logMouse(buttons: Int, dx: Int, dy: Int, wheel: Int) {
        if (closed) return
        val ts = now()
        executor.execute {
            writer.write("$ts MOUSE buttons=$buttons dx=$dx dy=$dy wheel=$wheel\n")
        }
    }

    fun logGamepad(lx: Int, ly: Int, rx: Int, ry: Int, lt: Int, rt: Int, buttons: Int, hat: Int) {
        if (closed) return
        val ts = now()
        executor.execute {
            writer.write("$ts GAMEPAD lx=$lx ly=$ly rx=$rx ry=$ry lt=$lt rt=$rt buttons=$buttons hat=$hat\n")
        }
    }

    fun close() {
        if (closed) return
        closed = true
        val ts = now()
        executor.execute {
            try {
                writer.write("$ts SESSION_END\n")
                writer.flush()
                writer.close()
            } catch (_: Exception) {}
        }
        executor.shutdown()
    }

    private fun now(): String = isoFmt.format(Date())

    // ── Config file builder ──────────────────────────────────────────────────

    private fun buildConfig(
        mode: DeviceMode,
        profileName: String,
        touch: TouchMouseConfig?,
        gamepad: GamepadConfig?,
    ) = buildString {
        val now = now()
        appendLine("# TabletHID Session Config")
        appendLine("# Generated: $now")
        appendLine("# Mode: ${mode.displayName}")
        appendLine()
        appendLine("[session]")
        appendLine("mode = ${mode.name}")
        appendLine("profile = $profileName")
        appendLine("generated = $now")
        appendLine()

        if (touch != null) {
            appendLine("[touch_mouse]")
            appendLine("input_mode = ${touch.mode.name}")
            appendLine("sensitivity = ${touch.sensitivity}")
            appendLine()
            appendButtonZone("left_button", touch.leftButton)
            appendButtonZone("right_button", touch.rightButton)
        }

        if (gamepad != null) {
            appendButton("btn_a",      gamepad.btnA,      trigger = false)
            appendButton("btn_b",      gamepad.btnB,      trigger = false)
            appendButton("btn_x",      gamepad.btnX,      trigger = false)
            appendButton("btn_y",      gamepad.btnY,      trigger = false)
            appendButton("btn_lb",     gamepad.btnLb,     trigger = false)
            appendButton("btn_rb",     gamepad.btnRb,     trigger = false)
            appendButton("btn_lt",     gamepad.btnLt,     trigger = true)
            appendButton("btn_rt",     gamepad.btnRt,     trigger = true)
            appendButton("btn_back",   gamepad.btnBack,   trigger = false)
            appendButton("btn_start",  gamepad.btnStart,  trigger = false)
            appendButton("dpad_up",    gamepad.dpadUp,    trigger = false)
            appendButton("dpad_down",  gamepad.dpadDown,  trigger = false)
            appendButton("dpad_left",  gamepad.dpadLeft,  trigger = false)
            appendButton("dpad_right", gamepad.dpadRight, trigger = false)
            appendJoystick("left_joystick",  gamepad.leftJoystick)
            appendJoystick("right_joystick", gamepad.rightJoystick)
        }
    }

    private fun StringBuilder.appendButtonZone(name: String, b: ButtonZoneConfig) {
        appendLine("[$name]")
        appendLine("enabled = ${b.enabled}")
        appendLine("zone_type = ${b.zoneType.name}")
        appendLine("behavior = ${b.behavior.name}")
        appendLine("static_left = ${"%.3f".format(b.staticLeft)}")
        appendLine("static_top = ${"%.3f".format(b.staticTop)}")
        appendLine("static_right = ${"%.3f".format(b.staticRight)}")
        appendLine("static_bottom = ${"%.3f".format(b.staticBottom)}")
        appendLine("dynamic_offset_x = ${"%.3f".format(b.dynamicOffsetX)}")
        appendLine("dynamic_offset_y = ${"%.3f".format(b.dynamicOffsetY)}")
        appendLine("dynamic_radius = ${"%.3f".format(b.dynamicRadius)}")
        appendLine()
    }

    private fun StringBuilder.appendButton(name: String, b: ButtonConfig, trigger: Boolean) {
        appendLine("[$name]")
        appendLine("enabled = ${b.enabled}")
        appendLine("behavior = ${b.behavior.name}")
        appendLine("turbo = ${b.turbo}")
        appendLine("turbo_duration_ms = ${b.turboDurationMs}")
        appendLine("turbo_interval_ms = ${b.turboIntervalMs}")
        if (trigger) {
            appendLine("trigger_travel_dp = ${"%.1f".format(b.triggerTravelDp)}")
            appendLine("trigger_axis = ${b.triggerAxis.name}")
        }
        appendLine("offset_x = ${"%.2f".format(b.offsetX)}")
        appendLine("offset_y = ${"%.2f".format(b.offsetY)}")
        appendLine("scale_x = ${"%.3f".format(b.scaleX)}")
        appendLine("scale_y = ${"%.3f".format(b.scaleY)}")
        appendLine()
    }

    private fun StringBuilder.appendJoystick(name: String, j: JoystickConfig) {
        appendLine("[$name]")
        appendLine("enabled = ${j.enabled}")
        appendLine("deadzone = ${"%.3f".format(j.deadzone)}")
        appendLine("gain = ${"%.3f".format(j.gain)}")
        appendLine("offset_x = ${"%.2f".format(j.offsetX)}")
        appendLine("offset_y = ${"%.2f".format(j.offsetY)}")
        appendLine("scale_x = ${"%.3f".format(j.scaleX)}")
        appendLine("scale_y = ${"%.3f".format(j.scaleY)}")
        appendLine()
    }
}
