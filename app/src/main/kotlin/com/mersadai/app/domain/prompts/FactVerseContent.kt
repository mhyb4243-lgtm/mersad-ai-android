package com.mersadai.app.domain.prompts

object FactVerseContent {
    private const val VISUAL_MARKER = "\n\n--- FACTVERSE VISUAL PROMPT ---\n"
    private const val REELS_MARKER = "\n\n--- FACTVERSE 30S REELS SCRIPT ---\n"
    const val SIGNATURE = "FactVerse • Explore The Future"

    data class Package(val visualPrompt: String, val reelsScript: String) {
        fun serialize(): String = VISUAL_MARKER + visualPrompt + REELS_MARKER + reelsScript
    }

    fun create(title: String, summary: String, visualPrompt: String? = null, reelsScript: String? = null): Package {
        val cleanTitle = title.trim().ifBlank { "Science discovery" }
        val cleanSummary = summary.trim().ifBlank { cleanTitle }
        return Package(
            visualPrompt = visualPrompt?.trim()?.takeIf(String::isNotBlank) ?: """
                Create a high-contrast sci-tech split-screen editorial image about "$cleanTitle".
                LEFT PANEL — show the real scientific mechanism described here: "$cleanSummary", rendered as a precise, credible close-up with labeled-free molecular, orbital, biological, or engineering detail appropriate to the subject.
                RIGHT PANEL — visualize the plausible future impact of the same discovery as an advanced but grounded human-scale application.
                Use a perfectly aligned vertical split, a shared central subject crossing both panels, deep midnight navy shadows, electric cyan and amber highlights, crisp rim lighting, cinematic volumetric haze, photorealistic materials, sharp scientific detail, premium magazine art direction, strong readable silhouette, balanced negative space, 35mm lens perspective. No text, no logos, no watermark, no invented scientific claims.
            """.trimIndent(),
            reelsScript = reelsScript?.trim()?.takeIf(String::isNotBlank) ?: """
                Scene 1 (0-10s): Hook & Visual — Open on a striking close-up of "$cleanTitle". Reveal the key visual contrast with a fast, controlled push-in. Voiceover: "What if this discovery changes how we understand the world?"
                Scene 2 (10-20s): Science Breakdown — Show the real mechanism behind the report using one clear, evidence-led visualization. Keep the camera steady and explain "$cleanSummary" in plain English. Voiceover: "Here is the science, and what researchers have actually found."
                Scene 3 (20-30s): Future Impact & Outro — Pull back from the experiment to a grounded glimpse of its possible future application. End on a clean high-contrast hero frame. Voiceover: "The next chapter is still being written. FactVerse • Explore The Future"
                End card: "$SIGNATURE"
            """.trimIndent(),
        )
    }

    fun parse(serialized: String): Package? {
        if (!serialized.startsWith(VISUAL_MARKER)) return null
        val reelsStart = serialized.indexOf(REELS_MARKER, VISUAL_MARKER.length)
        if (reelsStart < 0) return null
        val visual = serialized.substring(VISUAL_MARKER.length, reelsStart).trim()
        val reels = serialized.substring(reelsStart + REELS_MARKER.length).trim()
        return if (visual.isNotBlank() && reels.isNotBlank()) Package(visual, reels) else null
    }
}
