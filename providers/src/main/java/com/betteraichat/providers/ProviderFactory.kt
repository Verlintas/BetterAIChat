package com.betteraichat.providers

import com.betteraichat.core.model.ProviderId
import com.betteraichat.core.provider.ChatProvider
import com.betteraichat.providers.anthropic.AnthropicProvider
import com.betteraichat.providers.gemini.GeminiProvider
import com.betteraichat.providers.openai.OpenAiProvider

object ProviderFactory {
    fun create(id: ProviderId): ChatProvider = when (id) {
        ProviderId.OPENAI_COMPAT -> OpenAiProvider()
        ProviderId.ANTHROPIC -> AnthropicProvider()
        ProviderId.GEMINI -> GeminiProvider()
    }
}
