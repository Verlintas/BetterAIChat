package com.betteraichat.core.sse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ResponseBody

object SseParser {

    fun parse(body: ResponseBody, onEvent: suspend (event: String, data: String) -> Boolean): Flow<Unit> = flow<Unit> {
        val source = body.source()
        val dataBuffer = StringBuilder()
        var eventName = ""
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            when {
                line.isBlank() -> {
                    if (dataBuffer.isNotEmpty()) {
                        val shouldContinue = onEvent(eventName, dataBuffer.toString())
                        dataBuffer.clear()
                        eventName = ""
                        if (!shouldContinue) break
                    }
                }
                line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                line.startsWith("data:") -> {
                    val payload = line.removePrefix("data:").trimStart()
                    if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                    dataBuffer.append(payload)
                }
            }
        }
        if (dataBuffer.isNotEmpty()) onEvent(eventName, dataBuffer.toString())
    }.flowOn(Dispatchers.IO)
}
