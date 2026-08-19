package com.betteraichat.providers.gemini

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
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class GeminiRequest(
    @SerialName("systemInstruction") val systemInstruction: GeminiSystemInstruction? = null,
    val contents: List<GeminiContent>,
    @SerialName("generationConfig") val generationConfig: GeminiGenerationConfig? = null,
    val tools: List<GeminiTool>? = null
)

@Serializable
data class GeminiSystemInstruction(val parts: List<GeminiPart>)

@Serializable
data class GeminiContent(val role: String, val parts: List<GeminiPart>)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val thought: Boolean = false,
    @SerialName("inlineData") val inlineData: GeminiInlineData? = null,
    @SerialName("functionCall") val functionCall: GeminiFunctionCall? = null,
    @SerialName("functionResponse") val functionResponse: GeminiFunctionResponse? = null
)

@Serializable
data class GeminiInlineData(
    @SerialName("mimeType") val mimeType: String,
    val data: String
)

@Serializable
data class GeminiFunctionCall(
    val name: String,
    val id: String? = null,
    val args: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class GeminiFunctionResponse(
    val name: String,
    val response: JsonObject
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double? = null,
    @SerialName("maxOutputTokens") val maxOutputTokens: Int? = null
)

@Serializable
data class GeminiTool(
    @SerialName("functionDeclarations") val functionDeclarations: List<GeminiFunctionDeclaration>
)

@Serializable
data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val error: GeminiError? = null,
    @SerialName("usageMetadata") val usageMetadata: GeminiUsageMetadata? = null
)

@Serializable
data class GeminiUsageMetadata(
    @SerialName("promptTokenCount") val promptTokenCount: Long = 0,
    @SerialName("candidatesTokenCount") val candidatesTokenCount: Long = 0
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerialName("finishReason") val finishReason: String? = null
)

@Serializable
data class GeminiError(
    val code: Int = 0,
    val message: String? = null,
    val status: String? = null
)

class GeminiProvider : ChatProvider {

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
        val system = messages.firstOrNull { it.role == ChatRole.SYSTEM }?.content
        val body = GeminiRequest(
            systemInstruction = system?.takeIf { it.isNotBlank() }?.let {
                GeminiSystemInstruction(listOf(GeminiPart(text = it)))
            },
            contents = messages.filter { it.role != ChatRole.SYSTEM }.toGeminiContents(),
            generationConfig = GeminiGenerationConfig(
                temperature = config.temperature,
                maxOutputTokens = config.maxTokens
            ),
            tools = tools.map {
                GeminiTool(listOf(GeminiFunctionDeclaration(it.name, it.description, it.parameters)))
            }.takeIf { it.isNotEmpty() }
        )
        val url = "${config.baseUrl.trimEnd('/')}/v1beta/models/${config.model}:streamGenerateContent"
            .toHttpUrl().newBuilder().addQueryParameter("alt", "sse").build()
        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(GeminiRequest.serializer(), body)
                .toRequestBody("application/json".toMediaType()))
            .header("X-Goog-Api-Key", config.apiKey)
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
            respBody.use { body ->
                val toolCalls = mutableListOf<ToolCall>()
                var lastUsage: GeminiUsageMetadata? = null
                var sawCandidate = false
                var finished = false
                SseParser.parse(body) { _, data ->
                    val resp = runCatching { json.decodeFromString(GeminiResponse.serializer(), data) }
                        .getOrNull() ?: return@parse true
                    resp.error?.message?.let {
                        emit(StreamEvent.Error(it))
                        return@parse false
                    }
                    resp.usageMetadata?.let { lastUsage = it }
                    val parts = resp.candidates.firstOrNull()?.content?.parts ?: return@parse true
                    sawCandidate = true
                    val reason = resp.candidates.firstOrNull()?.finishReason
                    if (reason == "STOP" || reason == "MAX_TOKENS" || reason == "SAFETY" || reason == "RECITATION") {
                        finished = true
                    }
                    for (part in parts) {
                        if (part.thought && !part.text.isNullOrBlank()) {
                            emit(StreamEvent.ThinkingDelta(part.text))
                            continue
                        }
                        part.text?.let { emit(StreamEvent.Delta(it)) }
                        part.functionCall?.let { fc ->
                            toolCalls.add(
                                ToolCall(
                                    id = fc.id ?: "call_${toolCalls.size}",
                                    name = fc.name,
                                    arguments = fc.args.toString()
                                )
                            )
                        }
                    }
                    true
                }.collect { }
                lastUsage?.let { emit(StreamEvent.Usage(it.promptTokenCount, it.candidatesTokenCount)) }
                if (sawCandidate && !finished) {
                    emit(StreamEvent.Error("流意外中断（未收到完成标记）"))
                } else {
                    emit(StreamEvent.ToolCallsDone(toolCalls))
                    emit(StreamEvent.Done)
                }
            }
        } finally {
            call.cancel()
        }
    }

    private fun List<ChatMessage>.toGeminiContents(): List<GeminiContent> {
        val result = mutableListOf<GeminiContent>()
        for (m in this) {
            val parts = buildList {
                when (m.role) {
                    ChatRole.USER -> {
                        if (m.content.isNotBlank()) add(GeminiPart(text = m.content))
                        m.attachments.forEach { att ->
                            when {
                                att.isImage -> add(GeminiPart(inlineData = GeminiInlineData(att.mimeType, att.dataBase64)))
                                att.textContent != null -> add(GeminiPart(text = "（文件：${att.name}）\n${att.textContent}"))
                            }
                        }
                    }
                    ChatRole.ASSISTANT -> {
                        if (m.content.isNotBlank()) add(GeminiPart(text = m.content))
                        m.toolCalls.forEach { tc ->
                            val args = runCatching { json.parseToJsonElement(tc.arguments).jsonObject }
                                .getOrDefault(JsonObject(emptyMap()))
                            add(GeminiPart(functionCall = GeminiFunctionCall(name = tc.name, args = args)))
                        }
                    }
                    ChatRole.TOOL -> add(
                        GeminiPart(
                            functionResponse = GeminiFunctionResponse(
                                name = m.toolName ?: "unknown",
                                response = JsonObject(mapOf("result" to JsonPrimitive(m.content)))
                            )
                        )
                    )
                    ChatRole.SYSTEM -> Unit
                }
            }
            if (parts.isEmpty()) continue
            val role = if (m.role == ChatRole.ASSISTANT) "model" else "user"
            val last = result.lastOrNull()
            if (last != null && last.role == role) {
                result[result.size - 1] = last.copy(parts = last.parts + parts)
            } else {
                result.add(GeminiContent(role = role, parts = parts))
            }
        }
        return result
    }
}
