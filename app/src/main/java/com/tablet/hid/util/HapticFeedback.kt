package com.tablet.hid.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.tablet.hid.model.VibrationIntensity

object HapticFeedback {

    fun vibrate(context: Context, intensity: VibrationIntensity) {
        if (intensity == VibrationIntensity.OFF) return
        val (durationMs, amplitude) = when (intensity) {
            VibrationIntensity.LIGHT  -> 18L to  60
            VibrationIntensity.MEDIUM -> 30L to 140
            VibrationIntensity.STRONG -> 50L to 255
            VibrationIntensity.OFF    -> return
        }
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
    }
}
