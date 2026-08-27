package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class ScreenRecordTool : DeviceTool {

    override val name = "screen_record"
    override val description = "录制屏幕为 MP4 视频并保存到「下载」目录。duration 为录制秒数（默认 10，最多 120）。需要先完成「截屏授权」。用于录操作教程、演示步骤等。"
    override val readOnly = false
    override val parameters = schemaOf(
        "duration" to intProp("录制秒数，默认 10，最多 120"),
        required = emptyList()
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val recorder = context.screenRecorder
            ?: return "ERROR:当前版本不支持录屏"
        val seconds = (arguments["duration"]?.jsonPrimitive?.content?.toIntOrNull() ?: 10).coerceIn(1, 120)
        return recorder.record(seconds)
    }
}
