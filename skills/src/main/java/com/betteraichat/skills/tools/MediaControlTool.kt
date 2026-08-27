package com.betteraichat.skills.tools

import android.content.Context
import android.media.session.MediaSessionManager
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class MediaControlTool : DeviceTool {

    override val name = "media_control"
    override val description = "控制正在播放的媒体（音乐/视频）：action 为 play 播放、pause 暂停、next 下一曲、previous 上一曲。无需特殊权限。"
    override val readOnly = false
    override val parameters = schemaOf(
        "action" to stringProp("操作：play / pause / next / previous"),
        required = listOf("action")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val action = arguments["action"]?.jsonPrimitive?.content ?: return "action 参数无效"
        val manager = context.appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions = try {
            manager.getActiveSessions(null)
        } catch (e: Exception) {
            return "ERROR:无法读取媒体会话（Android 11+ 需在系统设置开启「通知使用权」才能控制其他应用的播放）"
        }
        val controller = sessions.takeIf { it.isNotEmpty() }?.firstOrNull()
        val controls = controller?.transportControls
        if (controls == null) {
            val flat = android.provider.Settings.Secure.getString(
                context.appContext.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""
            val hasAccess = android.os.Build.VERSION.SDK_INT < 31 || flat.split(':').any {
                android.content.ComponentName.unflattenFromString(it)?.packageName ==
                    context.appContext.packageName
            }
            return if (hasAccess) {
                "当前没有正在播放的媒体会话，无法控制"
            } else {
                "ERROR:未发现媒体会话。Android 11+ 需在系统设置开启 BetterAIChat 的「通知使用权」才能控制其他应用的播放"
            }
        }
        when (action) {
            "play" -> controls.play()
            "pause" -> controls.pause()
            "next" -> controls.skipToNext()
            "previous" -> controls.skipToPrevious()
            else -> return "action 无效，可选：play / pause / next / previous"
        }
        return "已发送媒体控制：$action"
    }
}
