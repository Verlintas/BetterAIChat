package com.betteraichat.core.catalog

import com.betteraichat.core.model.ProviderId

data class ModelEntry(
    val id: String,
    val label: String,
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    val supportsReasoning: Boolean = false
)

object ModelCatalog {

    val openAiCompat = listOf(
        ModelEntry("gpt-5", "GPT-5", maxTokens = 8192, supportsReasoning = true),
        ModelEntry("gpt-4o", "GPT-4o", maxTokens = 8192),
        ModelEntry("gpt-4o-mini", "GPT-4o mini", maxTokens = 8192),
        ModelEntry("gpt-4.1", "GPT-4.1", maxTokens = 8192),
        ModelEntry("gpt-4.1-mini", "GPT-4.1 mini", maxTokens = 8192),
        ModelEntry("o3-mini", "o3-mini", temperature = 1.0, maxTokens = 8192, supportsReasoning = true),
        ModelEntry("o1", "o1", temperature = 1.0, maxTokens = 8192, supportsReasoning = true),
        ModelEntry("deepseek-chat", "DeepSeek Chat", maxTokens = 8192),
        ModelEntry("deepseek-reasoner", "DeepSeek Reasoner", maxTokens = 8192, supportsReasoning = true),
        ModelEntry("moonshot-v1-128k", "Moonshot v1 128k", maxTokens = 8192),
        ModelEntry("kimi-k2-0711-preview", "Kimi K2", maxTokens = 8192),
        ModelEntry("qwen-max", "通义千问 Max", maxTokens = 8192),
        ModelEntry("qwen-plus", "通义千问 Plus", maxTokens = 8192),
        ModelEntry("qwen-turbo", "通义千问 Turbo", maxTokens = 8192)
    )

    val anthropic = listOf(
        ModelEntry("claude-opus-4-20250514", "Claude Opus 4", maxTokens = 8192, supportsReasoning = true),
        ModelEntry("claude-sonnet-4-20250514", "Claude Sonnet 4", maxTokens = 8192, supportsReasoning = true),
        ModelEntry("claude-haiku-4-20250514", "Claude Haiku 4", maxTokens = 8192),
        ModelEntry("claude-3-7-sonnet-20250219", "Claude 3.7 Sonnet", maxTokens = 8192, supportsReasoning = true),
        ModelEntry("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", maxTokens = 8192)
    )

    val gemini = listOf(
        ModelEntry("gemini-2.5-pro", "Gemini 2.5 Pro", maxTokens = 8192, supportsReasoning = true),
        ModelEntry("gemini-2.5-flash", "Gemini 2.5 Flash", maxTokens = 8192, supportsReasoning = true),
        ModelEntry("gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite", maxTokens = 8192),
        ModelEntry("gemini-2.0-flash", "Gemini 2.0 Flash", maxTokens = 8192)
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
