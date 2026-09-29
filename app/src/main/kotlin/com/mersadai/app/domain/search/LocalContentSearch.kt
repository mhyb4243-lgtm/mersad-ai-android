package com.mersadai.app.domain.search

import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.ContentType
import java.text.Normalizer
import java.util.Locale

data class SearchFilters(
    val contentTypes: Set<ContentType> = emptySet(),
    val sourceId: String? = null,
)

object LocalContentSearch {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .filterNot { character ->
            character == '\u0640' || Character.getType(character) in setOf(
                Character.NON_SPACING_MARK.toInt(),
                Character.COMBINING_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt(),
            )
        }
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .lowercase(Locale.ROOT)
        .split(WHITESPACE)
        .filter(String::isNotBlank)
        .joinToString(" ")

    fun search(items: List<ContentItem>, query: String, filters: SearchFilters = SearchFilters()): List<ContentItem> {
        val normalizedQuery = normalize(query)
        val terms = normalizedQuery.split(' ').filter(String::isNotBlank)
        return items.asSequence()
            .filter { filters.contentTypes.isEmpty() || it.contentType in filters.contentTypes }
            .filter { filters.sourceId == null || it.source?.id == filters.sourceId }
            .mapNotNull { content ->
                val score = if (terms.isEmpty()) 0 else relevance(content, normalizedQuery, terms)
                if (terms.isNotEmpty() && score == 0) null else content to score
            }
            .sortedWith(
                compareByDescending<Pair<ContentItem, Int>> { it.second }
                    .thenByDescending { it.first.sourceTimestamp() ?: it.first.createdAt }
                    .thenBy { normalize(it.first.title) }
                    .thenBy { it.first.id },
            )
            .map { it.first }
            .toList()
    }

    private fun relevance(item: ContentItem, query: String, terms: List<String>): Int {
        val titles = listOfNotNull(item.displayTitleAr, item.title, item.originalTitle).distinct()
        val descriptions = listOfNotNull(item.displayDescriptionAr, item.description, item.originalDescription)
        val fields = buildList {
            titles.forEach { add(normalize(it) to TITLE_WEIGHT) }
            item.tags.forEach { add(normalize(it) to TAG_WEIGHT) }
            item.category?.let { add(normalize("${it.name} ${it.id}") to CATEGORY_WEIGHT) }
            item.source?.let { add(normalize("${it.name} ${it.id}") to SOURCE_WEIGHT) }
            add(normalize(item.contentType.name.replace('_', ' ')) to TYPE_WEIGHT)
            item.contentType.searchTerms().forEach { add(normalize(it) to TYPE_WEIGHT) }
            item.pipelineTag?.let { add(normalize(it) to TAG_WEIGHT) }
            descriptions.forEach { add(normalize(it) to DESCRIPTION_WEIGHT) }
        }
        return fields.sumOf { (field, weight) ->
            when {
                field == query -> weight * 3
                field.startsWith(query) -> weight * 2
                field.contains(query) -> weight
                terms.size > 1 && terms.all(field::contains) -> weight
                else -> 0
            }
        }
    }

    private fun ContentType.searchTerms(): List<String> = when (this) {
        ContentType.AI_TOOL -> listOf("ai tools", "أدوات الذكاء الاصطناعي", "أداة")
        ContentType.ANDROID_PROJECT -> listOf("android project", "مشاريع android", "أندرويد")
        ContentType.MODEL -> listOf("ai model", "نماذج الذكاء الاصطناعي", "نموذج")
        ContentType.PROMPT -> listOf("prompts", "prompt", "برومبت", "برومبتات", "أمر توليد", "أوامر توليد", "المطالبات النصية", "مطالبة")
        ContentType.NEWS -> listOf("news", "أخبار", "خبر")
        ContentType.OTHER -> emptyList()
    }

    private fun ContentItem.sourceTimestamp(): Long? = publishedAt ?: pushedAt ?: sourceUpdatedAt

    private val WHITESPACE = Regex("\\s+")
    private const val TITLE_WEIGHT = 100
    private const val TAG_WEIGHT = 60
    private const val CATEGORY_WEIGHT = 45
    private const val SOURCE_WEIGHT = 35
    private const val TYPE_WEIGHT = 30
    private const val DESCRIPTION_WEIGHT = 20
}