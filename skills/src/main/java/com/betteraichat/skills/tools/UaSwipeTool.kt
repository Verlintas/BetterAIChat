package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class UaSwipeTool : DeviceTool {

    override val name = "ua_swipe"
    override val description = "通过无障碍服务在屏幕上滑动（如上下滚动页面、翻页）。x1,y1 为起点，x2,y2 为终点，duration_ms 为滑动耗时毫秒。"
    override val readOnly = false
    override val parameters = schemaOf(
        "x1" to intProp("起点横坐标"),
        "y1" to intProp("起点纵坐标"),
        "x2" to intProp("终点横坐标"),
        "y2" to intProp("终点纵坐标"),
        "duration_ms" to intProp("滑动耗时（毫秒），默认 300"),
        required = listOf("x1", "y1", "x2", "y2")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val bridge = context.accessibility ?: return "ERROR:无障碍服务未开启，请到系统设置 → 无障碍 → 开启 BetterAIChat"
        val x1 = arguments["x1"]?.jsonPrimitive?.content?.toIntOrNull() ?: return "x1 参数无效"
        val y1 = arguments["y1"]?.jsonPrimitive?.content?.toIntOrNull() ?: return "y1 参数无效"
        val x2 = arguments["x2"]?.jsonPrimitive?.content?.toIntOrNull() ?: return "x2 参数无效"
        val y2 = arguments["y2"]?.jsonPrimitive?.content?.toIntOrNull() ?: return "y2 参数无效"
        val duration = arguments["duration_ms"]?.jsonPrimitive?.content?.toIntOrNull() ?: 300
        return bridge.swipe(x1, y1, x2, y2, duration)
    }
}
