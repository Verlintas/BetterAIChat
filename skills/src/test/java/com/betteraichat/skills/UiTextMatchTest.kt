package com.betteraichat.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiTextMatchTest {

    @Test
    fun `exact match wins`() {
        assertEquals(0, UiTextMatch.score("Settings", "settings"))
    }

    @Test
    fun `normalized match ignores punctuation and spaces`() {
        assertEquals(1, UiTextMatch.score("OK!", "ok"))
        assertEquals(1, UiTextMatch.score("Sign in", "signin"))
    }

    @Test
    fun `prefix and substring are ordered`() {
        assertEquals(2, UiTextMatch.score("Settings and more", "settings"))
        assertEquals(3, UiTextMatch.score("Open settings now", "settings"))
    }

    @Test
    fun `short label inside a longer query still matches`() {
        assertEquals(4, UiTextMatch.score("OK", "tap ok button"))
    }

    @Test
    fun `typos within two edits match`() {
        assertEquals(5, UiTextMatch.score("Settings", "setings"))
    }

    @Test
    fun `unrelated labels do not match`() {
        assertNull(UiTextMatch.score("Camera", "settings"))
        assertEquals(3, UiTextMatch.distance("kitten", "sitting"))
    }
}
