package com.betteraichat.ui.chat

import android.graphics.Bitmap

object OcrEngineProvider {

    fun get(): OcrEngine = LiteStubOcrEngine

    private object LiteStubOcrEngine : OcrEngine {
        override suspend fun recognize(bitmap: Bitmap): String {
            throw IllegalStateException("当前为精简版（lite），不含 OCR 识别模型。PDF 识别请安装完整版（full）。")
        }
    }
}
