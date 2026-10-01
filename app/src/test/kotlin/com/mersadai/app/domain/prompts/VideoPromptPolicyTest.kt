package com.mersadai.app.domain.prompts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPromptPolicyTest {
    @Test
    fun formatsThirtySecondPromptAsThreeCopyableScenesWithArabicProductionText() {
        val result = VideoPromptPolicy.apply(
            """Create a 30-second film starring [CHARACTER].
                |Scene 1 (0-10s): Introduce the character.
                |Scene 2 (10-20s): The character discovers a clue.
                |Scene 3 (20-30s): The character resolves the mystery.
            """.trimMargin(),
        )

        val scenes = VideoPromptPolicy.scenes(result)

        assertEquals(listOf(1, 2, 3), scenes.map { it.number })
        assertEquals(listOf(0 to 10, 10 to 20, 20 to 30), scenes.map { it.startSeconds to it.endSeconds })
        scenes.forEach { scene ->
            assertTrue(scene.prompt.contains("Audio/Voiceover (Arabic):"))
            assertTrue(scene.prompt.contains("On-screen text:"))
            assertTrue(scene.prompt.contains(VideoPromptPolicy.LOWER_THIRD))
        }
        assertTrue(!scenes[0].prompt.contains("Consistent Character Parameters:"))
        assertTrue(scenes[1].prompt.contains("Consistent Character Parameters:"))
        assertTrue(scenes[2].prompt.contains("Consistent Character Parameters:"))
        assertTrue(scenes[0].standalonePrompt().startsWith("Scene 1 (0-10s):"))
    }

    @Test
    fun createsThreeScenesWhenThirtySecondPromptHasNoSceneMarkers() {
        val scenes = VideoPromptPolicy.scenes(
            VideoPromptPolicy.apply("Create a 30-second cinematic story.", isThirtySeconds = true),
        )

        assertEquals(3, scenes.size)
        assertTrue(scenes.all { it.prompt.isNotBlank() })
    }
}