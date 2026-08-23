package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import kotlinx.serialization.json.JsonObject

class ScreenOcrTool : DeviceTool {

    override val name = "screen_ocr"
    override val description = "截取当前屏幕并识别其中的文字（支持中英文）。用于让 AI「读」屏幕上显示的内容（配合 ua_tap 可精确操作界面）。需要截屏授权。"
    override val readOnly = true
    override val parameters = com.betteraichat.skills.schemaOf()

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val ocr = context.ocrProvider ?: return "ERROR:当前版本不支持屏幕文字识别（需完整版）"
        return ocr.ocrScreenshot()
    }
}
