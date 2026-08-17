package com.betteraichat.ui.settings

import com.betteraichat.core.model.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ModelProbe {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class ProbeResult(
        val ok: Boolean,
        val message: String,
        val models: List<String> = emptyList()
    )

    suspend fun probe(provider: ProviderId, baseUrl: String, apiKey: String): ProbeResult =
        withContext(Dispatchers.IO) {
            when (provider) {
                ProviderId.OPENAI_COMPAT -> probeOpenAiCompat(baseUrl, apiKey)
                ProviderId.ANTHROPIC -> probeAnthropic(baseUrl, apiKey)
                ProviderId.GEMINI -> probeGemini(baseUrl, apiKey)
            }
        }

    private fun probeOpenAiCompat(baseUrl: String, apiKey: String): ProbeResult {
        val clean = baseUrl.trimEnd('/')
        val candidates = listOf("$clean/models", "$clean/v1/models")
        var lastError = ""
        for (url in candidates) {
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .build()
            val response = runCatching { client.newCall(req).execute() }.getOrNull()
            if (response == null) {
                lastError = "网络连接失败"
                continue
            }
            response.use {
                when {
                    it.isSuccessful -> {
                        val models = parseOpenAiModels(it.body?.string().orEmpty())
                        return ProbeResult(
                            ok = true,
                            message = if (models.isEmpty()) "连接成功（未返回模型列表）" else "连接成功，发现 ${models.size} 个模型",
                            models = models
                        )
                    }
                    it.code == 401 -> return ProbeResult(false, "API Key 无效或已过期（HTTP 401）")
                    it.code == 403 -> return ProbeResult(false, "API Key 无权限访问（HTTP 403）")
                    it.code == 404 && url == candidates.last() ->
                        return ProbeResult(false, "Base URL 不正确（HTTP 404），请检查地址")
                    else -> lastError = "HTTP ${it.code}"
                }
            }
        }
        return ProbeResult(false, if (lastError.isBlank()) "连接失败" else "连接失败：$lastError")
    }

    private fun probeAnthropic(baseUrl: String, apiKey: String): ProbeResult {
        val url = "${baseUrl.trimEnd('/')}/v1/models"
        val req = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .build()
        return runCatching { client.newCall(req).execute() }.fold(
            onSuccess = { response ->
                response.use {
                    when {
                        it.isSuccessful -> {
                            val models = parseAnthropicModels(it.body?.string().orEmpty())
                            ProbeResult(
                                ok = true,
                                message = if (models.isEmpty()) "连接成功" else "连接成功，发现 ${models.size} 个模型",
                                models = models
                            )
                        }
                        it.code == 401 -> ProbeResult(false, "API Key 无效或已过期（HTTP 401）")
                        it.code == 404 -> ProbeResult(false, "Base URL 不正确（HTTP 404）")
                        it.code == 429 -> ProbeResult(false, "请求过于频繁或额度不足（HTTP 429）")
                        else -> ProbeResult(false, "连接失败（HTTP ${it.code}）")
                    }
                }
            },
            onFailure = { e -> ProbeResult(false, "网络连接失败：${e.message}") }
        )
    }

    private fun probeGemini(baseUrl: String, apiKey: String): ProbeResult {
        val url = "${baseUrl.trimEnd('/')}/v1beta/models"
        val req = Request.Builder()
            .url(url)
            .header("X-Goog-Api-Key", apiKey)
            .build()
        return runCatching { client.newCall(req).execute() }.fold(
            onSuccess = { response ->
                response.use {
                    when {
                        it.isSuccessful -> {
                            val models = parseGeminiModels(it.body?.string().orEmpty())
                            ProbeResult(
                                ok = true,
                                message = if (models.isEmpty()) "连接成功" else "连接成功，发现 ${models.size} 个模型",
                                models = models
                            )
                        }
                        it.code == 400 -> ProbeResult(false, "API Key 无效（HTTP 400）")
                        it.code == 403 -> ProbeResult(false, "API Key 无权限（HTTP 403）")
                        else -> ProbeResult(false, "连接失败（HTTP ${it.code}）")
                    }
                }
            },
            onFailure = { e -> ProbeResult(false, "网络连接失败：${e.message}") }
        )
    }

    private fun parseOpenAiModels(body: String): List<String> = runCatching {
        json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
            ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
            ?: emptyList()
    }.getOrDefault(emptyList())

    private fun parseAnthropicModels(body: String): List<String> = runCatching {
        json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
            ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
            ?: emptyList()
    }.getOrDefault(emptyList())

    private fun parseGeminiModels(body: String): List<String> = runCatching {
        json.parseToJsonElement(body).jsonObject["models"]?.jsonArray
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content?.removePrefix("models/") }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }.getOrDefault(emptyList())
}
