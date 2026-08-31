package com.betteraichat.skills.tools

import android.content.ContentValues
import android.content.Context
import android.net.Uri
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
import java.net.HttpURLConnection
import java.net.URL

class DownloadFileTool : DeviceTool {

    override val name = "download_file"
    override val description = "从 URL 下载文件保存到手机「下载」目录（支持任意类型，自动从文件名推断扩展名）。返回保存位置。"
    override val readOnly = false
    override val parameters = schemaOf(
        "url" to stringProp("文件下载地址"),
        "filename" to stringProp("保存文件名（含扩展名），不填则从 URL 推断"),
        required = listOf("url")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val urlStr = arguments["url"]?.jsonPrimitive?.content ?: return "url 参数无效"
        val appContext = context.appContext
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(urlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 120_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "BetterAIChat/0.18")
                }
                try {
                    conn.connect()
                    if (conn.responseCode !in 200..299) {
                        return@runCatching "ERROR:下载失败 HTTP ${conn.responseCode}"
                    }
                    val size = conn.contentLengthLong
                    if (size > 100L * 1024 * 1024) {
                        return@runCatching "ERROR:文件过大（${size / 1024 / 1024}MB），已拒绝下载（上限 100MB）"
                    }
                    val fileName = arguments["filename"]?.jsonPrimitive?.content
                        ?.takeIf { it.isNotBlank() }
                        ?: inferName(url, conn)
                    conn.inputStream.use { input ->
                        saveToDownloads(appContext, fileName, input, size)
                    }
                    val sizeText = if (size > 0) "，${size / 1024 / 1024}MB" else ""
                    "下载完成：$fileName$sizeText"
                } finally {
                    conn.disconnect()
                }
            }.getOrElse { e -> "ERROR:下载失败：${e.message}" }
        }
    }

    private fun inferName(url: URL, conn: HttpURLConnection): String {
        val fromHeader = conn.getHeaderField("Content-Disposition")
            ?.let { Regex("filename=\"?([^\";]+)\"?").find(it)?.groupValues?.getOrNull(1) }
        if (!fromHeader.isNullOrBlank()) return fromHeader
        val path = url.path
        val name = path.substringAfterLast('/').ifBlank { "download" }
        return if (name.contains('.')) name else "$name.bin"
    }

    private fun saveToDownloads(
        context: Context,
        fileName: String,
        input: java.io.InputStream,
        declaredSize: Long
    ): String {
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
        var written = 0L
        fun copy(out: java.io.OutputStream) {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                written += n
                if (written > 100L * 1024 * 1024) {
                    throw IllegalStateException("下载超过 100MB 上限")
                }
                out.write(buffer, 0, n)
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, guessMime(safeName))
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return "ERROR:无法创建下载文件"
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    copy(out)
                } ?: return "ERROR:无法写入下载文件"
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            return "下载/${safeName}"
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = File(dir, safeName)
            file.outputStream().use { out ->
                copy(out)
            }
            return file.absolutePath
        }
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "apk" -> "application/vnd.android.package-archive"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "txt", "md", "log" -> "text/plain"
        else -> "application/octet-stream"
    }
}
