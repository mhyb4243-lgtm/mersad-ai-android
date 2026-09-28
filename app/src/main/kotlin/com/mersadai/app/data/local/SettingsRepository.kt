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
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setAutoUpdate(enabled: Boolean) {
        context.settingsDataStore.edit { it[AUTO_UPDATE] = enabled }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
    }
}
