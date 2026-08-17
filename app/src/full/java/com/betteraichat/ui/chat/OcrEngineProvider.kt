package com.betteraichat.ui.chat

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.CompletableDeferred

object OcrEngineProvider {

    fun get(): OcrEngine = MlKitOcrEngine

    private object MlKitOcrEngine : OcrEngine {
        override suspend fun recognize(bitmap: Bitmap): String {
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            try {
                val deferred = CompletableDeferred<String>()
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result -> deferred.complete(result.text) }
                    .addOnFailureListener { e -> deferred.completeExceptionally(e) }
                return deferred.await()
            } finally {
                recognizer.close()
            }
        }
    }
}
