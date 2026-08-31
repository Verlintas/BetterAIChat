package com.betteraichat.core.chat

import com.betteraichat.core.model.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentKeyInferTest {

    @Test
    fun `anthropic key prefix`() {
        assertEquals(ProviderId.ANTHROPIC, inferProviderFromKey("sk-ant-api03-abcdef123"))
        assertEquals(ProviderId.ANTHROPIC, inferProviderFromKey("  sk-ant-api03-xyz  "))
    }

    @Test
    fun `gemini key prefix`() {
        assertEquals(ProviderId.GEMINI, inferProviderFromKey("AIzaSyD12345"))
    }

    @Test
    fun `openai compatible default`() {
        assertEquals(ProviderId.OPENAI_COMPAT, inferProviderFromKey("sk-proj-abcdef"))
        assertEquals(ProviderId.OPENAI_COMPAT, inferProviderFromKey(""))
        assertEquals(ProviderId.OPENAI_COMPAT, inferProviderFromKey("anything-else"))
    }

    @Test
    fun `openrouter stays openai compatible`() {
        assertEquals(ProviderId.OPENAI_COMPAT, inferProviderFromKey("sk-or-v1-xyz"))
    }
}
