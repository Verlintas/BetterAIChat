package com.betteraichat.skills.tools

import android.content.Context
import com.betteraichat.skills.ScreenshotProvider
import com.betteraichat.skills.ToolContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class UnitConvertToolTest {

    private val tool = UnitConvertTool()
    private val context = ToolContext(
        mock(Context::class.java),
        mock(ScreenshotProvider::class.java)
    )

    private fun convert(value: Double, from: String, to: String): String = runBlocking {
        tool.execute(context, buildJsonObject {
            put("value", value)
            put("from", from)
            put("to", to)
        })
    }

    @Test
    fun `speed conversion`() {
        assertEquals("100 km/h = 27.777778 m/s", convert(100.0, "km/h", "m/s"))
    }

    @Test
    fun `temperature conversion`() {
        assertEquals("100 c = 212 f", convert(100.0, "c", "f"))
        assertEquals("0 c = 32 f", convert(0.0, "c", "f"))
        assertEquals("0 c = 273.15 k", convert(0.0, "c", "k"))
    }

    @Test
    fun `weight conversion with alias`() {
        assertEquals("1 公斤 = 2.204623 lb", convert(1.0, "公斤", "lb"))
    }

    @Test
    fun `data conversion`() {
        assertEquals("1 gb = 1024 mb", convert(1.0, "gb", "mb"))
    }

    @Test
    fun `incompatible units rejected`() {
        val r = convert(1.0, "kg", "km")
        assertTrue(r.contains("不支持"))
    }
}
