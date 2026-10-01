package com.mersadai.app.feature.explore

import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.ContentType

enum class HomeSection {
    LATEST,
    AI_TOOLS,
    ANDROID_PROJECTS,
    ANDROID_MEDIA_DESIGN,
    MODELS,
    PROMPTS,
    IMAGE_PROMPTS,
    REELS_PROMPTS,
    CHARACTER_PROMPTS,
    PHOTOREALISTIC_PROMPTS,
    VISUAL_TRICKS,
    SOCIAL_PORTRAITS,
    BTS_FILMMAKING,
    FREE_PERKS,
    AI_NEWS,
    DEVELOPER_TOOLS,
}

data class HomeSectionContent(val section: HomeSection, val items: List<ContentItem>)

fun ContentItem.sourceTimestamp(): Long? = publishedAt ?: pushedAt ?: sourceUpdatedAt

fun ContentItem.isNewAt(now: Long, maxAgeMillis: Long = 7 * 24 * 60 * 60 * 1000L): Boolean =
    sourceTimestamp()?.let { it in (now - maxAgeMillis)..now } == true

fun homeSections(items: List<ContentItem>, limit: Int = 5): List<HomeSectionContent> =
    HomeSection.entries.mapNotNull { section ->
        val matching = items.asSequence()
            .filter { it.belongsToHomeSection(section) }
            .sortedByDescending { it.sourceTimestamp() ?: Long.MIN_VALUE }
            .take(limit)
            .toList()
        matching.takeIf { it.isNotEmpty() }?.let { HomeSectionContent(section, it) }
    }

fun ContentItem.isCreativeAndroidApp(): Boolean {
    if (contentType != ContentType.ANDROID_PROJECT) return false
    val searchableText = listOfNotNull(
        category?.id,
        category?.name,
        title,
        description,
        originalTitle,
        originalDescription,
    ).plus(tags).joinToString(" ").lowercase()
    return listOf(
        "photo-editor",
        "video-editor",
        "generative-ai",
        "on-device-ai",
        "design-tool",
        "moneyprinterturbo",
        "pocketpal",
        "krita",
        "seal",
        "creative app",
        "android media",
        "design tool",
    ).any(searchableText::contains)
}

fun ContentItem.belongsToHomeSection(section: HomeSection): Boolean = when (section) {
    HomeSection.LATEST -> sourceTimestamp() != null
    HomeSection.AI_TOOLS -> contentType == ContentType.AI_TOOL && source?.id in setOf("hf-spaces", "ai-offers", "official-free-perks") && category?.id != "free-perks"
    HomeSection.ANDROID_PROJECTS -> contentType == ContentType.ANDROID_PROJECT && source?.id == "github" && !isCreativeAndroidApp()
    HomeSection.ANDROID_MEDIA_DESIGN -> contentType == ContentType.ANDROID_PROJECT && (category?.id == "android-media-design" || isCreativeAndroidApp())
    HomeSection.MODELS -> contentType == ContentType.MODEL && source?.id == "hf-models"
    HomeSection.PROMPTS -> contentType == ContentType.PROMPT && source?.id == "prompts-chat" && !hasExcludedPromptStyle()
    HomeSection.IMAGE_PROMPTS -> contentType == ContentType.PROMPT && source?.id == "image-prompts" && !hasExcludedPromptStyle()
    HomeSection.REELS_PROMPTS -> contentType == ContentType.PROMPT && category?.id == "reels-prompts" && !hasExcludedPromptStyle()
    HomeSection.CHARACTER_PROMPTS -> contentType == ContentType.PROMPT && category?.id == "character-prompts" && !hasExcludedPromptStyle()
    HomeSection.PHOTOREALISTIC_PROMPTS -> contentType == ContentType.PROMPT && category?.id == "photorealistic-prompts" && !hasExcludedPromptStyle()
    HomeSection.VISUAL_TRICKS -> contentType == ContentType.PROMPT && category?.id == "visual-tricks" && !hasExcludedPromptStyle()
    HomeSection.SOCIAL_PORTRAITS -> contentType == ContentType.PROMPT && category?.id == "social-portraits" && !hasExcludedPromptStyle()
    HomeSection.BTS_FILMMAKING -> contentType == ContentType.PROMPT && category?.id == "bts-filmmaking"
    HomeSection.FREE_PERKS -> contentType == ContentType.AI_TOOL && (category?.id == "free-perks" || source?.id == "official-free-perks")
    HomeSection.AI_NEWS -> contentType == ContentType.NEWS && source?.id == "openai-news"
    HomeSection.DEVELOPER_TOOLS -> contentType == ContentType.NEWS &&
        source?.id in setOf("android-developers", "google-developers")
}

fun ContentItem.hasExcludedPromptStyle(): Boolean {
    if (contentType != ContentType.PROMPT) return false
    val searchableText = listOfNotNull(title, description, originalTitle, originalDescription)
        .plus(tags)
        .joinToString(" ")
        .lowercase()
    return listOf(
        "abstract texture only",
        "abstract shapes background",
        "purely abstract backdrop",
        "non-euclidean noise",
        "خلفية تجريدية فقط",
        "أشكال تجريدية بلا موضوع",
        "ملمس تجريدي فقط",
    )
        .any(searchableText::contains)
}