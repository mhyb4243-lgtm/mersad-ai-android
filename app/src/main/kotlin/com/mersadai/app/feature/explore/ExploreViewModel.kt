package com.mersadai.app.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mersadai.app.core.network.NetworkMonitor
import com.mersadai.app.data.translation.CachedTranslationService
import kotlinx.coroutines.CancellationException
import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.SyncRecord
import com.mersadai.app.domain.repository.ContentRepository
import com.mersadai.app.feature.explore.hasExcludedPromptStyle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class ExploreViewModel(
    private val repository: ContentRepository,
    networkMonitor: NetworkMonitor,
    private val translationService: CachedTranslationService,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedSection = MutableStateFlow<HomeSection?>(null)
    private val selectedSource = MutableStateFlow<String?>(null)
    private val translationJobs = MutableStateFlow<Set<String>>(emptySet())
    private val translationFailures = MutableStateFlow<Set<String>>(emptySet())

    val searchQuery: StateFlow<String> = query
    val searchSection: StateFlow<HomeSection?> = selectedSection
    val searchSource: StateFlow<String?> = selectedSource
    val translatingFields: StateFlow<Set<String>> = translationJobs
    val failedTranslations: StateFlow<Set<String>> = translationFailures
    val items: StateFlow<List<ContentItem>> = repository.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites: StateFlow<List<ContentItem>> = repository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val searchResults: StateFlow<List<ContentItem>> = query
        .debounce(250)
        .flatMapLatest(repository::searchItems)
        .combine(selectedSection.combine(selectedSource) { section, source -> section to source }) { results, filters ->
            val (section, source) = filters
            results.filter { item ->
                !item.hasExcludedPromptStyle() &&
                    (section == null || item.belongsToHomeSection(section)) &&
                    (source == null || item.source?.id == source)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val syncRecord: StateFlow<SyncRecord?> = repository.observeSyncRecord()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isOnline = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun updateQuery(value: String) {
        query.value = value
    }

    fun selectSearchSection(section: HomeSection?) {
        selectedSection.value = section
    }

    fun selectSearchSource(sourceId: String?) {
        selectedSource.value = sourceId
    }

    fun translateToArabic(itemId: String, field: String, sourceText: String) {
        val key = "$itemId:$field"
        if (key in translationJobs.value || sourceText.isBlank()) return
        translationJobs.value = translationJobs.value + key
        translationFailures.value = translationFailures.value - key
        viewModelScope.launch {
            try {
                if (translationService.translateToArabic(itemId, field, sourceText) == null) {
                    translationFailures.value = translationFailures.value + key
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                translationFailures.value = translationFailures.value + key
            } finally {
                translationJobs.value = translationJobs.value - key
            }
        }
    }

    fun setFavorite(id: String, favorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(id, favorite) }
    }

    fun observeItem(id: String) = repository.observeItem(id)

    fun observeFavorite(id: String) = repository.observeFavorite(id)

    fun clearCache() {
        viewModelScope.launch { repository.clearCache() }
    }
}
