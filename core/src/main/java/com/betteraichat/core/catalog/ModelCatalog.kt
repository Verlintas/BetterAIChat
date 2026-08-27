package com.betteraichat.core.catalog

import com.betteraichat.core.model.ProviderId

data class ModelEntry(
    val id: String,
    val label: String,
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    val supportsReasoning: Boolean = false,
    val contextWindow: Int = 200_000
)

object ModelCatalog {

    val openAiCompat = listOf(
        ModelEntry("gpt-5.6", "GPT-5.6", maxTokens = 8192, supportsReasoning = true, contextWindow = 400_000),
        ModelEntry("gpt-5.5", "GPT-5.5", maxTokens = 8192, supportsReasoning = true, contextWindow = 400_000),
        ModelEntry("gpt-5.5-pro", "GPT-5.5 Pro", maxTokens = 8192, supportsReasoning = true, contextWindow = 400_000),
        ModelEntry("gpt-5.4", "GPT-5.4", maxTokens = 8192, supportsReasoning = true, contextWindow = 400_000),
        ModelEntry("gpt-5.4-mini", "GPT-5.4 mini", maxTokens = 8192, supportsReasoning = true, contextWindow = 400_000),
        ModelEntry("gpt-5.2", "GPT-5.2", maxTokens = 8192, supportsReasoning = true, contextWindow = 400_000),
        ModelEntry("gpt-5.2-pro", "GPT-5.2 Pro", maxTokens = 8192, supportsReasoning = true, contextWindow = 400_000),
        ModelEntry("gpt-5", "GPT-5", maxTokens = 8192, supportsReasoning = true, contextWindow = 400_000),
        ModelEntry("gpt-5-mini", "GPT-5 mini", maxTokens = 8192, supportsReasoning = true, contextWindow = 400_000),
        ModelEntry("gpt-5-nano", "GPT-5 nano", maxTokens = 8192, supportsReasoning = true, contextWindow = 400_000),
        ModelEntry("gpt-4o", "GPT-4o", maxTokens = 8192, contextWindow = 128_000),
        ModelEntry("gpt-4o-mini", "GPT-4o mini", maxTokens = 8192, contextWindow = 128_000),
        ModelEntry("gpt-4.1", "GPT-4.1", maxTokens = 8192, contextWindow = 1_047_576),
        ModelEntry("gpt-4.1-mini", "GPT-4.1 mini", maxTokens = 8192, contextWindow = 1_047_576),
        ModelEntry("gpt-4.1-nano", "GPT-4.1 nano", maxTokens = 8192, contextWindow = 1_047_576),
        ModelEntry("deepseek-v4-pro", "DeepSeek V4 Pro", maxTokens = 8192, supportsReasoning = true, contextWindow = 128_000),
        ModelEntry("deepseek-v4-flash", "DeepSeek V4 Flash", maxTokens = 8192, supportsReasoning = true, contextWindow = 128_000),
        ModelEntry("deepseek-v4-flash-vision-exp", "DeepSeek V4 Flash Vision", maxTokens = 8192, supportsReasoning = true, contextWindow = 128_000),
        ModelEntry("kimi-k3", "Kimi K3", maxTokens = 8192, supportsReasoning = true, contextWindow = 128_000),
        ModelEntry("kimi-k2.7-code", "Kimi K2.7 Code", maxTokens = 8192, supportsReasoning = true, contextWindow = 128_000),
        ModelEntry("kimi-k2-thinking", "Kimi K2 Thinking", maxTokens = 8192, supportsReasoning = true, contextWindow = 128_000),
        ModelEntry("qwen3.8-max", "通义千问 3.8 Max", maxTokens = 8192, supportsReasoning = true, contextWindow = 128_000),
        ModelEntry("qwen3.8-flash", "通义千问 3.8 Flash", maxTokens = 8192, supportsReasoning = true, contextWindow = 128_000),
        ModelEntry("qwen3.7-plus", "通义千问 3.7 Plus", maxTokens = 8192, supportsReasoning = true, contextWindow = 128_000),
        ModelEntry("qwen-max", "通义千问 Max", maxTokens = 8192, contextWindow = 32_000),
        ModelEntry("qwen-plus", "通义千问 Plus", maxTokens = 8192, supportsReasoning = true, contextWindow = 131_072),
        ModelEntry("qwen-turbo", "通义千问 Turbo", maxTokens = 8192, contextWindow = 1_000_000),
        ModelEntry("glm-5.3", "智谱 GLM-5.3", maxTokens = 8192, supportsReasoning = true, contextWindow = 128_000),
        ModelEntry("glm-5.3-flash", "智谱 GLM-5.3 Flash", maxTokens = 8192, supportsReasoning = true, contextWindow = 128_000),
        ModelEntry("glm-5.2", "智谱 GLM-5.2", maxTokens = 8192, supportsReasoning = true, contextWindow = 128_000)
    )

