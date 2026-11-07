package com.betteraichat.core.chat

import com.betteraichat.core.model.ChatMessage
import com.betteraichat.core.model.ChatRole

/**
 * Repairs a transcript before it is sent to a provider: every tool call in an
 * assistant message must be followed by a tool message with the same id.
 * Missing results (stop / process death / older bugs) are filled with an
 * explicit cancellation result. Blank tool-call ids are normalized so the
 * synthetic result can be matched.
 */
fun repairToolTranscript(history: List<ChatMessage>): List<ChatMessage> {
    val resultIds = history.asSequence()
        .filter { it.role == ChatRole.TOOL }
        .mapNotNull { it.toolCallId }
        .toHashSet()
    val out = ArrayList<ChatMessage>(history.size + 4)
    history.forEachIndexed { index, msg ->
        if (msg.role == ChatRole.ASSISTANT && msg.toolCalls.isNotEmpty()) {
            val calls = msg.toolCalls.mapIndexed { callIndex, call ->
                if (call.id.isBlank()) call.copy(id = "recovered_${index}_$callIndex") else call
            }
            out += if (calls == msg.toolCalls) msg else msg.copy(toolCalls = calls)
            calls.filter { it.id !in resultIds }.forEach { call ->
                out += ChatMessage(
                    role = ChatRole.TOOL,
                    content = "已取消（上一轮中断，自动补齐）",
                    toolCallId = call.id,
                    toolName = call.name
                )
            }
        } else {
            out += msg
        }
    }
    return out
}
