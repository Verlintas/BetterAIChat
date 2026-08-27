package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class OcrFileTool : DeviceTool {

    override val name = "ocr_file"
    override val description = "识别图片文件中的文字（中英文，端侧识别）。path 为图片文件路径（可用 download_file 下载或 take_screenshot 截屏获取）。用于提取截图、照片、文档图片中的文字。"
    override val readOnly = true
    override val parameters = schemaOf(
        "path" to stringProp("图片文件路径"),
        required = listOf("path")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val ocr = context.ocrProvider ?: return "ERROR:当前版本不支持文字识别（需完整版）"
        val path = arguments["path"]?.jsonPrimitive?.content ?: return "path 参数无效"
        return ocr.ocrImageFile(path)
    }
}
