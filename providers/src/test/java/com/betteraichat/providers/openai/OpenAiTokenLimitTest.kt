package com.betteraichat.providers.openai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiTokenLimitTest {

    @Test
    fun `official gpt-5 and o-series use max_completion_tokens`() {
        assertTrue(usesMaxCompletionTokens("https://api.openai.com/v1", "gpt-5.6"))
        assertTrue(usesMaxCompletionTokens("https://api.openai.com/v1", "gpt-6-astra"))
        assertTrue(usesMaxCompletionTokens("https://api.openai.com/v1", "o3"))
        assertTrue(usesMaxCompletionTokens("https://my.openai.azure.com", "o4-mini"))
    }

    @Test
    fun `compatible gateways and older models keep max_tokens`() {
        assertFalse(usesMaxCompletionTokens("https://api.deepseek.com/v1", "gpt-5.6"))
        assertFalse(usesMaxCompletionTokens("https://api.openai.com/v1", "gpt-4o"))
        assertFalse(usesMaxCompletionTokens("https://api.openai.com/v1", "deepseek-v4-flash"))
    }
}
