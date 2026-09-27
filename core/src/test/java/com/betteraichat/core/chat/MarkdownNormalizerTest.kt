package com.betteraichat.core.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

class IntrawordBoldTest {
    @Test
    fun `cjk intraword bold gets fixed with hair spaces`() {
        val out = com.betteraichat.core.chat.MarkdownNormalizer.normalize("中文**加粗**中文")
        assertTrue(out.contains("\u200A**加粗**\u200A"))
    }

    @Test
    fun `standalone bold untouched`() {
        val input = "**加粗** 正常"
        val out = com.betteraichat.core.chat.MarkdownNormalizer.normalize(input)
        assertEquals(input, out)
    }

    @Test
    fun `multiple adjacent intraword bolds all fixed`() {
        val out = com.betteraichat.core.chat.MarkdownNormalizer.normalize("中**a**文**b**字")
        assertTrue(out.contains("\u200A**a**\u200A"))
        assertTrue(out.contains("\u200A**b**\u200A"))
    }

    @Test
    fun `triple star then intraword`() {
        val out = com.betteraichat.core.chat.MarkdownNormalizer.normalize("前***粗斜***后")
        assertTrue(out.contains("\u200A**粗斜**\u200A"))
        assertFalse(out.contains("***"))
    }

    @Test
    fun `table parsing with alignment and padding`() {
        val md = "| 名称 | 数量 | 备注 |\n|:---|:---:|---:|\n| 苹果 | 3 | 甜 |\n| 梨 | 5 |"
        val data = com.betteraichat.core.chat.MarkdownNormalizer.parseTable(md)
        assertEquals(3, data!!.headers.size)
        assertEquals(2, data.rows.size)
        assertEquals(3, data.rows[1].size)
        assertEquals(com.betteraichat.core.chat.MarkdownNormalizer.TableAlign.START, data.alignments[0])
        assertEquals(com.betteraichat.core.chat.MarkdownNormalizer.TableAlign.CENTER, data.alignments[1])
        assertEquals(com.betteraichat.core.chat.MarkdownNormalizer.TableAlign.END, data.alignments[2])
        assertEquals("", data.rows[1][2])
    }

    @Test
    fun `escaped pipe stays in cell`() {
        val md = "| a | b |\n|---|---|\n| x \\| y | z |"
        val data = com.betteraichat.core.chat.MarkdownNormalizer.parseTable(md)
        assertEquals("x | y", data!!.rows[0][0])
    }

    @Test
    fun `windows paths keep backslashes in cells`() {
        val md = "| 路径 | 大小 |\n|---|---|\n| C:\\Users\\test\\file.txt | 1KB |"
        val data = com.betteraichat.core.chat.MarkdownNormalizer.parseTable(md)
        assertEquals("C:\\Users\\test\\file.txt", data!!.rows[0][0])
    }

    @Test
    fun `double backslash escapes to single`() {
        val md = "| a |\n|---|\n| C:\\\\dir |"
        val data = com.betteraichat.core.chat.MarkdownNormalizer.parseTable(md)
        assertEquals("C:\\dir", data!!.rows[0][0])
    }

    @Test
    fun `trailing backslash preserved`() {
        val md = "| a |\n|---|\n| dir\\ |"
        val data = com.betteraichat.core.chat.MarkdownNormalizer.parseTable(md)
        assertEquals("dir\\", data!!.rows[0][0])
    }
}

class NormalizeV4Test {
    @Test
    fun `heading without space fixed`() {
        val out = com.betteraichat.core.chat.MarkdownNormalizer.normalize("###标题\n正文")
        assertTrue(out.startsWith("### 标题"))
    }

    @Test
    fun `dash list without space fixed`() {
        val out = com.betteraichat.core.chat.MarkdownNormalizer.normalize("-项目一\n-项目二")
        assertTrue(out.contains("- 项目一"))
        assertTrue(out.contains("- 项目二"))
    }

    @Test
    fun `ordered list without space fixed`() {
        val out = com.betteraichat.core.chat.MarkdownNormalizer.normalize("1.第一项\n2.第二项")
        assertTrue(out.contains("1. 第一项"))
    }

    @Test
    fun `version numbers untouched`() {
        val out = com.betteraichat.core.chat.MarkdownNormalizer.normalize("升级到 1.5 版本")
        assertEquals("升级到 1.5 版本", out)
    }

    @Test
    fun `quote without space fixed`() {
        val out = com.betteraichat.core.chat.MarkdownNormalizer.normalize(">引用内容")
        assertTrue(out.startsWith("> 引用内容"))
    }

    @Test
    fun `fullwidth stars converted`() {
        val out = com.betteraichat.core.chat.MarkdownNormalizer.normalize("＊＊加粗＊＊")
        assertTrue(out.contains("**加粗**"))
    }

    @Test
    fun `table without outer pipes parses`() {
        val md = "项目 | 价格 | 备注\n--- | :---: | ---:\n苹果 | 5 | 甜\n香蕉 | 3 | 糯"
        val data = com.betteraichat.core.chat.MarkdownNormalizer.parseTable(md)
        assertNotNull(data)
        assertEquals(3, data!!.headers.size)
        assertEquals("项目", data.headers[0])
        assertEquals(2, data.rows.size)
        assertEquals(com.betteraichat.core.chat.MarkdownNormalizer.TableAlign.CENTER, data.alignments[1])
        assertEquals(com.betteraichat.core.chat.MarkdownNormalizer.TableAlign.END, data.alignments[2])
    }

    @Test
    fun `table with outer pipes still parses`() {
        val md = "| a | b |\n|---|---|\n| 1 | 2 |"
        val data = com.betteraichat.core.chat.MarkdownNormalizer.parseTable(md)
        assertNotNull(data)
        assertEquals(2, data!!.headers.size)
    }
}
