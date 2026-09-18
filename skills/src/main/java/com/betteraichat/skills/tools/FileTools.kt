package com.betteraichat.skills.tools

import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.isAllowedFilePath
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ListFilesTool : DeviceTool {

    override val name = "list_files"
    override val description = "列出手机「下载」或「文档」目录中的文件（名称/大小/修改时间），可选子路径。用于找文件、确认下载结果。仅限应用目录与公共下载/文档/图片目录。"
    override val readOnly = true
    override val parameters = schemaOf(
        "path" to stringProp("目录路径（可选，默认下载目录 /storage/emulated/0/Download）"),
        "limit" to intProp("最多列出多少项，默认 30，最多 100")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val path = arguments["path"]?.jsonPrimitive?.content?.trim().takeIf { !it.isNullOrBlank() }
            ?: "/storage/emulated/0/Download"
        if (!isAllowedFilePath(context.appContext, path)) {
            return "ERROR:仅允许访问应用目录与公共下载/文档/图片目录"
        }
        val limit = (arguments["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 30).coerceIn(1, 100)
        val dir = File(path)
        if (!dir.exists()) return "目录不存在：$path"
        if (!dir.isDirectory) return "不是目录：$path"
        val files = dir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenByDescending { it.lastModified() }
        ) ?: return "无法读取目录：$path"
        if (files.isEmpty()) return "目录为空：$path"
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return buildString {
            appendLine("$path（共 ${files.size} 项，显示前 ${minOf(limit, files.size)}）：")
            files.take(limit).forEach { f ->
                val size = if (f.isDirectory) "<目录>" else formatSize(f.length())
                appendLine("${f.name}  $size  ${fmt.format(Date(f.lastModified()))}")
            }
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1fMB".format(bytes / 1024.0 / 1024)
        bytes >= 1024 -> "%.0fKB".format(bytes / 1024.0)
        else -> "${bytes}B"
    }
}

class ReadTextFileTool : DeviceTool {

    override val name = "read_text_file"
    override val description = "读取文本文件内容（≤1MB），如 .txt/.md/.log/.json/.csv。用于查看下载的日志、文档等。仅限应用目录与公共下载/文档/图片目录。"
    override val readOnly = true
    override val parameters = schemaOf(
        "path" to stringProp("文件完整路径"),
        "max_chars" to intProp("最多读取字符数，默认 3000，最多 6000")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val path = arguments["path"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (path.isBlank()) return "path 不能为空"
        if (!isAllowedFilePath(context.appContext, path)) {
            return "ERROR:仅允许读取应用目录与公共下载/文档/图片目录中的文件"
        }
        val maxChars = (arguments["max_chars"]?.jsonPrimitive?.content?.toIntOrNull() ?: 3000).coerceIn(500, 6000)
        val file = File(path)
        if (!file.exists()) return "文件不存在：$path"
        if (file.isDirectory) return "这是一个目录，请用 list_files 查看：$path"
        if (file.length() > 1024 * 1024) return "文件过大（${file.length() / 1024}KB），仅支持读取 ≤1MB 的文本文件"
        val text = runCatching { file.readText(Charsets.UTF_8) }
            .getOrElse { return "读取失败：${it.message}" }
        val excerpt = text.take(maxChars) + if (text.length > maxChars) "\n…（已截断，共 ${text.length} 字符）" else ""
        return "文件：${file.name}（${file.length()}B）\n内容：\n$excerpt"
    }
}
