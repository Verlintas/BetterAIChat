package com.betteraichat.providers.anthropic

import com.betteraichat.core.catalog.ModelCatalog
import com.betteraichat.core.model.ChatMessage
import com.betteraichat.core.model.ChatRole
import com.betteraichat.core.model.ProviderConfig
import com.betteraichat.core.model.StreamEvent
import com.betteraichat.core.model.ToolCall
import com.betteraichat.core.model.ToolSpec
import com.betteraichat.core.provider.ChatProvider
import com.betteraichat.core.sse.SseParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String? = null,
    val messages: List<AnthropicMessage>,
    val stream: Boolean = true,
    val temperature: Double? = null,
    val thinking: AnthropicThinking? = null,
    val tools: List<AnthropicTool>? = null
)

@Serializable
data class AnthropicThinking(
    val type: String = "enabled",
    @SerialName("budget_tokens") val budgetTokens: Int
)

@Serializable
data class AnthropicMessage(
    val role: String,
    val content: List<AnthropicBlock>
)

@Serializable
data class AnthropicBlock(
    val type: String,
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: JsonObject? = null,
    @SerialName("tool_use_id") val toolUseId: String? = null,
    val content: String? = null,
    val source: AnthropicImageSource? = null
)

@Serializable
data class AnthropicImageSource(
    val type: String,
    @SerialName("media_type") val mediaType: String,
    val data: String
)

@Serializable
data class AnthropicTool(
    val name: String,
    val description: String,
    @SerialName("input_schema") val inputSchema: JsonObject
)

@Serializable
data class AnthropicEvent(
    val type: String? = null,
    @SerialName("content_block") val contentBlock: AnthropicBlock? = null,
    val delta: AnthropicDelta? = null,
    val error: AnthropicError? = null,
    val message: AnthropicMessageStart? = null,
    val usage: AnthropicUsage? = null
)

@Serializable
data class AnthropicMessageStart(
    val usage: AnthropicUsage? = null
)

@Serializable
data class AnthropicUsage(
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0
)

@Serializable
data class AnthropicDelta(
    val type: String? = null,
    val text: String? = null,
    @SerialName("partial_json") val partialJson: String? = null
)

@Serializable
data class AnthropicError(
    val type: String? = null,
    val message: String? = null
)

