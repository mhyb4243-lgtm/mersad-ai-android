package com.mersadai.app.domain.prompts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FactVerseContentTest {
    @Test
    fun createsFactVerseAssetsWithThreeTimedEnglishScenesAndSignature() {
        val content = FactVerseContent.create("Quantum battery", "A new material stores energy.")

        assertTrue(content.visualPrompt.contains("split-screen"))
        assertTrue(content.visualPrompt.contains("LEFT PANEL"))
        assertTrue(content.visualPrompt.contains("RIGHT PANEL"))
        assertTrue(content.reelsScript.contains("Scene 1 (0-10s): Hook & Visual"))
        assertTrue(content.reelsScript.contains("Scene 2 (10-20s): Science Breakdown"))
        assertTrue(content.reelsScript.contains("Scene 3 (20-30s): Future Impact & Outro"))
        assertTrue(content.reelsScript.contains(FactVerseContent.SIGNATURE))
    }

    @Test
    fun serializedAssetsRoundTrip() {
        val expected = FactVerseContent.create("Discovery", "A short summary.")
        assertEquals(expected, FactVerseContent.parse(expected.serialize()))
    }
}
