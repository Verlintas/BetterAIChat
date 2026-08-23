package com.betteraichat.skills.tools

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.intProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class VibrateTool : DeviceTool {

    override val name = "vibrate"
    override val description = "让手机震动提醒用户，duration 为震动时长（毫秒，默认 500）。"
    override val readOnly = false
    override val parameters = schemaOf(
        "duration" to intProp("震动时长（毫秒），默认 500")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val duration = arguments["duration"]?.jsonPrimitive?.content?.toLongOrNull()?.coerceIn(50, 10000) ?: 500L
        val appContext = context.appContext
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
        return "已震动 ${duration}ms"
    }
}
