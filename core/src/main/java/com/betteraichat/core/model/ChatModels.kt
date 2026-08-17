package com.betteraichat.core.model

import com.betteraichat.core.mode.AppMode
import kotlinx.serialization.Serializable

enum class ProviderId(val displayName: String) {
    OPENAI_COMPAT("OpenAI 兼容"),
    ANTHROPIC("Anthropic Claude"),
    GEMINI("Google Gemini")
}

enum class ChatRole(val wire: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool")
}

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    val result: String? = null,
    val status: ToolCallStatus = ToolCallStatus.PENDING
)

enum class ToolCallStatus { PENDING, RUNNING, DONE, FAILED, REJECTED, DENIED }

@Serializable
data class Attachment(
    val kind: String,
    val name: String,
    val mimeType: String,
    val dataBase64: String = "",
    val textContent: String? = null
) {
    val isImage: Boolean get() = kind == "image"
}

data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val model: String? = null,
    val mode: AppMode? = null,
    val attachments: List<Attachment> = emptyList()
)

data class ProviderConfig(
    val provider: ProviderId,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Double,
    val maxTokens: Int,
    val reasoning: Boolean
)

data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: kotlinx.serialization.json.JsonObject,
    val readOnly: Boolean = false
)

sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class ToolCallsDone(val calls: List<ToolCall>) : StreamEvent
    data class Usage(val promptTokens: Long, val completionTokens: Long) : StreamEvent
    data object Done : StreamEvent
    data class Error(val message: String) : StreamEvent
}
