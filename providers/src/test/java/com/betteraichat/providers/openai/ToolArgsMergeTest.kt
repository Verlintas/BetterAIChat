package com.betteraichat.providers.openai

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolArgsMergeTest {

    @Test
    fun `standard prefix streaming accumulates`() {
        var acc = ""
        acc = mergeToolArgs("{\"que", acc)
        acc = mergeToolArgs("ry\":\"hel", acc)
        acc = mergeToolArgs("lo\"}", acc)
        assertEquals("{\"query\":\"hello\"}", acc)
    }

    @Test
    fun `full object resend replaces instead of duplicating`() {
        val first = "{\"query\":\"hello\"}"
        val resent = "{\"query\":\"hello world\"}"
        assertEquals(resent, mergeToolArgs(resent, first))
    }

    @Test
    fun `identical delta ignored`() {
        val v = "{\"a\":1}"
        assertEquals(v, mergeToolArgs(v, v))
    }

    @Test
    fun `stale shorter prefix ignored`() {
        val full = "{\"query\":\"hello\"}"
        assertEquals(full, mergeToolArgs("{\"query\":", full))
    }

    @Test
    fun `out of order fragments concatenate when valid`() {
        var acc = "{\"query\":\"he"
        acc = mergeToolArgs("llo\"}", acc)
        assertEquals("{\"query\":\"hello\"}", acc)
    }

    @Test
    fun `junk delta keeps existing object`() {
        val obj = "{\"a\":1}"
        assertEquals(obj, mergeToolArgs("oops", obj))
    }

    @Test
    fun `empty delta keeps existing`() {
        assertEquals("{\"a\":1}", mergeToolArgs("", "{\"a\":1}"))
    }
}
