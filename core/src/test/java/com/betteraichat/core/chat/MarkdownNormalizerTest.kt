package com.betteraichat.core.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownNormalizerTest {

    @Test
    fun `full width pipe table fixed`() {
        val input = "| 列一 ｜ 列二 ｜ 列三 |\n| 1 ｜ 2 ｜ 3 |"
        val out = MarkdownNormalizer.normalize(input)
        assertFalse("全角竖线应被替换", out.contains('｜'))
        assertTrue("应保留半角竖线", out.contains('|'))
        assertEquals(2, out.lines().count { it.contains('|') })
    }

    @Test
    fun `single full width pipe untouched`() {
        val input = "他｜她 都是代词"
        assertEquals(input, MarkdownNormalizer.normalize(input))
    }

    @Test
    fun `underscore emphasis converted with word boundary guard`() {
        assertEquals("**加粗**", MarkdownNormalizer.normalize("__加粗__"))
        assertEquals("**bold** text", MarkdownNormalizer.normalize("__bold__ text"))
        // 单词中间的下划线不应被误伤
        assertEquals("a__b", MarkdownNormalizer.normalize("a__b"))
        assertEquals("1__2", MarkdownNormalizer.normalize("1__2"))
        assertEquals("v__x__y", MarkdownNormalizer.normalize("v__x__y"))
    }

    @Test
    fun `triple star simplified`() {
        assertEquals("**加粗**", MarkdownNormalizer.normalize("***加粗***"))
    }

    @Test
    fun `strip code blocks leaves rest intact`() {
        val input = "```kotlin\nfun main() {}\n```\n后续文本"
        val out = MarkdownNormalizer.stripCodeBlocks(input)
        assertFalse(out.contains("kotlin"))
        assertFalse(out.contains("fun main"))
        assertTrue("代码块后的文本应保留", out.contains("后续文本"))
        assertFalse("不应留下可能开启新围栏的反引号", out.contains("``"))
    }

    @Test
    fun `extract code blocks`() {
        val input = "text\n```python\nprint(1)\n```\nmore\n```\nplain\n```"
        val blocks = MarkdownNormalizer.extractCodeBlocks(input)
        assertEquals(listOf("print(1)", "plain"), blocks)
    }

    @Test
    fun `extract links dedupe`() {
        val input = "[a](https://example.com/a) [b](https://example.com/a) [c](ftp://x.y/z)"
        val links = MarkdownNormalizer.extractLinks(input)
        assertEquals(2, links.size)
        assertEquals("https://example.com/a", links[0])
    }

    @Test
    fun `strip multiple blocks`() {
        val input = "```a\n1\n```\n中\n```b\n2\n```"
        assertEquals("中", MarkdownNormalizer.stripCodeBlocks(input).trim())
    }
}
