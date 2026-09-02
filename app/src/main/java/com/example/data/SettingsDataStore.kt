package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "telokuh_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val WORD_WRAP_KEY = booleanPreferencesKey("editor_word_wrap")
        val FONT_SIZE_KEY = intPreferencesKey("editor_font_size")
        val ROOT_MODE_KEY = booleanPreferencesKey("root_mode_enabled")
        val LEFT_PANEL_PATH_KEY = stringPreferencesKey("left_panel_path")
        val RIGHT_PANEL_PATH_KEY = stringPreferencesKey("right_panel_path")
        val ACTIVE_PANEL_KEY = stringPreferencesKey("active_panel")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }

    val wordWrapFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[WORD_WRAP_KEY] ?: false
    }

    val fontSizeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[FONT_SIZE_KEY] ?: 14
    }

    val rootModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ROOT_MODE_KEY] ?: false
    }

    val leftPanelPathFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LEFT_PANEL_PATH_KEY]
    }

    val rightPanelPathFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[RIGHT_PANEL_PATH_KEY]
    }

    val activePanelFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACTIVE_PANEL_KEY]
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY] ?: "System"
    }

    suspend fun saveWordWrap(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WORD_WRAP_KEY] = enabled
        }
    }

    suspend fun saveFontSize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[FONT_SIZE_KEY] = size
        }
    }

    suspend fun saveRootMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ROOT_MODE_KEY] = enabled
        }
    }

    suspend fun saveLeftPanelPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[LEFT_PANEL_PATH_KEY] = path
        }
    }

    suspend fun saveRightPanelPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[RIGHT_PANEL_PATH_KEY] = path
        }
    }

    suspend fun saveActivePanel(panel: String) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVE_PANEL_KEY] = panel
        }
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }
}
