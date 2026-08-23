package com.betteraichat.skills.tools

import android.content.Context
import android.media.AudioManager
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class RingerModeTool : DeviceTool {

    override val name = "set_ringer_mode"
    override val description = "切换手机铃声模式：normal 响铃、vibrate 振动、silent 静音。"
    override val readOnly = false
    override val parameters = schemaOf(
        "mode" to stringProp("模式：normal / vibrate / silent"),
        required = listOf("mode")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val mode = arguments["mode"]?.jsonPrimitive?.content ?: return "mode 参数无效"
        val audio = context.appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mapped = when (mode) {
            "normal" -> AudioManager.RINGER_MODE_NORMAL
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            "silent" -> AudioManager.RINGER_MODE_SILENT
            else -> return "mode 无效，可选：normal / vibrate / silent"
        }
        audio.setRingerMode(mapped)
        val label = when (mapped) {
            AudioManager.RINGER_MODE_NORMAL -> "响铃"
            AudioManager.RINGER_MODE_VIBRATE -> "振动"
            else -> "静音"
        }
        return "铃声模式已切换为「$label」"
    }
}
