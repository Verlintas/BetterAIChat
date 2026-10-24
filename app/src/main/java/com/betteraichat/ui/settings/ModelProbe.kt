package com.betteraichat.ui.settings

import android.content.Context
import com.betteraichat.R
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

    suspend fun probe(context: Context, provider: ProviderId, baseUrl: String, apiKey: String): ProbeResult =
        withContext(Dispatchers.IO) {
            when (provider) {
                ProviderId.OPENAI_COMPAT -> probeOpenAiCompat(context, baseUrl, apiKey)
                ProviderId.ANTHROPIC -> probeAnthropic(context, baseUrl, apiKey)
                ProviderId.GEMINI -> probeGemini(context, baseUrl, apiKey)
            }
        }

    private fun connected(context: Context, models: List<String>, emptyMessage: Int) = ProbeResult(
        ok = true,
        message = if (models.isEmpty()) context.getString(emptyMessage) else context.getString(R.string.probe_connected_models, models.size),
        models = models
    )

    private fun probeOpenAiCompat(context: Context, baseUrl: String, apiKey: String): ProbeResult {
        val clean = baseUrl.trimEnd('/')
        val candidates = listOf("$clean/models", "$clean/v1/models")
        var lastCode: Int? = null
        for (url in candidates) {
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .build()
            val response = runCatching { client.newCall(req).execute() }.getOrNull()
            if (response == null) continue
            response.use {
                when {
                    it.isSuccessful -> {
                        val models = parseOpenAiModels(it.body?.string().orEmpty())
                        return connected(context, models, R.string.probe_connected_empty)
                    }
                    it.code == 401 -> return ProbeResult(false, context.getString(R.string.probe_auth_invalid))
                    it.code == 403 -> return ProbeResult(false, context.getString(R.string.probe_auth_forbidden))
                    it.code == 404 && url == candidates.last() ->
                        return ProbeResult(false, context.getString(R.string.probe_base_url_404))
                    else -> lastCode = it.code
                }
            }
        }
        return if (lastCode != null) {
            ProbeResult(false, context.getString(R.string.probe_http_error, lastCode))
        } else {
            ProbeResult(false, context.getString(R.string.probe_network_failed))
        }
    }

    private fun probeAnthropic(context: Context, baseUrl: String, apiKey: String): ProbeResult {
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
                            connected(context, models, R.string.probe_connected)
                        }
                        it.code == 401 -> ProbeResult(false, context.getString(R.string.probe_auth_invalid))
                        it.code == 404 -> ProbeResult(false, context.getString(R.string.probe_base_url_404_short))
                        it.code == 429 -> ProbeResult(false, context.getString(R.string.probe_rate_limited))
                        else -> ProbeResult(false, context.getString(R.string.probe_http_error_brief, it.code))
                    }
                }
            },
            onFailure = { e ->
                ProbeResult(false, context.getString(R.string.probe_network_failed_detail, e.message.orEmpty()))
            }
        )
    }

    private fun probeGemini(context: Context, baseUrl: String, apiKey: String): ProbeResult {
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
                            connected(context, models, R.string.probe_connected)
                        }
                        it.code == 400 -> ProbeResult(false, context.getString(R.string.probe_gemini_key_invalid))
                        it.code == 403 -> ProbeResult(false, context.getString(R.string.probe_gemini_forbidden))
                        else -> ProbeResult(false, context.getString(R.string.probe_http_error_brief, it.code))
                    }
                }
            },
            onFailure = { e ->
                ProbeResult(false, context.getString(R.string.probe_network_failed_detail, e.message.orEmpty()))
            }
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
