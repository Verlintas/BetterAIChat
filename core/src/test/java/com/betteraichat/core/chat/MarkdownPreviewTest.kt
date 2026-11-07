package com.betteraichat.core.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownPreviewTest {

    @Test
    fun `bold and heading markers are removed`() {
        assertEquals(
            "Android 16 released on 2025-06-11",
            markdownPreview("## Android 16 released on **2025-06-11**")
        )
    }

    @Test
    fun `links keep their label and images are dropped to alt text`() {
        assertEquals(
            "see docs and logo",
            markdownPreview("see [docs](https://example.com) and ![logo](https://example.com/a.png)")
        )
    }

    @Test
    fun `fenced code blocks are removed`() {
        assertEquals(
            "before after",
            markdownPreview("before\n```kt\nval x = 1\n```\nafter")
        )
    }

    @Test
    fun `whitespace is collapsed and newlines removed`() {
        assertEquals("a b c", markdownPreview("a\n\n b\t c"))
    }

    @Test
    fun `blank input stays blank`() {
        assertEquals("", markdownPreview("   \n "))
    }
}
