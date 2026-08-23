package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class UaTapTool : DeviceTool {

    override val name = "ua_tap"
    override val description = "通过无障碍服务点击屏幕指定坐标点（x, y 为屏幕像素坐标，分辨率可在 device_info 查询）。可配合 screen_ocr 识别屏幕后精确点击。"
    override val readOnly = false
    override val parameters = schemaOf(
        "x" to intProp("横坐标像素"),
        "y" to intProp("纵坐标像素"),
        required = listOf("x", "y")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val bridge = context.accessibility ?: return "ERROR:无障碍服务未开启，请到系统设置 → 无障碍 → 开启 BetterAIChat"
        val x = arguments["x"]?.jsonPrimitive?.content?.toIntOrNull() ?: return "x 参数无效"
        val y = arguments["y"]?.jsonPrimitive?.content?.toIntOrNull() ?: return "y 参数无效"
        return bridge.tap(x, y)
    }
}
