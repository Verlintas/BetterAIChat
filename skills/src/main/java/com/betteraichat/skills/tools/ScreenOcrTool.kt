package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.boolProp
import com.betteraichat.skills.schemaOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class ScreenOcrTool : DeviceTool {

    override val name = "screen_ocr"
    override val description = "截取当前屏幕并识别其中的文字（支持中英文）。with_coordinates=true（默认）时每行文字附带屏幕坐标 @(x,y)，可直接用 ua_tap 精确点击；纯阅读场景可传 false 获得更紧凑的文本。需要截屏授权。"
    override val readOnly = true
    override val parameters = schemaOf(
        "with_coordinates" to boolProp("是否附带每行文字的中心坐标，默认 true（便于点击）")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val ocr = context.ocrProvider ?: return "ERROR:当前版本不支持屏幕文字识别（需完整版）"
        val withBoxes = arguments["with_coordinates"]?.jsonPrimitive?.content
            ?.toBooleanStrictOrNull() ?: true
        return if (withBoxes) ocr.ocrScreenshotWithBoxes() else ocr.ocrScreenshot()
    }
}
