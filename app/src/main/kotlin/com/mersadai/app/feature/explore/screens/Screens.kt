package com.mersadai.app.feature.explore.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.mersadai.app.R
import com.mersadai.app.data.local.SettingsRepository
import com.mersadai.app.domain.model.AppSettings
import com.mersadai.app.domain.model.Category
import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.FreeStatus
import com.mersadai.app.domain.model.SyncState
import com.mersadai.app.domain.model.ThemeMode
import com.mersadai.app.domain.model.VerificationLevel
import com.mersadai.app.domain.search.SimilarContent
import com.mersadai.app.domain.social.SocialPostFormatter
import com.mersadai.app.feature.explore.ExploreViewModel
import com.mersadai.app.feature.explore.HomeSection
import com.mersadai.app.feature.explore.HomeSectionContent
import com.mersadai.app.feature.explore.homeSections
import com.mersadai.app.feature.explore.isNewAt
import com.mersadai.app.feature.explore.sourceTimestamp
import com.mersadai.app.data.translation.TranslationFields
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(
    viewModel: ExploreViewModel,
    onOpenSearch: (HomeSection?) -> Unit,
    onOpenItem: (String) -> Unit,
    onManualSync: suspend () -> Unit,
    contentPadding: PaddingValues,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val syncRecord by viewModel.syncRecord.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sections = remember(items) { homeSections(items) }
    PullToRefreshBox(
        isRefreshing = syncRecord?.state == SyncState.SYNCING,
        onRefresh = { scope.launch { onManualSync() } },
        state = rememberPullToRefreshState(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenSearch(null) },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onOpenSearch(null) }),
                    readOnly = false,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.quick_categories), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sections.map(HomeSectionContent::section)) { section ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.clickable { onOpenSearch(section) },
                            ) {
                                Text(stringResource(section.stringResource()), modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.discover_now), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { onManualSync() } }, enabled = syncRecord?.state != SyncState.SYNCING) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    TextButton(onClick = { scope.launch { onManualSync() } }, enabled = syncRecord?.state != SyncState.SYNCING) { Text(stringResource(R.string.refresh)) }
                }
            }
            if (!isOnline) item { StatusBanner(stringResource(R.string.offline_message)) }
            when (syncRecord?.state) {
                SyncState.SYNCING -> item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.syncing), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                SyncState.FAILURE -> item { StatusBanner(syncRecord?.message ?: stringResource(R.string.sync_failed), isError = true) }
                SyncState.NOT_CONFIGURED -> item { StatusBanner(stringResource(R.string.sync_not_configured)) }
                SyncState.SUCCESS -> syncRecord?.message?.let { message -> item { StatusBanner(message) } }
                else -> Unit
            }
            item {
                Text(
                    syncRecord?.lastSuccessAt?.let { timestamp ->
                        val formatted = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("ar")).format(Date(timestamp))
                        stringResource(R.string.last_updated, formatted)
                    } ?: stringResource(R.string.not_updated_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(stringResource(R.string.local_cache_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (sections.isEmpty()) {
                item { EmptyState(R.string.empty_home_title, R.string.empty_home_message) }
            } else {
                sections.forEach { section ->
                    item(key = "section-${section.section.name}") {
                        HomeSectionHeading(section, onShowAll = { onOpenSearch(section.section) })
                    }
                    items(section.items, key = { "${section.section.name}:${it.id}" }) { content ->
                        ContentCard(item = content, viewModel = viewModel, onClick = { onOpenItem(content.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeading(section: HomeSectionContent, onShowAll: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(section.section.stringResource()),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShowAll) { Text(stringResource(R.string.show_all)) }
    }
}

@Composable
fun SearchScreen(viewModel: ExploreViewModel, onOpenItem: (String) -> Unit, contentPadding: PaddingValues) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val selectedSection by viewModel.searchSection.collectAsStateWithLifecycle()
    val selectedSource by viewModel.searchSource.collectAsStateWithLifecycle()
    val content by viewModel.items.collectAsStateWithLifecycle()
    val sources = remember(content) { content.mapNotNull { it.source }.distinctBy { it.id }.sortedBy { it.name } }
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text(stringResource(R.string.nav_search), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedSection == null,
                        onClick = { viewModel.selectSearchSection(null) },
                        label = { Text(stringResource(R.string.filter_all)) },
                    )
                }
                items(HomeSection.entries) { section ->
                    FilterChip(
                        selected = selectedSection == section,
                        onClick = { viewModel.selectSearchSection(section) },
                        label = { Text(stringResource(section.stringResource())) },
                    )
                }
                item {
                    Box {
                        FilterChip(
                            selected = selectedSource != null,
                            onClick = { sourceMenuExpanded = true },
                            label = {
                                Text(
                                    selectedSource?.let { id -> sources.firstOrNull { it.id == id }?.name }
                                        ?: stringResource(R.string.filter_source),
                                )
                            },
                        )
                        DropdownMenu(expanded = sourceMenuExpanded, onDismissRequest = { sourceMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.filter_all_sources)) },
                                onClick = { viewModel.selectSearchSource(null); sourceMenuExpanded = false },
                            )
                            sources.forEach { source ->
                                DropdownMenuItem(
                                    text = { Text(source.name) },
                                    onClick = { viewModel.selectSearchSource(source.id); sourceMenuExpanded = false },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (query.isBlank() && selectedSection == null) {
            item { EmptyState(R.string.search_start_message, R.string.empty_home_message) }
        } else if (results.isEmpty()) {
            item { EmptyState(R.string.empty_search_title, R.string.empty_search_message) }
        } else {
            items(results, key = ContentItem::id) { item ->
                ContentCard(item = item, viewModel = viewModel, onClick = { onOpenItem(item.id) })
            }
        }
    }
}

@Composable
fun FavoritesScreen(viewModel: ExploreViewModel, onOpenItem: (String) -> Unit, contentPadding: PaddingValues) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text(stringResource(R.string.nav_favorites), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (favorites.isEmpty()) {
            item { EmptyState(R.string.empty_favorites_title, R.string.empty_favorites_message) }
        } else {
            items(favorites, key = ContentItem::id) { item ->
                ContentCard(item = item, viewModel = viewModel, onClick = { onOpenItem(item.id) })
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    settingsRepository: SettingsRepository,
    onClearCache: () -> Unit,
    contentPadding: PaddingValues,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showClearConfirmation by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf<Int?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        scope.launch {
            settingsRepository.setNotificationsEnabled(granted)
        }
        if (!granted) dialogMessage = R.string.notification_permission_denied
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        ThemeMode.entries.forEach { mode ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { scope.launch { settingsRepository.setThemeMode(mode) } }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.themeMode == mode, onClick = { scope.launch { settingsRepository.setThemeMode(mode) } })
                    Text(stringResource(mode.stringResource()), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item { HorizontalDivider() }
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(R.string.notifications), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.notifications_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            scope.launch { settingsRepository.setNotificationsEnabled(false) }
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            scope.launch { settingsRepository.setNotificationPermissionRequested() }
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            scope.launch { settingsRepository.setNotificationsEnabled(true) }
                        }
                    },
                )
            }
        }
        item { HorizontalDivider() }
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(R.string.auto_update), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.auto_update_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.autoUpdate, onCheckedChange = { enabled -> scope.launch { settingsRepository.setAutoUpdate(enabled) } })
            }
        }
        item { HorizontalDivider() }
        item {
            Column(modifier = Modifier.fillMaxWidth().clickable { showClearConfirmation = true }.padding(vertical = 8.dp)) {
                Text(stringResource(R.string.clear_cache), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.clear_cache_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { HorizontalDivider() }
        item { TextButton(onClick = { dialogMessage = R.string.about_message }) { Text(stringResource(R.string.about_app)) } }
        item { TextButton(onClick = { dialogMessage = R.string.privacy_message }) { Text(stringResource(R.string.privacy)) } }
    }
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.clear_cache_confirm_title)) },
            text = { Text(stringResource(R.string.clear_cache_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { onClearCache(); showClearConfirmation = false }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    dialogMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { dialogMessage = null },
            text = { Text(stringResource(message)) },
            confirmButton = { TextButton(onClick = { dialogMessage = null }) { Text(stringResource(R.string.dismiss)) } },
        )
    }
}

@Composable
fun DetailsScreen(itemId: String, viewModel: ExploreViewModel, onOpenItem: (String) -> Unit, contentPadding: PaddingValues) {
    val item by viewModel.observeItem(itemId).collectAsStateWithLifecycle(initialValue = null)
    val availableItems by viewModel.items.collectAsStateWithLifecycle()
    val isFavorite by viewModel.observeFavorite(itemId).collectAsStateWithLifecycle(initialValue = false)
    val translating by viewModel.translatingFields.collectAsStateWithLifecycle()
    val failedTranslations by viewModel.failedTranslations.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text(stringResource(R.string.details_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (item == null) {
            item { EmptyState(R.string.item_not_found, R.string.offline_message) }
        } else {
            val content = item!!
            val similarItems = SimilarContent.find(content, availableItems)
            val canTranslateTitle = content.contentType == ContentType.NEWS ||
                content.contentType == ContentType.PROMPT ||
                (content.contentType == ContentType.AI_TOOL && content.title != content.originalTitle)
            val sourceDescription = content.description ?: content.originalDescription
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(content.displayTitleAr ?: content.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    content.originalTitle?.takeIf { it != (content.displayTitleAr ?: content.title) }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrLtr), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (canTranslateTitle && content.displayTitleAr == null) {
                item {
                    TranslationButton(
                        fieldKey = "${content.id}:${TranslationFields.TITLE}",
                        translating = translating,
                        failed = failedTranslations,
                        onTranslate = { viewModel.translateToArabic(content.id, TranslationFields.TITLE, content.title) },
                    )
                }
            }
            item {
                DetailValue(
                    if (content.displayDescriptionAr != null) R.string.translated_description else R.string.description,
                    content.displayDescriptionAr ?: sourceDescription ?: stringResource(R.string.no_description),
                )
            }
            if (content.contentType == ContentType.PROMPT) {
                content.originalDescription?.let { original -> item { DetailValue(R.string.original_description, original) } }
            } else {
                content.originalDescription?.takeIf { it != content.description && '<' !in it }?.let { original ->
                    item { DetailValue(R.string.original_description, original) }
                }
            }
            sourceDescription?.takeIf { it.isNotBlank() && content.displayDescriptionAr == null }?.let { description ->
                item {
                    TranslationButton(
                        fieldKey = "${content.id}:${TranslationFields.DESCRIPTION}",
                        translating = translating,
                        failed = failedTranslations,
                        onTranslate = { viewModel.translateToArabic(content.id, TranslationFields.DESCRIPTION, description) },
                    )
                }
            }
            content.publishedAt?.let { published ->
                item { DetailValue(R.string.published_date, formatDate(published)) }
            }
            content.sourceUpdatedAt?.let { updated ->
                item { DetailValue(R.string.source_updated, formatDate(updated)) }
            }
            item { DetailValue(R.string.content_type, stringResource(content.contentType.stringResource())) }
            content.category?.let { category -> item { DetailValue(R.string.category, category.displayName()) } }
            content.language?.let { item { DetailValue(R.string.language, it, forceLtr = true) } }
            content.starsCount?.let { item { DetailValue(R.string.stars, it.toString(), forceLtr = true) } }
            content.forksCount?.let { item { DetailValue(R.string.forks, it.toString(), forceLtr = true) } }
            content.openIssuesCount?.let { item { DetailValue(R.string.open_issues, it.toString(), forceLtr = true) } }
            content.pipelineTag?.let {
                item { DetailValue(R.string.pipeline, content.pipelineCategory ?: stringResource(R.string.section_models)) }
            }
            content.pipelineCategory?.let { item { DetailValue(R.string.pipeline_category, it) } }
            content.libraryName?.let { item { DetailValue(R.string.model_library, it, forceLtr = true) } }
            content.downloads?.let { item { DetailValue(R.string.downloads, it.toString(), forceLtr = true) } }
            content.likes?.let { item { DetailValue(R.string.likes, it.toString(), forceLtr = true) } }
            content.trendingScore?.let { item { DetailValue(R.string.trending_score, it.toString(), forceLtr = true) } }
            if (content.contentType == ContentType.ANDROID_PROJECT || content.contentType == ContentType.MODEL) {
                item { DetailValue(R.string.license, content.license ?: stringResource(R.string.license_unknown), forceLtr = true) }
            }
            content.sdk?.let { item { DetailValue(R.string.sdk, it, forceLtr = true) } }
            content.tags.takeIf { it.isNotEmpty() }?.let { tags ->
                val labels = tags.mapNotNull(String::functionalTagAr).distinct()
                    .ifEmpty { listOfNotNull(content.pipelineCategory) }
                if (labels.isNotEmpty()) item { DetailValue(R.string.tags, labels.joinToString("، ")) }
            }
            content.author?.let { item { DetailValue(R.string.author, it, forceLtr = true) } }
            content.contributor?.let { item { DetailValue(R.string.contributor, it) } }
            item {
                val freeStatus = content.classifiedFreeStatus()
                DetailValue(
                    R.string.free_status,
                    stringResource(if (freeStatus == FreeStatus.OPEN_SOURCE) R.string.free_open_source_free else freeStatus.stringResource()),
                )
            }
            item { DetailValue(R.string.verification, stringResource(content.verificationLevel.stringResource())) }
            item { DetailValue(R.string.source, content.source?.name ?: stringResource(R.string.source_not_available), forceLtr = content.source != null) }
            content.lastVerifiedAt?.let { verified ->
                item {
                    DetailValue(
                        R.string.last_verified,
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("ar")).format(Date(verified)),
                    )
                }
            }
            content.url?.let { sourceUrl ->
                item { UrlValue(sourceUrl) }
                item {
                    OutlinedButton(onClick = {
                        val uri = Uri.parse(sourceUrl)
                        if (uri.scheme == "https") runCatching { CustomTabsIntent.Builder().build().launchUrl(context, uri) }
                    }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.open_source))
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { viewModel.setFavorite(content.id, !isFavorite) }) {
                        Icon(if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkAdd, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(if (isFavorite) R.string.remove_favorite else R.string.save_favorite))
                    }
                    Button(onClick = {
                        val shareText = listOfNotNull(content.title, content.description ?: content.originalDescription, content.url).joinToString("\n")
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_chooser)))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.share))
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val promptText = content.originalDescription?.takeIf { it.isNotBlank() }
                            ?: content.description?.takeIf { it.isNotBlank() }
                            ?: content.displayDescriptionAr
                        clipboard.setText(AnnotatedString(SocialPostFormatter.format(content, promptText)))
                        Toast.makeText(context, context.getString(R.string.post_body_copied), Toast.LENGTH_SHORT).show()
                    }) {
                        Text(stringResource(R.string.copy_post_body))
                    }
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(SocialPostFormatter.formatFirstComment(content)))
                        Toast.makeText(context, context.getString(R.string.first_comment_copied), Toast.LENGTH_SHORT).show()
                    }) {
                        Text(stringResource(R.string.copy_first_comment))
                    }
                }
            }
            if (content.contentType == ContentType.PROMPT) {
                val promptText = content.originalDescription?.takeIf { it.isNotBlank() }
                    ?: content.description?.takeIf { it.isNotBlank() }
                    ?: content.displayDescriptionAr?.takeIf { it.isNotBlank() }
                if (promptText != null) item {
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(promptText))
                        Toast.makeText(context, context.getString(R.string.prompt_copied), Toast.LENGTH_SHORT).show()
                    }) {
                        Text(stringResource(R.string.copy_prompt))
                    }
                }
            }
            if (similarItems.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.similar_content), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                items(similarItems, key = { "similar:${it.id}" }) { similar ->
                    ContentCard(item = similar, viewModel = viewModel, onClick = { onOpenItem(similar.id) })
                }
            }
        }
    }
}

