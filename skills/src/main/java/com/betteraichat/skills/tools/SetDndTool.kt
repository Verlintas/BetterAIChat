package com.betteraichat.skills.tools

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SetDndTool : DeviceTool {

    override val name = "set_dnd"
    override val description = "开关勿扰模式（Do Not Disturb）：on 开启（仅闹钟/重要通知）、off 关闭。Android 15 以上无需额外授权，旧版本需要系统「勿扰访问权限」（可在设置页开启）。"
    override val readOnly = false
    override val parameters = schemaOf(
        "state" to stringProp("on 开启勿扰 / off 关闭勿扰"),
        required = listOf("state")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val state = arguments["state"]?.jsonPrimitive?.content ?: return "state 参数无效"
        val nm = context.appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return when (state) {
            "on" -> {
                runCatching {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                }.getOrElse { e ->
                    return "ERROR:开启勿扰失败：${e.message}（Android 15 以下需在系统设置 → 通知 → 勿扰访问权限中允许 BetterAIChat）"
                }
                "勿扰模式已开启（仅重要通知/闹钟）"
            }
            "off" -> {
                runCatching {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }.getOrElse { e ->
                    return "ERROR:关闭勿扰失败：${e.message}（Android 15 以下需在系统设置 → 通知 → 勿扰访问权限中允许 BetterAIChat）"
                }
                "勿扰模式已关闭"
            }
            else -> "state 无效，可选：on / off"
        }
    }
}
