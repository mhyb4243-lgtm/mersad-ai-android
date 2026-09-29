package com.mersadai.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mersadai.app.domain.model.AppSettings
import com.mersadai.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            themeMode = runCatching {
                ThemeMode.valueOf(preferences[THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            autoUpdate = preferences[AUTO_UPDATE] ?: false,
            notificationsEnabled = preferences[NOTIFICATIONS_ENABLED] ?: false,
            notifyAiTools = preferences[NOTIFY_AI_TOOLS] ?: true,
            notifyAndroidProjects = preferences[NOTIFY_ANDROID_PROJECTS] ?: true,
            notifyModels = preferences[NOTIFY_MODELS] ?: true,
            notifyPrompts = preferences[NOTIFY_PROMPTS] ?: true,
            notifyNews = preferences[NOTIFY_NEWS] ?: true,
            notificationPermissionRequested = preferences[NOTIFICATION_PERMISSION_REQUESTED] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setAutoUpdate(enabled: Boolean) {
        context.settingsDataStore.edit { it[AUTO_UPDATE] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setNotificationType(type: String, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            when (type) {
                "ai-tools" -> preferences[NOTIFY_AI_TOOLS] = enabled
                "android-projects" -> preferences[NOTIFY_ANDROID_PROJECTS] = enabled
                "models" -> preferences[NOTIFY_MODELS] = enabled
                "prompts" -> preferences[NOTIFY_PROMPTS] = enabled
                "news" -> preferences[NOTIFY_NEWS] = enabled
            }
        }
    }

    suspend fun setNotificationPermissionRequested() {
        context.settingsDataStore.edit { it[NOTIFICATION_PERMISSION_REQUESTED] = true }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFY_AI_TOOLS = booleanPreferencesKey("notify_ai_tools")
        val NOTIFY_ANDROID_PROJECTS = booleanPreferencesKey("notify_android_projects")
        val NOTIFY_MODELS = booleanPreferencesKey("notify_models")
        val NOTIFY_PROMPTS = booleanPreferencesKey("notify_prompts")
        val NOTIFY_NEWS = booleanPreferencesKey("notify_news")
        val NOTIFICATION_PERMISSION_REQUESTED = booleanPreferencesKey("notification_permission_requested")
    }
}