    val anthropic = listOf(
        ModelEntry("claude-opus-5", "Claude Opus 5", maxTokens = 8192, supportsReasoning = true, contextWindow = 200_000),
        ModelEntry("claude-opus-4-8", "Claude Opus 4.8", maxTokens = 8192, supportsReasoning = true, contextWindow = 200_000),
        ModelEntry("claude-opus-4-6", "Claude Opus 4.6", maxTokens = 8192, supportsReasoning = true, contextWindow = 200_000),
        ModelEntry("claude-sonnet-5", "Claude Sonnet 5", maxTokens = 8192, supportsReasoning = true, contextWindow = 200_000),
        ModelEntry("claude-sonnet-4-6", "Claude Sonnet 4.6", maxTokens = 8192, supportsReasoning = true, contextWindow = 200_000),
        ModelEntry("claude-sonnet-4-5", "Claude Sonnet 4.5", maxTokens = 8192, supportsReasoning = true, contextWindow = 200_000),
        ModelEntry("claude-haiku-4-5", "Claude Haiku 4.5", maxTokens = 8192, contextWindow = 200_000),
        ModelEntry("claude-fable-5", "Claude Fable 5", maxTokens = 8192, supportsReasoning = true, contextWindow = 200_000)
    )

    val gemini = listOf(
        ModelEntry("gemini-3.7-flash", "Gemini 3.7 Flash", maxTokens = 8192, supportsReasoning = true, contextWindow = 1_048_576),
        ModelEntry("gemini-3.6-flash", "Gemini 3.6 Flash", maxTokens = 8192, supportsReasoning = true, contextWindow = 1_048_576),
        ModelEntry("gemini-3.5-flash", "Gemini 3.5 Flash", maxTokens = 8192, supportsReasoning = true, contextWindow = 1_048_576),
        ModelEntry("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", maxTokens = 8192, supportsReasoning = true, contextWindow = 1_048_576),
        ModelEntry("gemini-3.1-pro-preview", "Gemini 3.1 Pro", maxTokens = 8192, supportsReasoning = true, contextWindow = 1_048_576),
        ModelEntry("gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite", maxTokens = 8192, supportsReasoning = true, contextWindow = 1_048_576),
        ModelEntry("gemini-3-flash-preview", "Gemini 3 Flash", maxTokens = 8192, supportsReasoning = true, contextWindow = 1_048_576),
        ModelEntry("gemini-2.5-pro", "Gemini 2.5 Pro", maxTokens = 8192, supportsReasoning = true, contextWindow = 1_048_576),
        ModelEntry("gemini-2.5-flash", "Gemini 2.5 Flash", maxTokens = 8192, supportsReasoning = true, contextWindow = 1_048_576),
        ModelEntry("gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite", maxTokens = 8192, contextWindow = 1_048_576)
    )

    fun modelsFor(provider: ProviderId): List<ModelEntry> = when (provider) {
        ProviderId.OPENAI_COMPAT -> openAiCompat
        ProviderId.ANTHROPIC -> anthropic
        ProviderId.GEMINI -> gemini
    }

    fun entryFor(provider: ProviderId, modelId: String): ModelEntry =
        modelsFor(provider).firstOrNull { it.id == modelId } ?: ModelEntry(modelId, modelId)

    fun defaultModel(provider: ProviderId): String = modelsFor(provider).first().id

    fun defaultBaseUrl(provider: ProviderId): String = when (provider) {
        ProviderId.OPENAI_COMPAT -> "https://api.openai.com/v1"
        ProviderId.ANTHROPIC -> "https://api.anthropic.com"
        ProviderId.GEMINI -> "https://generativelanguage.googleapis.com"
    }
}
