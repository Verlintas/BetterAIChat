package com.betteraichat.skills.tools

import android.content.Context
import android.os.PowerManager
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class KeepScreenOnTool : DeviceTool {

    override val name = "keep_screen_on"
    override val description = "让屏幕保持常亮指定时长（秒），期间不熄屏。适合让 AI 执行需要看着屏幕的任务，或用户阅读长文。时长最多 600 秒。"
    override val readOnly = false
    override val parameters = schemaOf(
        "seconds" to intProp("常亮时长（秒），默认 60，最多 600"),
        required = emptyList()
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val seconds = (arguments["seconds"]?.jsonPrimitive?.content?.toIntOrNull() ?: 60).coerceIn(1, 600)
        val pm = context.appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "betteraichat:keep_screen_on"
        )
        wakeLock.setReferenceCounted(false)
        wakeLock.acquire(seconds * 1000L)
        return "屏幕将保持常亮 ${seconds} 秒"
    }
}