class AnthropicProvider : ChatProvider {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override fun chatStream(
        messages: List<ChatMessage>,
        config: ProviderConfig,
        tools: List<ToolSpec>
    ): Flow<StreamEvent> = flow {
        val reasoning = config.reasoning &&
            ModelCatalog.entryFor(config.provider, config.model).supportsReasoning
        val system = messages.firstOrNull { it.role == ChatRole.SYSTEM }?.content
        val body = AnthropicRequest(
            model = config.model,
            maxTokens = config.maxTokens,
            system = system,
            messages = messages.filter { it.role != ChatRole.SYSTEM }.map { it.toWire() },
            temperature = config.temperature,
            thinking = if (reasoning) {
                AnthropicThinking(budgetTokens = minOf(4096, config.maxTokens))
            } else null,
            tools = tools.map {
                AnthropicTool(it.name, it.description, it.parameters)
            }.takeIf { it.isNotEmpty() }
        )
        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/v1/messages")
            .post(json.encodeToString(AnthropicRequest.serializer(), body)
                .toRequestBody("application/json".toMediaType()))
            .header("x-api-key", config.apiKey)
            .header("anthropic-version", "2023-06-01")
            .build()
        val call = client.newCall(request)
        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                emit(StreamEvent.Error("HTTP ${response.code}: ${response.body?.string().orEmpty()}"))
                return@flow
            }
            val respBody = response.body ?: run {
                emit(StreamEvent.Error("空响应"))
                return@flow
            }
            val toolCalls = mutableListOf<ToolCall>()
            var currentBlockId: String? = null
            var currentBlockName: String? = null
            var currentBlockArgs = StringBuilder()
            var callsEmitted = false
            var doneEmitted = false
            var inputTokens = 0L
            var outputTokens = 0L
            SseParser.parse(respBody) { _, data ->
                val ev = runCatching { json.decodeFromString(AnthropicEvent.serializer(), data) }
                    .getOrNull() ?: return@parse
                when (ev.type) {
                    "message_start" -> ev.message?.usage?.let { inputTokens = it.inputTokens }
                    "message_delta" -> ev.usage?.let { outputTokens = it.outputTokens }
                    "content_block_start" -> {
                        val block = ev.contentBlock
                        if (block?.type == "tool_use") {
                            currentBlockId = block.id
                            currentBlockName = block.name
                            currentBlockArgs = StringBuilder()
                        }
                    }
                    "content_block_delta" -> {
                        val delta = ev.delta ?: return@parse
                        when (delta.type) {
                            "text_delta" -> delta.text?.let { emit(StreamEvent.Delta(it)) }
                            "thinking_delta" -> delta.text?.let { emit(StreamEvent.ThinkingDelta(it)) }
                            "input_json_delta" -> delta.partialJson?.let { currentBlockArgs.append(it) }
                        }
                    }
                    "content_block_stop" -> {
                        if (currentBlockId != null && currentBlockName != null) {
                            toolCalls.add(
                                ToolCall(
                                    id = currentBlockId!!,
                                    name = currentBlockName!!,
                                    arguments = currentBlockArgs.toString().ifBlank { "{}" }
                                )
                            )
                            currentBlockId = null
                            currentBlockName = null
                            currentBlockArgs = StringBuilder()
                        }
                    }
                    "message_stop" -> {
                        if (!callsEmitted) {
                            callsEmitted = true
                            emit(StreamEvent.ToolCallsDone(toolCalls))
                        }
                        if (!doneEmitted) {
                            doneEmitted = true
                            emit(StreamEvent.Done)
                        }
                    }
                    "error" -> ev.error?.message?.let { emit(StreamEvent.Error(it)) }
                }
            }
            emit(StreamEvent.Usage(inputTokens, outputTokens))
            if (!callsEmitted) {
                emit(StreamEvent.ToolCallsDone(toolCalls))
                emit(StreamEvent.Done)
            }
        } finally {
            call.cancel()
        }
    }

    private fun ChatMessage.toWire(): AnthropicMessage = when (role) {
        ChatRole.USER -> {
            val blocks = mutableListOf<AnthropicBlock>()
            if (content.isNotBlank()) blocks.add(AnthropicBlock(type = "text", text = content))
            attachments.forEach { att ->
                when {
                    att.isImage -> blocks.add(
                        AnthropicBlock(
                            type = "image",
                            source = AnthropicImageSource(
                                type = "base64",
                                mediaType = att.mimeType,
                                data = att.dataBase64
                            )
                        )
                    )
                    att.textContent != null -> blocks.add(
                        AnthropicBlock(type = "text", text = "（文件：${att.name}）\n${att.textContent}")
                    )
                }
            }
            AnthropicMessage(role = "user", content = blocks)
        }
        ChatRole.ASSISTANT -> {
            val blocks = mutableListOf<AnthropicBlock>()
            if (content.isNotBlank()) blocks.add(AnthropicBlock(type = "text", text = content))
            toolCalls.forEach {
                val input = runCatching { json.parseToJsonElement(it.arguments).jsonObject }
                    .getOrDefault(JsonObject(emptyMap()))
                blocks.add(
                    AnthropicBlock(
                        type = "tool_use",
                        id = it.id,
                        name = it.name,
                        input = input
                    )
                )
            }
            AnthropicMessage(role = "assistant", content = blocks)
        }
        ChatRole.TOOL -> AnthropicMessage(
            role = "user",
            content = listOf(
                AnthropicBlock(
                    type = "tool_result",
                    toolUseId = toolCallId,
                    content = content
                )
            )
        )
        ChatRole.SYSTEM -> AnthropicMessage(role = "user", content = listOf(AnthropicBlock(type = "text", text = content)))
    }
}
