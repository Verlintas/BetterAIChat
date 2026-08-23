package com.betteraichat.skills.tools

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WriteDocumentTool : DeviceTool {

    override val name = "write_document"
    override val description = "把内容保存为文档（.md / .txt / .html）到手机「下载」或「文档」目录，方便用户分享或归档。返回保存位置。"
    override val readOnly = false
    override val parameters = schemaOf(
        "content" to stringProp("文档正文内容"),
        "filename" to stringProp("文件名（含扩展名），不填则按时间自动生成"),
        "folder" to stringProp("保存位置：downloads 下载（默认） / documents 文档"),
        required = listOf("content")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val content = arguments["content"]?.jsonPrimitive?.content ?: return "content 参数无效"
        if (content.length > 200_000) return "ERROR:内容过长（最多 20 万字符）"
        val ext = arguments["filename"]?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() } ?: "文档_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.md"
        val folder = arguments["folder"]?.jsonPrimitive?.content ?: "downloads"
        val safeName = ext.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
        val appContext = context.appContext
        return withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) {
                    val resolver = appContext.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                        put(MediaStore.MediaColumns.MIME_TYPE, guessMime(safeName))
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            if (folder == "documents") Environment.DIRECTORY_DOCUMENTS else Environment.DIRECTORY_DOWNLOADS
                        )
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return@runCatching "ERROR:无法创建文档"
                    resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                        ?: return@runCatching "ERROR:无法写入文档"
                    "文档已保存：${if (folder == "documents") "文档" else "下载"}/$safeName（${content.length} 字）"
                } else {
                    val dir = Environment.getExternalStoragePublicDirectory(
                        if (folder == "documents") Environment.DIRECTORY_DOCUMENTS else Environment.DIRECTORY_DOWNLOADS
                    )
                    dir.mkdirs()
                    val file = File(dir, safeName)
                    file.writeText(content)
                    "文档已保存：${file.absolutePath}（${content.length} 字）"
                }
            }.getOrElse { e -> "ERROR:保存失败：${e.message}" }
        }
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "md" -> "text/markdown"
        "txt", "log" -> "text/plain"
        "html", "htm" -> "text/html"
        "json" -> "application/json"
        "csv" -> "text/csv"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }
}
