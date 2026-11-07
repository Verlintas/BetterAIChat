package com.betteraichat.core.chat

import com.betteraichat.core.model.ChatMessage
import com.betteraichat.core.model.ChatRole
import com.betteraichat.core.model.ToolCall
import com.betteraichat.core.model.ToolCallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolTranscriptRepairTest {

    private fun assistant(vararg calls: ToolCall) = ChatMessage(
        role = ChatRole.ASSISTANT,
        content = "",
        toolCalls = calls.toList()
    )

    private fun user(text: String) = ChatMessage(role = ChatRole.USER, content = text)

    private fun tool(id: String) = ChatMessage(
        role = ChatRole.TOOL,
        content = "ok",
        toolCallId = id,
        toolName = "get_time"
    )

    private fun call(id: String, name: String = "get_time") =
        ToolCall(id = id, name = name, arguments = "{}", status = ToolCallStatus.DONE)

    @Test
    fun `complete transcript is unchanged`() {
        val history = listOf(user("a"), assistant(call("c1")), tool("c1"), user("b"))
        val repaired = repairToolTranscript(history)
        assertEquals(history, repaired)
    }

    @Test
    fun `missing result is appended right after the assistant message`() {
        val history = listOf(user("a"), assistant(call("c1")), user("next"))
        val repaired = repairToolTranscript(history)

        assertEquals(4, repaired.size)
        assertEquals(ChatRole.TOOL, repaired[2].role)
        assertEquals("c1", repaired[2].toolCallId)
        assertEquals(ChatRole.USER, repaired[3].role)
        assertTrue(repaired[2].content.contains("已取消"))
    }

    @Test
    fun `blank call id is normalized and matched`() {
        val history = listOf(user("a"), assistant(call("")), user("next"))
        val repaired = repairToolTranscript(history)

        val assistant = repaired[1]
        assertTrue(assistant.toolCalls.single().id.isNotBlank())
        assertEquals(assistant.toolCalls.single().id, repaired[2].toolCallId)
    }

    @Test
    fun `only the missing call in a parallel batch is filled`() {
        val history = listOf(
            user("a"),
            assistant(call("c1"), call("c2")),
            tool("c1"),
            user("next")
        )
        val repaired = repairToolTranscript(history)

        val toolIds = repaired.filter { it.role == ChatRole.TOOL }.mapNotNull { it.toolCallId }
        assertEquals(setOf("c1", "c2"), toolIds.toSet())
        assertEquals("c2", repaired[2].toolCallId)
    }
}
