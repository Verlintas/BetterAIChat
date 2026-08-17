package com.betteraichat.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap): String
}

object DocParser {

    private const val MAX_OUTPUT_CHARS = 300_000
    private const val MAX_PDF_PAGES = 5
    private const val OCR_SCALE = 2f

    suspend fun extractPdf(context: Context, uri: Uri, name: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cacheFile = File(context.cacheDir, "doc_${System.currentTimeMillis()}.pdf")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalArgumentException("无法读取 PDF 文件")
                    val pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    try {
                        val pageCount = renderer.pageCount
                        if (pageCount <= 0) throw IllegalArgumentException("PDF 没有页面")
                        val pageLimit = minOf(pageCount, MAX_PDF_PAGES)
                        val recognizer: OcrEngine = OcrEngineProvider.get()
                        val sb = StringBuilder()
                        for (i in 0 until pageLimit) {
                            renderer.openPage(i).use { page ->
                                val width = (page.width * OCR_SCALE).toInt().coerceAtLeast(1)
                                val height = (page.height * OCR_SCALE).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                try {
                                    page.render(
                                        bitmap, null,
                                        Matrix().apply { setScale(OCR_SCALE, OCR_SCALE) },
                                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                    )
                                    val text = recognize(recognizer, bitmap)
                                    sb.appendLine("【第 ${i + 1} 页 / 共 $pageCount 页】")
                                    sb.appendLine(text.trim())
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                        if (pageLimit < pageCount) {
                            sb.appendLine("\n（文档共 $pageCount 页，仅解析了前 $pageLimit 页）")
                        }
                        truncate(sb.toString())
                    } finally {
                        renderer.close()
                        pfd.close()
                    }
                } finally {
                    cacheFile.delete()
                }
            }
        }

    suspend fun extractDocx(context: Context, uri: Uri, name: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cacheFile = File(context.cacheDir, "doc_${System.currentTimeMillis()}.docx")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalArgumentException("无法读取 docx 文件")
                    ZipFile(cacheFile).use { zip ->
                        val entry = zip.getEntry("word/document.xml")
                            ?: throw IllegalArgumentException("docx 结构异常：缺少 document.xml")
                        val xml = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
                        val text = xml
                            .replace(Regex("<w:tab[^>]*/>"), "\t")
                            .replace(Regex("<w:br[^>]*/>"), "\n")
                            .replace(Regex("</w:p>"), "\n")
                            .replace(Regex("<w:t[^>]*>"), "")
                            .replace(Regex("</w:t>"), "\u0001")
                            .replace(Regex("<[^>]+>"), "")
                            .replace("\u0001", "")
                            .replace(Regex("\\n{3,}"), "\n\n")
                            .trim()
                        if (text.isBlank()) throw IllegalArgumentException("docx 中未提取到文本内容")
                        truncate(text)
                    }
                } finally {
                    cacheFile.delete()
                }
            }
        }

    suspend fun extractXlsx(context: Context, uri: Uri, name: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cacheFile = File(context.cacheDir, "doc_${System.currentTimeMillis()}.xlsx")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalArgumentException("无法读取 xlsx 文件")
                    ZipFile(cacheFile).use { zip ->
                        val sharedStrings = parseSharedStrings(zip)
                        val sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml")
                            ?: zip.getEntry("xl/worksheets/sheet.xml")
                            ?: throw IllegalArgumentException("xlsx 结构异常：缺少工作表")
                        val sheetXml = zip.getInputStream(sheetEntry).readBytes().toString(Charsets.UTF_8)
                        val rows = mutableListOf<String>()
                        Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
                            .findAll(sheetXml)
                            .forEach { rowMatch ->
                                val cells = mutableMapOf<Int, String>()
                                Regex(
                                    "<c[^>]*r=\"([A-Z]+)\\d+\"[^>]*t=\"([^\"]+)\"[^>]*>(.*?)</c>|<c[^>]*r=\"([A-Z]+)\\d+\"[^>]*>(.*?)</c>",
                                    RegexOption.DOT_MATCHES_ALL
                                ).findAll(rowMatch.groupValues[1]).forEach { cellMatch ->
                                    val colLetters = cellMatch.groupValues[1].ifEmpty { cellMatch.groupValues[4] }
                                    val type = cellMatch.groupValues[2]
                                    val body = cellMatch.groupValues[3].ifEmpty { cellMatch.groupValues[5] }
                                    val value = when {
                                        type == "s" -> Regex("<v>([^<]*)</v>").find(body)
                                            ?.groupValues?.get(1)?.toIntOrNull()
                                            ?.let { sharedStrings.getOrNull(it) } ?: ""
                                        else -> Regex("<v>([^<]*)</v>").find(body)
                                            ?.groupValues?.get(1) ?: ""
                                    }
                                    cells[colIndex(colLetters)] = value
                                }
                                val maxCol = cells.keys.maxOrNull() ?: 0
                                rows.add((0..maxCol).joinToString("\t") { cells[it] ?: "" })
                            }
                        if (rows.isEmpty()) throw IllegalArgumentException("xlsx 中没有数据")
                        truncate(rows.joinToString("\n"))
                    }
                } finally {
                    cacheFile.delete()
                }
            }
        }

    private fun parseSharedStrings(zip: ZipFile): List<String> {
        val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        val xml = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
        return Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .map { si ->
                si.groupValues[1]
                    .replace(Regex("<t[^>]*>"), "")
                    .replace(Regex("</t>"), "")
                    .replace(Regex("<[^>]+>"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            .toList()
    }

    private fun colIndex(letters: String): Int {
        var index = 0
        for (ch in letters) {
            index = index * 26 + (ch - 'A' + 1)
        }
        return index - 1
    }

    private suspend fun recognize(
        recognizer: OcrEngine,
        bitmap: Bitmap
    ): String = recognizer.recognize(bitmap)

    private fun truncate(text: String): String =
        if (text.length > MAX_OUTPUT_CHARS) {
            text.take(MAX_OUTPUT_CHARS) + "\n…（内容过长，已截断，共 ${text.length} 字符）"
        } else text
}
