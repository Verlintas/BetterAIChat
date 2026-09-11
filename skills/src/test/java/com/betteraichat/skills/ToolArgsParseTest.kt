package com.betteraichat.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ToolArgsParseTest {

    @Test
    fun `plain json object`() {
        val args = parseToolArguments("{\"query\":\"hello\",\"max\":3}")
        assertNotNull(args)
        assertEquals(2, args!!.size)
    }

    @Test
    fun `double encoded string object`() {
        val args = parseToolArguments("\"{\\\"query\\\":\\\"hello\\\"}\"")
        assertNotNull(args)
        assertEquals("hello", args!!["query"].toString().trim('"'))
    }

    @Test
    fun `text wrapped object`() {
        val args = parseToolArguments("Sure, here are the args: {\"query\":\"hello\"} — done")
        assertNotNull(args)
        assertEquals("hello", args!!["query"].toString().trim('"'))
    }

    @Test
    fun `plain string is not an object`() {
        assertNull(parseToolArguments("\"just a string\""))
    }

    @Test
    fun `garbage returns null`() {
        assertNull(parseToolArguments("not json at all"))
        assertNull(parseToolArguments(""))
    }

    @Test
    fun `number literal returns null`() {
        assertNull(parseToolArguments("42"))
    }
}
