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
    override val description = "调整媒体音量，percent 为 0-100 的百分比。"
    override val readOnly = false
    override val parameters = schemaOf(
        "percent" to intProp("音量百分比，0-100"),
        required = listOf("percent")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val percent = arguments["percent"]?.jsonPrimitive?.content?.toIntOrNull()
            ?.coerceIn(0, 100) ?: return "percent 参数无效"
        val audio = context.appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = max * percent / 100
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        return "媒体音量已调整为 $percent%"
    }
}
