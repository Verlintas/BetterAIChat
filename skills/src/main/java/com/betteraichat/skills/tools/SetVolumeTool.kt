package com.betteraichat.skills.tools

import android.content.Context
import android.media.AudioManager
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SetVolumeTool : DeviceTool {

    override val name = "set_volume"
    override val description = "调整音量，percent 为 0-100 的百分比；stream 指定音量类型（media 媒体/ring 铃声/notification 通知/alarm 闹钟/call 通话，默认 media）。"
    override val readOnly = false
    override val parameters = schemaOf(
        "percent" to intProp("音量百分比，0-100"),
        "stream" to com.betteraichat.skills.stringProp("音量类型：media/ring/notification/alarm/call，默认 media"),
        required = listOf("percent")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val percent = arguments["percent"]?.jsonPrimitive?.content?.toIntOrNull()
            ?.coerceIn(0, 100) ?: return "percent 参数无效"
        val audio = context.appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val streamName = arguments["stream"]?.jsonPrimitive?.content?.trim()?.lowercase() ?: "media"
        val stream = when (streamName) {
            "ring", "铃声" -> AudioManager.STREAM_RING
            "notification", "通知" -> AudioManager.STREAM_NOTIFICATION
            "alarm", "闹钟" -> AudioManager.STREAM_ALARM
            "call", "通话" -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }
        val label = when (stream) {
            AudioManager.STREAM_RING -> "铃声"
            AudioManager.STREAM_NOTIFICATION -> "通知"
            AudioManager.STREAM_ALARM -> "闹钟"
            AudioManager.STREAM_VOICE_CALL -> "通话音"
            else -> "媒体"
        }
        val max = audio.getStreamMaxVolume(stream)
        val target = max * percent / 100
        audio.setStreamVolume(stream, target, 0)
        return "${label}音量已调整为 $percent%"
    }
}
