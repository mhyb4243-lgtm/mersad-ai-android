package com.mersadai.app.feature.explore.screens

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mersadai.app.R
import com.mersadai.app.data.local.SettingsRepository
import com.mersadai.app.domain.model.AppSettings
import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.FreeStatus
import com.mersadai.app.domain.model.SyncState
import com.mersadai.app.domain.model.ThemeMode
import com.mersadai.app.domain.model.VerificationLevel
import com.mersadai.app.feature.explore.ExploreViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: ExploreViewModel,
    onOpenSearch: () -> Unit,
    onOpenItem: (String) -> Unit,
    onManualSync: () -> Unit,
    contentPadding: PaddingValues,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val syncRecord by viewModel.syncRecord.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
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
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSearch),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onOpenSearch() }),
                readOnly = false,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.quick_categories), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        listOf(
                            R.string.category_latest,
                            R.string.category_ai,
                            R.string.category_free,
                            R.string.category_android,
                            R.string.category_models,
                            R.string.category_prompts,
                        ),
                    ) { category ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.clickable(onClick = onOpenSearch),
                        ) {
                            Text(stringResource(category), modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.discover_now), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onManualSync) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                }
                TextButton(onClick = onManualSync) { Text(stringResource(R.string.refresh)) }
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
            SyncState.FAILURE -> item { StatusBanner(stringResource(R.string.sync_failed), isError = true) }
            SyncState.NOT_CONFIGURED -> item { StatusBanner(stringResource(R.string.sync_not_configured)) }
            else -> Unit
        }
        item {
            Text(
                syncRecord?.lastFinishedAt?.let { timestamp ->
                    val formatted = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("ar")).format(Date(timestamp))
                    stringResource(R.string.last_updated, formatted)
                } ?: stringResource(R.string.not_updated_yet),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Text(stringResource(R.string.latest_content), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (items.isEmpty()) {
            item { EmptyState(R.string.empty_home_title, R.string.empty_home_message) }
        } else {
            items(items, key = ContentItem::id) { item ->
                ContentCard(item = item, viewModel = viewModel, onClick = { onOpenItem(item.id) })
            }
        }
    }
}

@Composable
fun SearchScreen(viewModel: ExploreViewModel, onOpenItem: (String) -> Unit, contentPadding: PaddingValues) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
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
        if (query.isBlank()) {
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
    var showClearConfirmation by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf<Int?>(null) }
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
fun DetailsScreen(itemId: String, viewModel: ExploreViewModel, contentPadding: PaddingValues) {
    val item by viewModel.observeItem(itemId).collectAsStateWithLifecycle(initialValue = null)
    val isFavorite by viewModel.observeFavorite(itemId).collectAsStateWithLifecycle(initialValue = false)
    val context = LocalContext.current
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
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(content.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    content.originalTitle?.takeIf { it != content.title }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrLtr), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { DetailValue(R.string.description, content.description ?: content.originalDescription ?: stringResource(R.string.no_description)) }
            content.originalDescription?.takeIf { it != content.description }?.let { original ->
                item { DetailValue(R.string.original_description, original) }
            }
            item { DetailValue(R.string.content_type, stringResource(content.contentType.stringResource())) }
            content.category?.let { category -> item { DetailValue(R.string.category, category.name) } }
            item { DetailValue(R.string.free_status, stringResource(content.freeStatus.stringResource())) }
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
        }
    }
}

@Composable
private fun ContentCard(item: ContentItem, viewModel: ExploreViewModel, onClick: () -> Unit) {
    val isFavorite by viewModel.observeFavorite(item.id).collectAsStateWithLifecycle(initialValue = false)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Text(stringResource(item.freeStatus.stringResource()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { viewModel.setFavorite(item.id, !isFavorite) }) {
                Icon(if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkAdd, contentDescription = stringResource(if (isFavorite) R.string.remove_favorite else R.string.save_favorite))
            }
        }
    }
}

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

private fun FreeStatus.stringResource(): Int = when (this) {
    FreeStatus.FULLY_FREE -> R.string.free_fully
    FreeStatus.FREE_TIER -> R.string.free_tier
    FreeStatus.FREE_CREDIT -> R.string.free_credit
    FreeStatus.TEMPORARY_OFFER -> R.string.temporary_offer
    FreeStatus.EXPIRED -> R.string.expired_offer
    FreeStatus.UNKNOWN -> R.string.free_unknown
}

private fun VerificationLevel.stringResource(): Int = when (this) {
    VerificationLevel.OFFICIAL -> R.string.verification_official
    VerificationLevel.COMMUNITY_SOURCE -> R.string.verification_community
    VerificationLevel.UNVERIFIED -> R.string.verification_unverified
}
