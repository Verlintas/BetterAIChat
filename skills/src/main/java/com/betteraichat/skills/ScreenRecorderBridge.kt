package com.betteraichat.skills

fun interface ScreenRecorderBridge {
    suspend fun record(seconds: Int): String
}
