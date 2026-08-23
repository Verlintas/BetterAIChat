package com.betteraichat.tools

import com.betteraichat.skills.OcrProvider
import kotlinx.serialization.json.JsonObject

class ScreenOcr(private val screenshotManager: ScreenshotManager) : OcrProvider {

    override suspend fun ocrScreenshot(): String =
        "ERROR:当前为精简版，不支持屏幕文字识别（screen_ocr），请使用完整版"
}
