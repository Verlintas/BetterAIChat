package com.betteraichat.skills.tools

import android.content.Context
import android.provider.Settings
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SetScreenTimeoutTool : DeviceTool {

    override val name = "set_screen_timeout"
    override val description = "设置屏幕自动熄灭的超时时间（秒）。需要「修改系统设置」权限。"
    override val readOnly = false
    override val parameters = schemaOf(
        "seconds" to intProp("屏幕超时秒数，如 30、60、300"),
        required = listOf("seconds")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        if (!Settings.System.canWrite(context.appContext)) {
            return "没有「修改系统设置」权限，无法设置屏幕超时。请到设置页授权后重试。"
        }
        val seconds = arguments["seconds"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: return "seconds 参数无效"
        if (seconds <= 0 || seconds > 86_400) return "seconds 需在 1-86400 之间"
        Settings.System.putInt(
            context.appContext.contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            (seconds * 1000).toInt()
        )
        return "屏幕超时已设置为 $seconds 秒"
    }
}
