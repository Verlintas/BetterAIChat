package com.betteraichat.providers.anthropic

import com.betteraichat.core.model.ChatMessage
import com.betteraichat.core.model.ChatRole
import com.betteraichat.core.model.ToolCall
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AnthropicThinkingSignatureTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `signature delta is parsed`() {
        val raw = """{"type":"content_block_delta","delta":{"type":"signature_delta","signature":"abc123"}}"""
        val event = json.decodeFromString(AnthropicEvent.serializer(), raw)
        assertEquals("signature_delta", event.delta?.type)
        assertEquals("abc123", event.delta?.signature)
    }

    @Test
    fun `thinking block is replayed even without thinking text`() {
        val message = ChatMessage(
            role = ChatRole.ASSISTANT,
            content = "",
            thinkingSignature = "sig-abc",
            toolCalls = listOf(
                ToolCall(id = "toolu_1", name = "web_search", arguments = "{}")
            )
        )
        val wire = with(AnthropicProvider()) { message.toWire(reasoning = true) }
        val thinking = wire.content.first { it.type == "thinking" }
        assertEquals("sig-abc", thinking.signature)
        assertEquals("", thinking.thinkingText)
        assertEquals("tool_use", wire.content.last().type)
    }
}
