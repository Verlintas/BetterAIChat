package com.betteraichat.tools

import android.graphics.BitmapFactory
import com.betteraichat.skills.OcrProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ScreenOcr(private val screenshotManager: ScreenshotManager) : OcrProvider {

    override suspend fun ocrScreenshot(): String {
        val shot = screenshotManager.capture()
        if (shot.startsWith("ERROR")) return shot
        val path = Regex("截屏成功：(.*?)[（(]").find(shot)?.groupValues?.getOrNull(1)
            ?: return "ERROR:无法定位截图文件"
        return runCatching {
            val bitmap = BitmapFactory.decodeFile(path)
                ?: return@runCatching "ERROR:截图文件读取失败"
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitTask()
            val text = result.text.trim()
            recognizer.close()
            if (text.isEmpty()) {
                "屏幕文字识别完成：屏幕上没有识别到文字"
            } else {
                "屏幕文字识别结果（共 ${text.length} 字）：\n$text"
            }
        }.getOrElse { e -> "ERROR:文字识别失败：${e.message}" }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(it) }
            addOnFailureListener { cont.resumeWith(Result.failure(it)) }
        }
}
