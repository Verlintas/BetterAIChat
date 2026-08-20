package com.betteraichat.providers.openai

import com.betteraichat.core.catalog.ModelCatalog
import com.betteraichat.core.model.ChatMessage
import com.betteraichat.core.model.ChatRole
import com.betteraichat.core.model.ProviderConfig
import com.betteraichat.core.model.StreamEvent
import com.betteraichat.core.model.ToolCall
import com.betteraichat.core.model.ToolSpec
import com.betteraichat.core.provider.ChatProvider
import com.betteraichat.core.sse.SseParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class OpenAiChunk(
    val choices: List<OpenAiChoice> = emptyList(),
    val usage: OpenAiUsage? = null
)

@Serializable
data class OpenAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Long = 0,
    @SerialName("completion_tokens") val completionTokens: Long = 0
)

@Serializable
data class OpenAiStreamOptions(
    @SerialName("include_usage") val includeUsage: Boolean = true
)

@Serializable
data class OpenAiChoice(
    val delta: OpenAiDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class OpenAiDelta(
    val content: JsonElement? = null,
    val reasoning: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAiToolCallDelta>? = null
)

@Serializable
data class OpenAiToolCallDelta(
    val index: Int? = null,
    val id: String? = null,
    val function: OpenAiFunctionDelta? = null
)

@Serializable
data class OpenAiFunctionDelta(
    val name: String? = null,
    val arguments: String? = null
)

@Serializable
data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = true,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    @SerialName("stream_options") val streamOptions: OpenAiStreamOptions? = null,
    val tools: List<OpenAiTool>? = null
)

@Serializable
data class OpenAiMessage(
    val role: String,
    val content: JsonElement? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAiToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null
)

@Serializable
data class OpenAiToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenAiFunction
)

@Serializable
data class OpenAiFunction(
    val name: String,
    val arguments: String
)

@Serializable
data class OpenAiTool(
    val type: String = "function",
    val function: OpenAiToolFunction
)

@Serializable
data class OpenAiToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

