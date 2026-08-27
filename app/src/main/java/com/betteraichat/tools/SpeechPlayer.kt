package com.betteraichat.tools

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class SpeechPlayer(context: Context) {

    var onSpeakingDone: (() -> Unit)? = null

    private val ready = AtomicBoolean(false)
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.CHINESE
            ready.set(true)
        }
    }

    fun isReady(): Boolean = ready.get()

    fun speak(text: String): Boolean {
        if (text.isBlank()) return false
        if (!ready.get()) return false
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "betteraichat_ui")
        return result == TextToSpeech.SUCCESS
    }

    fun speakWithCallback(text: String, onDone: () -> Unit) {
        if (text.isBlank()) {
            onDone()
            return
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "betteraichat_ui") onDone()
            }
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId == "betteraichat_ui") onDone()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == "betteraichat_ui") onDone()
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == "betteraichat_ui") onDone()
            }
        })
        val started = speak(text)
        if (!started) {
            Handler(Looper.getMainLooper()).post { onDone() }
        }
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        runCatching { tts.shutdown() }
    }
}
