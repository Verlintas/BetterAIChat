package com.betteraichat.skills.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.runBlocking

class CalculatorToolTest {

    private val tool = CalculatorTool()

    private fun calc(expr: String): String = kotlinx.coroutines.runBlocking {
        val args = buildJsonObject { put("expression", expr) }
        val fakeContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        tool.execute(
            com.betteraichat.skills.ToolContext(
                appContext = fakeContext,
                screenshotProvider = com.betteraichat.skills.ScreenshotProvider { "x" }
            ),
            args
        )
    }

    @Test
    fun `basic arithmetic`() {
        assertEquals("8", calc("3 + 5"))
        assertEquals("15", calc("3 * 5"))
        assertEquals("2", calc("10 / 5"))
        assertEquals("1", calc("10 % 3"))
    }

    @Test
    fun `parentheses and precedence`() {
        assertEquals("38.5", calc("(15 + 7) * 3.5 / 2"))
        assertEquals("23", calc("2 + 3 * 7"))
        assertEquals("35", calc("(2 + 3) * 7"))
    }

    @Test
    fun `power and negatives`() {
        assertEquals("256", calc("2^8"))
        assertEquals("-5", calc("-5"))
        assertEquals("8", calc("2^3"))
        assertEquals("0.25", calc("2^-2"))
    }

    @Test
    fun `division by zero is rejected`() {
        val r = calc("1/0")
        assertEquals(true, r.startsWith("ERROR"))
    }

    @Test
    fun `invalid input rejected`() {
        val r1 = calc("abc")
        assertEquals(true, r1.startsWith("ERROR"))
        val r2 = calc("")
        assertEquals(true, r2.startsWith("ERROR") || r2.contains("不能为空"))
        val r3 = calc("2 +")
        assertEquals(true, r3.startsWith("ERROR"))
    }
    @Test
    fun `right associative power`() {
        assertEquals("512", calc("2^3^2"))
        assertEquals("9", calc("3^2"))
    }

    @Test
    fun `edge inputs`() {
        assertEquals(true, calc("").contains("不能为空"))
        assertEquals("0", calc("0 * 999"))
        assertEquals("1", calc("1 + 0"))
        assertEquals(true, calc("1/0").startsWith("ERROR"))
        assertEquals(true, calc("abc").startsWith("ERROR"))
    }

    @Test
    fun `unary minus inside parens`() {
        assertEquals("-7", calc("3 + -10"))
        assertEquals("6", calc("2 * -3 * -1"))
    }


    @Test
    fun `very long expression rejected`() {
        val long = "1".repeat(600) + "+2"
        val r = calc(long)
        assertEquals(true, r.startsWith("ERROR"))
    }
}
