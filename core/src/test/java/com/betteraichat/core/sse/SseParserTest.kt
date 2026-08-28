package com.betteraichat.core.sse

import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

class SseParserTest {

    private fun bodyOf(text: String): ResponseBody {
        val buffer = Buffer().writeUtf8(text)
        return ResponseBody.create(
            "text/event-stream".toMediaType(),
            buffer.size,
            buffer
        )
    }

    @Test
    fun `parses multiple data events`() = runBlocking {
        val sse = "data: {\"a\":1}\n\ndata: {\"b\":2}\n\n"
        val events = mutableListOf<String>()
        SseParser.parse(bodyOf(sse)) { _, data ->
            events.add(data)
            true
        }.collect { }
        assertEquals(listOf("{\"a\":1}", "{\"b\":2}"), events)
    }

    @Test
    fun `stops on false return`() = runBlocking {
        val sse = "data: one\n\ndata: two\n\ndata: three\n\n"
        val events = mutableListOf<String>()
        SseParser.parse(bodyOf(sse)) { _, data ->
            events.add(data)
            data != "two"
        }.collect { }
        assertEquals(listOf("one", "two"), events)
    }

    @Test
    fun `ignores comments and non-data lines`() = runBlocking {
        val sse = ": comment\n\nkeep: x\n\ndata: hello\n\n"
        val events = mutableListOf<String>()
        SseParser.parse(bodyOf(sse)) { _, data ->
            events.add(data)
            true
        }.collect { }
        assertEquals(listOf("hello"), events)
    }

    @Test
    fun `handles trailing event without blank line`() = runBlocking {
        val sse = "data: first\n\ndata: last"
        val events = mutableListOf<String>()
        SseParser.parse(bodyOf(sse)) { _, data ->
            events.add(data)
            true
        }.collect { }
        assertEquals(listOf("first", "last"), events)
    }
}
