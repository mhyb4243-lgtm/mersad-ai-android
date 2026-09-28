package com.mersadai.app.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mersadai.app.core.network.NetworkMonitor
import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.SyncRecord
import com.mersadai.app.domain.repository.ContentRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class ExploreViewModel(
    private val repository: ContentRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val searchQuery: StateFlow<String> = query
    val items: StateFlow<List<ContentItem>> = repository.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites: StateFlow<List<ContentItem>> = repository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val searchResults: StateFlow<List<ContentItem>> = query
        .debounce(200)
        .flatMapLatest(repository::searchItems)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val syncRecord: StateFlow<SyncRecord?> = repository.observeSyncRecord()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isOnline = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun updateQuery(value: String) {
        query.value = value
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
