package com.betteraichat.skills.tools

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import com.betteraichat.skills.intProp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.EncodeHintType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.util.Hashtable

class GenerateQrTool : DeviceTool {

    override val name = "generate_qr"
    override val description = "把文本或链接生成二维码图片，保存到手机「图片/Download 二维码」目录，并返回保存路径（可配合分享使用）。"
    override val readOnly = false
    override val parameters = schemaOf(
        "content" to stringProp("二维码内容（文本或网址）"),
        "size" to intProp("二维码边长像素，默认 512，最大 2048"),
        required = listOf("content")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val content = arguments["content"]?.jsonPrimitive?.content ?: return@runCatching "content 参数无效"
                if (content.length > 4000) return@runCatching "ERROR:内容过长（最多 4000 字符）"
                val size = (arguments["size"]?.jsonPrimitive?.content?.toIntOrNull() ?: 512).coerceIn(128, 2048)
                val hints = Hashtable<EncodeHintType, Any>().apply {
                    put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                    put(EncodeHintType.MARGIN, 1)
                }
                val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
                for (x in 0 until size) {
                    for (y in 0 until size) {
                        bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                    }
                }
                val dir = File(context.appContext.getExternalFilesDir(null), "qr").apply { mkdirs() }
                val file = File(dir, "qr_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()
                "二维码已生成：${file.absolutePath}（${size}x${size}px）"
            }.getOrElse { e -> "ERROR:生成失败：${e.message}" }
        }
}
