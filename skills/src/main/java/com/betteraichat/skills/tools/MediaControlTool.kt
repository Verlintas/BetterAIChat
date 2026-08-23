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
        val sessions = manager.getActiveSessions(null)
        val controller = sessions.takeIf { it.isNotEmpty() }?.firstOrNull()
        val controls = controller?.transportControls
        if (controls == null) {
            return "当前没有正在播放的媒体会话，无法控制"
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
