package com.mersadai.app.domain.search

import com.mersadai.app.domain.model.ContentItem

object SimilarContent {
    fun find(current: ContentItem, candidates: List<ContentItem>, limit: Int = 5): List<ContentItem> {
        if (limit <= 0) return emptyList()
        val currentTags = current.tags.map(LocalContentSearch::normalize).toSet()
        val currentKeywords = keywords(current)
        return candidates.asSequence()
            .filter { it.id != current.id && it.contentType == current.contentType }
            .mapNotNull { candidate ->
                val score = similarity(current, candidate, currentTags, currentKeywords)
                if (score == 0) null else candidate to score
            }
            .sortedWith(
                compareByDescending<Pair<ContentItem, Int>> { it.second }
                    .thenByDescending { it.first.sourceTimestamp() ?: Long.MIN_VALUE }
                    .thenBy { it.first.id },
            )
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun similarity(
        current: ContentItem,
        candidate: ContentItem,
        currentTags: Set<String>,
        currentKeywords: Set<String>,
    ): Int {
        var score = SAME_TYPE_WEIGHT
        if (current.category?.id != null && current.category.id == candidate.category?.id) score += CATEGORY_WEIGHT
        if (current.source?.id != null && current.source.id == candidate.source?.id) score += SOURCE_WEIGHT
        if (current.pipelineTag != null && current.pipelineTag == candidate.pipelineTag) score += PIPELINE_WEIGHT
        if (current.pipelineCategory != null && current.pipelineCategory == candidate.pipelineCategory) score += PIPELINE_CATEGORY_WEIGHT
        score += currentTags.intersect(candidate.tags.map(LocalContentSearch::normalize).toSet()).size * TAG_WEIGHT
        score += currentKeywords.intersect(keywords(candidate)).size * KEYWORD_WEIGHT
        return score.takeIf { it > SAME_TYPE_WEIGHT } ?: 0
    }

    private fun keywords(item: ContentItem): Set<String> = listOfNotNull(
        item.displayTitleAr,
        item.title,
        item.originalTitle,
        item.description,
        item.originalDescription,
    ).joinToString(" ")
        .let(LocalContentSearch::normalize)
        .split(' ')
        .filter { it.length >= 3 && it !in STOP_WORDS }
        .toSet()

    private fun ContentItem.sourceTimestamp(): Long? = publishedAt ?: pushedAt ?: sourceUpdatedAt

    private val STOP_WORDS = setOf("the", "and", "for", "with", "from", "هذا", "هذه", "من", "على", "الى", "في")
    private const val SAME_TYPE_WEIGHT = 10
    private const val CATEGORY_WEIGHT = 40
    private const val SOURCE_WEIGHT = 20
    private const val PIPELINE_WEIGHT = 60
    private const val PIPELINE_CATEGORY_WEIGHT = 30
    private const val TAG_WEIGHT = 15
    private const val KEYWORD_WEIGHT = 5
}