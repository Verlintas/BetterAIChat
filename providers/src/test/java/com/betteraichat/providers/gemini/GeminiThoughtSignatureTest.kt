package com.betteraichat.providers.gemini

import com.betteraichat.core.model.ChatMessage
import com.betteraichat.core.model.ChatRole
import com.betteraichat.core.model.ToolCall
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiThoughtSignatureTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun `assistant tool call keeps its thought signature in history`() {
        val message = ChatMessage(
            role = ChatRole.ASSISTANT,
            content = "",
            toolCalls = listOf(
                ToolCall(
                    id = "c1",
                    name = "web_search",
                    arguments = """{"query":"x"}""",
                    thoughtSignature = "sig-123"
                )
            )
        )
        val contents = with(GeminiProvider()) {
            listOf(message).toGeminiContents(json)
        }
        val part = contents.single().parts.single()
        assertEquals("sig-123", part.thoughtSignature)
        val encoded = json.encodeToString(GeminiContent.serializer(), contents.single())
        assertTrue(encoded.contains("\"thoughtSignature\":\"sig-123\""))
    }

    @Test
    fun `unsigned gemini3 history uses the skip sentinel on the first call`() {
        val message = ChatMessage(
            role = ChatRole.ASSISTANT,
            content = "",
            toolCalls = listOf(
                ToolCall(id = "c1", name = "web_search", arguments = """{"query":"a"}"""),
                ToolCall(id = "c2", name = "web_search", arguments = """{"query":"b"}""")
            )
        )
        val contents = with(GeminiProvider()) {
            listOf(message).toGeminiContents(json, unsignedFallback = true)
        }
        val parts = contents.single().parts
        assertEquals("skip_thought_signature_validator", parts[0].thoughtSignature)
        assertNull(parts[1].thoughtSignature)
    }

    @Test
    fun `parallel call ids are replayed when available`() {
        val assistant = ChatMessage(
            role = ChatRole.ASSISTANT,
            content = "",
            toolCalls = listOf(
                ToolCall(
                    id = "c1",
                    name = "web_search",
                    arguments = """{"query":"x"}""",
                    providerId = "call_1"
                )
            )
        )
        val result = ChatMessage(
            role = ChatRole.TOOL,
            content = "ok",
            toolCallId = "c1",
            toolName = "web_search"
        )
        val contents = with(GeminiProvider()) {
            listOf(assistant, result).toGeminiContents(json, includeCallIds = true)
        }
        assertEquals("call_1", contents[0].parts.single().functionCall?.id)
        assertEquals("call_1", contents[1].parts.single().functionResponse?.id)
    }

    @Test
    fun `response part exposes thought signature`() {
        val raw = """
            {"candidates":[{"content":{"role":"model","parts":[
              {"functionCall":{"name":"web_search","args":{"query":"x"}},"thoughtSignature":"abc"}
            ]}}]}
        """.trimIndent()
        val response = json.decodeFromString(GeminiResponse.serializer(), raw)
        val part = response.candidates.single().content!!.parts.single()
        assertEquals("abc", part.thoughtSignature)
        assertEquals("web_search", part.functionCall!!.name)
    }
}