@Composable
private fun ContentCard(item: ContentItem, viewModel: ExploreViewModel, onClick: () -> Unit) {
    val isFavorite by viewModel.observeFavorite(item.id).collectAsStateWithLifecycle(initialValue = false)
    LaunchedEffect(item.id, item.title, item.description, item.displayTitleAr, item.displayDescriptionAr) {
        if (item.displayTitleAr == null) {
            item.translatableTitle()?.let { title ->
                viewModel.translateToArabic(item.id, TranslationFields.TITLE, title)
            }
        }
        if (item.contentType != ContentType.PROMPT && item.displayDescriptionAr == null) {
            (item.description ?: item.originalDescription)
                ?.takeIf { it.isNotBlank() && !it.containsArabic() }
                ?.let { description -> viewModel.translateToArabic(item.id, TranslationFields.DESCRIPTION, description) }
        }
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            val safeThumbnail = item.thumbnailUrl?.takeIf { Uri.parse(it).scheme == "https" }
            if (safeThumbnail != null) {
                AsyncImage(
                    model = safeThumbnail,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(item.emoji ?: item.title.firstOrNull()?.toString().orEmpty(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        item.displayTitleAr ?: item.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.isNewAt(System.currentTimeMillis())) {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
                            Text(stringResource(R.string.badge_new), modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                (item.displayDescriptionAr ?: item.description)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    listOfNotNull(item.source?.name, stringResource(item.contentType.stringResource()), item.freeStatus.takeIf { it != FreeStatus.UNKNOWN }?.let { stringResource(it.stringResource()) }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.sourceTimestamp()?.let { timestamp ->
                    Text(formatDate(timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { viewModel.setFavorite(item.id, !isFavorite) }) {
                Icon(if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkAdd, contentDescription = stringResource(if (isFavorite) R.string.remove_favorite else R.string.save_favorite))
            }
        }
    }
}

@Composable
private fun TranslationButton(fieldKey: String, translating: Set<String>, failed: Set<String>, onTranslate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = onTranslate, enabled = fieldKey !in translating) {
            if (fieldKey in translating) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.translation_in_progress))
            } else {
                Text(stringResource(R.string.translate_to_arabic))
            }
        }
        if (fieldKey in failed) Text(stringResource(R.string.translation_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

private fun ContentItem.translatableTitle(): String? = when {
    contentType == ContentType.NEWS || contentType == ContentType.PROMPT -> title
    contentType == ContentType.AI_TOOL && title != originalTitle -> title
    else -> null
}?.takeIf { it.isNotBlank() && !it.containsArabic() }

private fun String.containsArabic(): Boolean = any { character -> character.code in 0x0600..0x06FF }

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("ar")).format(Date(timestamp))

@Composable
private fun EmptyState(title: Int, message: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusBanner(message: String, isError: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DetailValue(label: Int, value: String, forceLtr: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (forceLtr) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Text(value, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr))
        }
    }
}

@Composable
private fun UrlValue(url: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.source), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun ThemeMode.stringResource(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun ContentType.stringResource(): Int = when (this) {
    ContentType.AI_TOOL -> R.string.type_ai_tool
    ContentType.ANDROID_PROJECT -> R.string.type_android_project
    ContentType.MODEL -> R.string.type_model
    ContentType.PROMPT -> R.string.type_prompt
    ContentType.NEWS -> R.string.type_news
    ContentType.OTHER -> R.string.type_other
}

private fun HomeSection.stringResource(): Int = when (this) {
    HomeSection.LATEST -> R.string.section_latest
    HomeSection.AI_TOOLS -> R.string.section_ai_tools
    HomeSection.ANDROID_PROJECTS -> R.string.section_android_projects
    HomeSection.ANDROID_MEDIA_DESIGN -> R.string.section_android_media_design
    HomeSection.MODELS -> R.string.section_models
    HomeSection.PROMPTS -> R.string.section_prompts
    HomeSection.IMAGE_PROMPTS -> R.string.section_image_prompts
    HomeSection.REELS_PROMPTS -> R.string.section_reels_prompts
    HomeSection.CHARACTER_PROMPTS -> R.string.section_character_prompts
    HomeSection.PHOTOREALISTIC_PROMPTS -> R.string.section_photorealistic_prompts
    HomeSection.VISUAL_TRICKS -> R.string.section_visual_tricks
    HomeSection.SOCIAL_PORTRAITS -> R.string.section_social_portraits
    HomeSection.FREE_PERKS -> R.string.section_free_perks
    HomeSection.AI_NEWS -> R.string.section_ai_news
    HomeSection.DEVELOPER_TOOLS -> R.string.section_developer_tools
}

@Composable
private fun Category.displayName(): String = when (id) {
    "ai-tools" -> stringResource(R.string.section_ai_tools)
    "android" -> stringResource(R.string.section_android_projects)
    "android-media-design" -> stringResource(R.string.section_android_media_design)
    "free-perks" -> stringResource(R.string.section_free_perks)
    "models" -> stringResource(R.string.section_models)
    "prompts" -> stringResource(R.string.section_prompts)
    "reels-prompts" -> stringResource(R.string.section_reels_prompts)
    "character-prompts" -> stringResource(R.string.section_character_prompts)
    "photorealistic-prompts" -> stringResource(R.string.section_photorealistic_prompts)
    "visual-tricks" -> stringResource(R.string.section_visual_tricks)
    "social-portraits" -> stringResource(R.string.section_social_portraits)
    "ai-news" -> stringResource(R.string.section_ai_news)
    "android-news" -> stringResource(R.string.category_android_news)
    "developer-tools" -> stringResource(R.string.section_developer_tools)
    else -> name
}

private fun String.functionalTagAr(): String? {
    val value = lowercase(Locale.ROOT)
    return when {
        value.startsWith("license:") || value in setOf("safetensors", "arxiv", "transformers", "diffusers") -> null
        "ocr" in value || "document" in value -> "قراءة مستندات OCR"
        "text-to-image" in value || "image-generation" in value -> "توليد صور"
        "image-to-image" in value -> "تعديل صور"
        "computer-vision" in value || "image-classification" in value || "object-detection" in value -> "رؤية حاسوبية"
        "speech" in value || "audio" in value -> "معالجة الصوت والكلام"
        "translation" in value -> "ترجمة النصوص"
        "text-generation" in value -> "توليد النصوص"
        else -> null
    }
}

private fun FreeStatus.stringResource(): Int = when (this) {
    FreeStatus.FULLY_FREE -> R.string.free_fully
    FreeStatus.FREE_TIER -> R.string.free_tier
    FreeStatus.FREE_CREDIT -> R.string.free_credit
    FreeStatus.TEMPORARY_OFFER -> R.string.temporary_offer
    FreeStatus.OPEN_SOURCE -> R.string.free_open_source
    FreeStatus.OPEN_WEIGHT -> R.string.free_open_weight
    FreeStatus.LOCAL_RUNNABLE -> R.string.free_local_runnable
    FreeStatus.EXPIRED -> R.string.expired_offer
    FreeStatus.UNKNOWN -> R.string.free_unknown
}

private fun VerificationLevel.stringResource(): Int = when (this) {
    VerificationLevel.OFFICIAL, VerificationLevel.OFFICIAL_SOURCE -> R.string.verification_official_source
    VerificationLevel.COMMUNITY_SOURCE -> R.string.verification_community
    VerificationLevel.UNVERIFIED -> R.string.verification_unverified
}
