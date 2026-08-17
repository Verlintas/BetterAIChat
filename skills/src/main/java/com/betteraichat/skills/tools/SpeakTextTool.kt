package com.betteraichat.skills.tools

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.betteraichat.skills.DeviceTool
import com.betteraichat.skills.ToolContext
import com.betteraichat.skills.intProp
import com.betteraichat.skills.schemaOf
import com.betteraichat.skills.stringProp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

class SpeakTextTool : DeviceTool {

    override val name = "speak_text"
    override val description = "用语音朗读指定文本（TTS）。适合朗读通知、文章摘要、提醒内容等。"
    override val readOnly = false
    override val parameters = schemaOf(
        "text" to stringProp("要朗读的文本"),
        "rate" to intProp("语速 50-200，默认 100"),
        required = listOf("text")
    )

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val text = arguments["text"]?.jsonPrimitive?.content?.trim()
            ?: return "缺少 text 参数"
        if (text.isBlank()) return "text 不能为空"
        val rate = (arguments["rate"]?.jsonPrimitive?.content?.toIntOrNull() ?: 100).coerceIn(50, 200)
        val done = CompletableDeferred<String>()
        val ttsRef = arrayOfNulls<TextToSpeech>(1)
        val tts = TextToSpeech(context.appContext) { status ->
            val engine = ttsRef[0]
            if (status == TextToSpeech.SUCCESS && engine != null) {
                engine.language = Locale.CHINESE
                engine.setSpeechRate(rate / 100f)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (!done.isCompleted) done.complete("朗读完成")
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (!done.isCompleted) done.complete("朗读中断")
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (!done.isCompleted) done.complete("朗读失败（错误码 $errorCode）")
                    }
                })
                val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "betteraichat_tts")
                if (result != TextToSpeech.SUCCESS) {
                    done.complete("TTS 引擎无法朗读")
                }
            } else {
                done.complete("TTS 引擎初始化失败（设备可能缺少语音包）")
            }
        }
        ttsRef[0] = tts
        return withTimeoutOrNull(90_000) { done.await() } ?: run {
            tts.stop()
            "朗读超时"
        }
    }
}
