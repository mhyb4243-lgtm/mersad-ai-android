package com.mersadai.app.domain.prompts

object VideoPromptPolicy {
    const val LOWER_THIRD = "Lower-third overlay: Arabic text \"محمد ابوهادي\" with green WhatsApp icon and \"+967776577658\""
    private const val CHARACTER_CONTINUITY = "Consistent Character Parameters: Preserve the exact same face, facial features, age, skin tone, hairstyle, body shape, wardrobe, and identity established in Scene 1. Change only what the story requires."
    private val sceneHeader = Regex("(?im)^Scene\\s+([1-3])\\s*\\(\\s*(\\d+)\\s*-\\s*(\\d+)s\\s*\\):")

    data class ScenePrompt(
        val number: Int,
        val startSeconds: Int,
        val endSeconds: Int,
        val prompt: String,
    ) {
        fun standalonePrompt(): String = "Scene $number ($startSeconds-${endSeconds}s):\n$prompt"
    }

    fun apply(prompt: String, isThirtySeconds: Boolean = false): String {
        val basePrompt = prompt.lineSequence()
            .filterNot { line ->
                val trimmed = line.trim()
                listOf(
                    "Lower-third overlay:",
                    "Audio/Voiceover (Arabic):",
                    "On-screen text:",
                    "Consistent Character Parameters:",
                ).any { trimmed.startsWith(it, ignoreCase = true) }
            }
            .joinToString("\n")
            .trim()
        val requiresSceneStructure = isThirtySeconds ||
            Regex("(?i)\\b30\\s*(?:-\\s*)?seconds?\\b|\\b30s\\b").containsMatchIn(prompt)

        if (requiresSceneStructure) {
            val existingScenes = scenes(basePrompt).associateBy { it.number }
            val firstSceneStart = sceneHeader.find(basePrompt)?.range?.first ?: basePrompt.length
            val sharedSetup = basePrompt.substring(0, firstSceneStart).trim()
            val sceneDescriptions = listOf(
                "Establish the location and introduce the main action.",
                "Continue the same action with a motivated professional camera move.",
                "Resolve the action and finish on a clear cinematic hero frame.",
            )
            val voiceovers = listOf(
                "في كل بداية، تولد فكرة تستحق أن تُروى.",
                "ومع كل خطوة، تتضح ملامح الحكاية.",
                "هنا تكتمل الحكاية، وتبدأ لحظتك.",
            )
            val screenTexts = listOf("كل حكاية تبدأ بفكرة", "خطوة تقرّب الصورة", "لحظتك تبدأ الآن")
            val formattedScenes = (1..3).map { number ->
                val start = (number - 1) * 10
                val end = number * 10
                val sceneBody = existingScenes[number]?.prompt
                    ?.lineSequence()
                    ?.filterNot { line ->
                        line.trim().startsWith("Consistent Character Parameters:", ignoreCase = true)
                    }
                    ?.joinToString("\n")
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: sceneDescriptions[number - 1]
                buildList {
                    add("Scene $number ($start-${end}s): $sceneBody")
                    if (number > 1) add(CHARACTER_CONTINUITY)
                    add("Audio/Voiceover (Arabic): \"${voiceovers[number - 1]}\"")
                    add("On-screen text: \"${screenTexts[number - 1]}\"")
                    add(LOWER_THIRD)
                }.joinToString("\n")
            }
            return (listOf(sharedSetup) + formattedScenes)
                .filter(String::isNotBlank)
                .joinToString("\n\n")
        }

        return listOf(basePrompt, LOWER_THIRD).filter(String::isNotBlank).joinToString("\n\n")
    }

    fun scenes(prompt: String): List<ScenePrompt> {
        val matches = sceneHeader.findAll(prompt).toList()
        return matches.mapIndexedNotNull { index, match ->
            val start = match.range.last + 1
            val end = matches.getOrNull(index + 1)?.range?.first ?: prompt.length
            ScenePrompt(
                number = match.groupValues[1].toInt(),
                startSeconds = match.groupValues[2].toInt(),
                endSeconds = match.groupValues[3].toInt(),
                prompt = prompt.substring(start, end).trim(),
            )
        }
    }
}