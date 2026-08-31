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
        if (!isAllowedPath(context, path)) {
            return "ERROR:仅允许识别应用缓存、应用文件目录、外部私有目录或公共下载/图片目录中的图片"
        }
        return ocr.ocrImageFile(path)
    }

    private fun isAllowedPath(context: ToolContext, path: String): Boolean {
        val normalized = runCatching { java.io.File(path).canonicalPath }.getOrNull() ?: return false
        val app = context.appContext
        val roots = buildList {
            runCatching { add(app.cacheDir.canonicalPath) }
            runCatching { add(app.filesDir.canonicalPath) }
            runCatching { app.getExternalFilesDir(null)?.let { add(it.canonicalPath) } }
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                add("/storage/emulated/0/Download")
                add("/storage/emulated/0/Pictures")
            } else {
                runCatching {
                    add(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).canonicalPath)
                    add(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).canonicalPath)
                }
            }
        }
        return roots.any { normalized == it || normalized.startsWith(it + "/") }
    }
}
