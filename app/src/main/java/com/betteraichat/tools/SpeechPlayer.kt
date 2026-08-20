package com.betteraichat.tools

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class SpeechPlayer(context: Context) {

    var onSpeakingDone: (() -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            this.tts.language = Locale.CHINESE
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "betteraichat_ui")
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
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == "betteraichat_ui") onDone()
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == "betteraichat_ui") onDone()
            }
        })
        speak(text)
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }
}
