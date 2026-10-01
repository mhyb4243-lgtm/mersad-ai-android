package com.mersadai.app.domain.prompts

object VideoPromptPolicy {
    const val LOWER_THIRD = "Lower-third overlay: Arabic text \"محمد ابوهادي\" with green WhatsApp icon and \"+967776577658\""

    fun apply(prompt: String, isThirtySeconds: Boolean = false): String {
        val basePrompt = prompt.lineSequence()
            .filterNot { it.trim().startsWith("Lower-third overlay:", ignoreCase = true) }
            .joinToString("\n")
            .trim()
        val sections = mutableListOf(basePrompt)
        val requiresSceneStructure = isThirtySeconds ||
            Regex("(?i)\\b30\\s*(?:-\\s*)?seconds?\\b|\\b30s\\b").containsMatchIn(prompt)
        val hasAllScenes = (1..3).all { scene ->
            val start = (scene - 1) * 10
            val end = scene * 10
            Regex("(?i)\\bscene\\s+$scene\\s*\\(\\s*$start\\s*-\\s*$end\\s*s\\s*\\)").containsMatchIn(prompt)
        }
        if (requiresSceneStructure && !hasAllScenes) {
            sections += """30-second sequence, exactly three connected scenes:
                |Scene 1 (0-10s): Establish the location and introduce the main action.
                |Scene 2 (10-20s): Continue the same action with a motivated professional camera move.
                |Scene 3 (20-30s): Resolve the action and finish on a clear cinematic hero frame.
                |Consistent Character Parameters: Preserve the same face, facial features, age, skin tone, hairstyle, body shape, wardrobe, and identity across all three scenes. Change only what the story explicitly requires.
            """.trimMargin()
        } else if (requiresSceneStructure && !prompt.contains("Consistent Character Parameters", ignoreCase = true)) {
            sections += "Consistent Character Parameters: Preserve the same face, facial features, age, skin tone, hairstyle, body shape, wardrobe, and identity across all three scenes."
        }
        sections += LOWER_THIRD
        return sections.filter(String::isNotBlank).joinToString("\n\n")
    }
}