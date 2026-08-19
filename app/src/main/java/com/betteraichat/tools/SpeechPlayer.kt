package com.betteraichat.tools

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class SpeechPlayer(context: Context) {

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            this.tts.language = Locale.CHINESE
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "betteraichat_ui")
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }
}
