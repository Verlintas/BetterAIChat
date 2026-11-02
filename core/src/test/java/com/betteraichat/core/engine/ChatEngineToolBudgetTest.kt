package com.betteraichat.core.engine

import com.betteraichat.core.mode.AppMode
import com.betteraichat.core.model.ChatMessage
import com.betteraichat.core.model.ChatRole
import com.betteraichat.core.model.ProviderConfig
import com.betteraichat.core.model.ProviderId
import com.betteraichat.core.model.StreamEvent
import com.betteraichat.core.model.ToolCall
import com.betteraichat.core.model.ToolSpec
import com.betteraichat.core.provider.ChatProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatEngineToolBudgetTest {

    private val spec = ToolSpec(
        name = "get_time",
        description = "Returns the current time",
        parameters = JsonObject(emptyMap())
    )

    private val catalog = object : ToolCatalog {
        override fun specsFor(mode: AppMode): List<ToolSpec> = listOf(spec)
        override fun find(name: String): ToolSpec? = if (name == spec.name) spec else null
    }

    private val runner = object : ToolRunner {
        override suspend fun run(name: String, arguments: String): String = "12:00"
    }

    private val config = ProviderConfig(
        provider = ProviderId.OPENAI_COMPAT,
        baseUrl = "http://localhost/v1",
        apiKey = "test",
        model = "test-model",
        temperature = 0.7,
        maxTokens = 1024,
        reasoning = false
    )

    private class ScriptedProvider(
        private val script: List<(Int) -> List<StreamEvent>>
    ) : ChatProvider {
        val toolCounts = mutableListOf<Int>()
        val requests = mutableListOf<List<ChatMessage>>()

        override fun chatStream(
            messages: List<ChatMessage>,
            config: ProviderConfig,
            tools: List<ToolSpec>
        ): Flow<StreamEvent> = flow {
            toolCounts += tools.size
            requests += messages
            script[toolCounts.size - 1].invoke(toolCounts.size).forEach { emit(it) }
        }
    }

    private fun toolCallRound(id: String) = listOf(
        StreamEvent.ToolCallsDone(listOf(ToolCall(id = id, name = "get_time", arguments = "{}"))),
        StreamEvent.Done
    )

    private fun textRound(text: String) = listOf(
        StreamEvent.Delta(text),
        StreamEvent.Done
    )

    @Test
    fun `budget cap triggers a final answer without tools instead of failing`() = runBlocking {
        val provider = ScriptedProvider(listOf(
            { toolCallRound("c1") },
            { textRound("done: task paused, here is the progress") }
        ))
        val events = ChatEngine({ provider }, catalog, runner)
            .run(
                messages = listOf(ChatMessage(ChatRole.USER, "do the task")),
                config = config,
                mode = AppMode.MAX,
                maxToolCalls = 1
            )
            .toList()

        assertTrue(events.none { it is EngineEvent.Failed })
        assertTrue(events.last() is EngineEvent.Completed)
        assertEquals(listOf(1, 0), provider.toolCounts)
        val finalRequest = provider.requests.last()
        assertTrue(finalRequest.last().role == ChatRole.USER)
        assertTrue(finalRequest.last().content.contains("上限"))
        val assistant = events.filterIsInstance<EngineEvent.AssistantFinished>().map { it.message }
        assertEquals(2, assistant.size)
        assertEquals("done: task paused, here is the progress", assistant.last().content)
        assertTrue(assistant.last().toolCalls.isEmpty())
    }

    @Test
    fun `empty final answer at the budget cap still yields a summary`() = runBlocking {
        val provider = ScriptedProvider(listOf(
            { toolCallRound("c1") },
            { listOf(StreamEvent.Done) }
        ))
        val events = ChatEngine({ provider }, catalog, runner)
            .run(
                messages = listOf(ChatMessage(ChatRole.USER, "do the task")),
                config = config,
                mode = AppMode.MAX,
                maxToolCalls = 1
            )
            .toList()

        val assistant = events.filterIsInstance<EngineEvent.AssistantFinished>().last().message
        assertTrue(assistant.content.contains("上限"))
        assertTrue(assistant.toolCalls.isEmpty())
        assertTrue(events.any { it is EngineEvent.Delta && it.text.contains("上限") })
    }

    @Test
    fun `zero budget means unlimited tool calls`() = runBlocking {
        val provider = ScriptedProvider(listOf(
            { toolCallRound("c1") },
            { toolCallRound("c2") },
            { textRound("finished") }
        ))
        val events = ChatEngine({ provider }, catalog, runner)
            .run(
                messages = listOf(ChatMessage(ChatRole.USER, "do the task")),
                config = config,
                mode = AppMode.MAX,
                maxToolCalls = 0
            )
            .toList()

        assertTrue(events.none { it is EngineEvent.Failed })
        assertTrue(events.last() is EngineEvent.Completed)
        assertEquals(listOf(1, 1, 1), provider.toolCounts)
        assertFalse(provider.requests.last().any {
            it.role == ChatRole.USER && it.content.contains("上限")
        })
        assertEquals("finished", events.filterIsInstance<EngineEvent.AssistantFinished>().last().message.content)
    }
}
