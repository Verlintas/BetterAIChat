package com.betteraichat.core.chat

import com.betteraichat.core.model.ChatMessage
import com.betteraichat.core.model.ChatRole

/**
 * Repairs a transcript before it is sent to a provider:
 * - every tool call in an assistant message gets a matching tool result;
 * - missing results are filled with an explicit cancellation;
 * - results are emitted in tool-call order (Gemini matches parallel
 *   responses positionally) right after the assistant message;
 * - blank tool-call ids are normalized;
 * - orphan tool results and duplicates are dropped.
 */
fun repairToolTranscript(history: List<ChatMessage>): List<ChatMessage> {
    val normalized = history.mapIndexed { index, msg ->
        if (msg.role == ChatRole.ASSISTANT && msg.toolCalls.isNotEmpty()) {
            val calls = msg.toolCalls.mapIndexed { callIndex, call ->
                if (call.id.isBlank()) call.copy(id = "recovered_${index}_$callIndex") else call
            }
            if (calls == msg.toolCalls) msg else msg.copy(toolCalls = calls)
        } else {
            msg
        }
    }
    val callIds = normalized.asSequence()
        .filter { it.role == ChatRole.ASSISTANT }
        .flatMap { it.toolCalls.asSequence() }
        .map { it.id }
        .toHashSet()
    val resultsById = LinkedHashMap<String, ChatMessage>()
    normalized.forEach { msg ->
        if (msg.role == ChatRole.TOOL) {
            val id = msg.toolCallId
            if (id != null && id in callIds && id !in resultsById) resultsById[id] = msg
        }
    }
    val consumed = HashSet<String>()
    val out = ArrayList<ChatMessage>(history.size + 4)
    normalized.forEach { msg ->
        when (msg.role) {
            ChatRole.ASSISTANT -> {
                out += msg
                msg.toolCalls.forEach { call ->
                    if (!consumed.add(call.id)) return@forEach
                    out += resultsById[call.id] ?: ChatMessage(
                        role = ChatRole.TOOL,
                        content = "已取消（上一轮中断，自动补齐）",
                        toolCallId = call.id,
                        toolName = call.name
                    )
                }
            }
            ChatRole.TOOL -> Unit
            else -> out += msg
        }
    }
    return out
}
