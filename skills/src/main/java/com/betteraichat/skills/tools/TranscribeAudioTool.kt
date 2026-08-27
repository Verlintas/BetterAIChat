package com.betteraichat.skills.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

class TranscribeAudioTool : DeviceTool {

    override val name = "transcribe_audio"
    override val description = "用麦克风录音并转成文字（在线语音识别）。适合会议纪要、听写、语音备忘。duration 为录音秒数（默认 15，最多 60）。需要录音权限。"
    override val readOnly = false
    override val parameters = schemaOf(
        "duration" to intProp("录音秒数，默认 15，最多 60"),
        required = emptyList()
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val duration = (arguments["duration"]?.jsonPrimitive?.content?.toIntOrNull() ?: 15).coerceIn(1, 60)
        val appContext = context.appContext
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "ERROR:缺少录音权限，请到设置页授权录音权限"
        }
        return withContext(Dispatchers.Main) {
            runCatching {
                val result = CompletableDeferred<String>()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                val listener = object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "未听清任何内容"
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络错误（语音识别需要网络）"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到语音"
                            else -> "语音识别失败（$error）"
                        }
                        if (!result.isCompleted) result.complete("ERROR:$msg")
                    }
                    override fun onResults(results: Bundle?) {
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (!result.isCompleted) result.complete(text?.takeIf { it.isNotBlank() } ?: "ERROR:未识别到文字")
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }
                recognizer.setRecognitionListener(listener)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toString())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, duration * 1000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, duration * 1000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1_000L)
                }
                try {
                    recognizer.startListening(intent)
                } catch (e: Exception) {
                    recognizer.destroy()
                    return@runCatching "ERROR:语音识别不可用：${e.message}"
                }
                val text = withTimeoutOrNull((duration + 15) * 1000L) { result.await() }
                    ?: "ERROR:录音超时"
                recognizer.destroy()
                if (text.startsWith("ERROR:")) text else "录音转写完成（${duration} 秒）：$text"
            }.getOrElse { e -> "ERROR:录音转写失败：${e.message}" }
        }
    }
}
