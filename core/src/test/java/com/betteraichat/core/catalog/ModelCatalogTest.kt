package com.betteraichat.core.catalog

import com.betteraichat.core.model.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun `minor model versions inherit the family entry`() {
        val entry = ModelCatalog.entryFor(ProviderId.ANTHROPIC, "claude-sonnet-5-5")
        assertEquals("claude-sonnet-5-5", entry.id)
        assertTrue(entry.supportsReasoning)
        assertEquals(200_000, entry.contextWindow)
    }

    @Test
    fun `unknown models fall back with reasoning disabled`() {
        val entry = ModelCatalog.entryFor(ProviderId.OPENAI_COMPAT, "some-custom-model")
        assertEquals("some-custom-model", entry.id)
        assertFalse(entry.supportsReasoning)
    }
}