class OpenAiProvider : ChatProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
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
        val body = OpenAiRequest(
            model = config.model,
            messages = messages.mapNotNull { it.toWire() },
            temperature = if (reasoning) null else config.temperature,
            maxTokens = config.maxTokens,
            reasoningEffort = if (reasoning) "high" else null,
            streamOptions = OpenAiStreamOptions(),
            tools = tools.map {
                OpenAiTool(function = OpenAiToolFunction(it.name, it.description, it.parameters))
            }.takeIf { it.isNotEmpty() }
        )
        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .post(json.encodeToString(OpenAiRequest.serializer(), body)
                .toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer ${config.apiKey}")
            .build()
        val call = client.newCall(request)
        try {
            var response = executeWithRetry(call)
            if (!response.isSuccessful) {
                val code = response.code
                val body = response.body?.string().orEmpty()
                response.close()
                emit(StreamEvent.Error("HTTP $code: $body"))
                return@flow
            }
            val respBody = response.body ?: run {
                emit(StreamEvent.Error("空响应"))
                return@flow
            }
            respBody.use { body ->
                val toolAcc = mutableMapOf<Int, ToolAcc>()
                var callsEmitted = false
                var completed = false
                SseParser.parse(body) { _, data ->
                    if (data == "[DONE]") {
                        if (!callsEmitted) {
                            callsEmitted = true
                            emit(StreamEvent.ToolCallsDone(toolAcc.toToolCalls()))
                        }
                        emit(StreamEvent.Done)
                        completed = true
                        return@parse false
                    }
                    val chunk = runCatching { json.decodeFromString(OpenAiChunk.serializer(), data) }
                        .getOrNull() ?: return@parse true
                    chunk.usage?.let { usage ->
                        if (usage.promptTokens > 0 || usage.completionTokens > 0) {
                            emit(StreamEvent.Usage(usage.promptTokens, usage.completionTokens))
                        }
                    }
                    val choice = chunk.choices.firstOrNull() ?: return@parse true
                    choice.delta?.content?.let { c ->
                        when {
                            c is JsonPrimitive -> c.content.takeIf { it.isNotBlank() }
                                ?.let { emit(StreamEvent.Delta(it)) }
                            c is JsonArray -> c.forEach { part ->
                                val text = (part as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                                if (!text.isNullOrBlank()) emit(StreamEvent.Delta(text))
                            }
                        }
                    }
                    choice.delta?.reasoning?.let { if (it.isNotBlank()) emit(StreamEvent.ThinkingDelta(it)) }
                    choice.delta?.reasoningContent?.let { if (it.isNotBlank()) emit(StreamEvent.ThinkingDelta(it)) }
                    choice.delta?.toolCalls?.forEach { tc ->
                        val idx = tc.index ?: 0
                        val acc = toolAcc.getOrPut(idx) { ToolAcc() }
                        tc.id?.let { acc.id = it }
                        tc.function?.name?.let { acc.name = it }
                        tc.function?.arguments?.let { arg ->
                            when {
                                arg == acc.args -> Unit
                                arg.length > acc.args.length && arg.startsWith(acc.args) -> acc.args = arg
                                acc.args.length > arg.length && acc.args.startsWith(arg) -> Unit
                                else -> acc.args += arg
                            }
                        }
                    }
                    if (choice.finishReason == "tool_calls" && !callsEmitted) {
                        callsEmitted = true
                        emit(StreamEvent.ToolCallsDone(toolAcc.toToolCalls()))
                    }
                    if (choice.finishReason == "stop") completed = true
                    true
                }.collect { }
                if (!completed) {
                    emit(StreamEvent.Error("流意外中断（未收到完成标记）"))
                } else {
                    if (!callsEmitted) {
                        emit(StreamEvent.ToolCallsDone(toolAcc.toToolCalls()))
                    }
                    emit(StreamEvent.Done)
                }
            }
        } finally {
            call.cancel()
        }
    }

    private class ToolAcc {
        var id: String? = null
        var name: String? = null
        var args: String = ""
    }

    private suspend fun executeWithRetry(call: okhttp3.Call): okhttp3.Response {
        val retryableCodes = setOf(429, 502, 503, 504)
        repeat(2) { attempt ->
            try {
                val response = call.execute()
                if (response.isSuccessful || response.code !in retryableCodes) return response
                response.close()
                if (attempt == 0) delay(500)
            } catch (e: java.io.IOException) {
                if (attempt == 0) delay(500) else throw e
            }
        }
        return call.execute()
    }

    private fun Map<Int, ToolAcc>.toToolCalls(): List<ToolCall> =
        toSortedMap().map { (idx, acc) ->
            ToolCall(
                id = acc.id ?: "call_$idx",
                name = acc.name ?: "unknown",
                arguments = acc.args.ifBlank { "{}" }
            )
        }

    private fun ChatMessage.toWire(): OpenAiMessage? = when (role) {
        ChatRole.SYSTEM -> OpenAiMessage(role = "system", content = JsonPrimitive(content))
        ChatRole.USER -> OpenAiMessage(
            role = "user",
            content = buildUserContent()
        )
        ChatRole.ASSISTANT -> {
            if (content.isBlank() && toolCalls.isEmpty()) return null
            OpenAiMessage(
                role = "assistant",
                content = content.ifEmpty { null }?.let { JsonPrimitive(it) },
                toolCalls = toolCalls.map {
                    OpenAiToolCall(
                        id = it.id,
                        function = OpenAiFunction(name = it.name, arguments = it.arguments)
                    )
                }.takeIf { it.isNotEmpty() }
            )
        }
        ChatRole.TOOL -> OpenAiMessage(
            role = "tool",
            content = JsonPrimitive(content),
            toolCallId = toolCallId
        )
    }

    private fun ChatMessage.buildUserContent(): JsonElement {
        val blocks = mutableListOf<JsonElement>()
        if (content.isNotBlank()) {
            blocks.add(buildJsonObject { put("type", "text"); put("text", content) })
        }
        attachments.forEach { att ->
            if (att.isImage) {
                blocks.add(
                    buildJsonObject {
                        put("type", "image_url")
                        put(
                            "image_url",
                            buildJsonObject {
                                put("url", "data:${att.mimeType};base64,${att.dataBase64}")
                            }
                        )
                    }
                )
            } else if (att.textContent != null) {
                blocks.add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", "（文件：${att.name}）\n${att.textContent}")
                    }
                )
            }
        }
        return if (blocks.isEmpty()) JsonPrimitive(content) else JsonArray(blocks)
    }
}
