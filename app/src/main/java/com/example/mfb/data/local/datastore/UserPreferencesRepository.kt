package com.example.mfb.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mfb.core.model.ThemeMode
import com.example.mfb.domain.model.EditorSessionSnapshot
import com.example.mfb.domain.model.EditorSettings
import com.example.mfb.domain.model.EditorThemePreset
import com.example.mfb.domain.model.PreviewHistoryEntry
import com.example.mfb.domain.model.PreviewSettings
import com.example.mfb.domain.model.ProjectFilterOption
import com.example.mfb.domain.model.ProjectSortOption
import com.example.mfb.domain.model.ViewportPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mfb_user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("mfb_theme_mode")
        val APP_LANGUAGE = stringPreferencesKey("mfb_app_language")
        val LAST_VIEWED_VERSION_CODE = intPreferencesKey("mfb_last_viewed_version_code")
        val PROJECT_SORT_OPTION = stringPreferencesKey("mfb_project_sort_option")
        val PROJECT_FILTER_OPTION = stringPreferencesKey("mfb_project_filter_option")
        val EDITOR_FONT_SIZE = intPreferencesKey("mfb_editor_font_size")
        val EDITOR_THEME = stringPreferencesKey("mfb_editor_theme")
        val EDITOR_LINE_NUMBERS = booleanPreferencesKey("mfb_editor_line_numbers")
        val EDITOR_WORD_WRAP = booleanPreferencesKey("mfb_editor_word_wrap")
        val EDITOR_AUTO_SAVE = booleanPreferencesKey("mfb_editor_auto_save")
        val EDITOR_INDENT_WIDTH = intPreferencesKey("mfb_editor_indent_width")
        val EDITOR_USE_TABS = booleanPreferencesKey("mfb_editor_use_tabs")
        val EDITOR_SYNTAX_HIGHLIGHTING = booleanPreferencesKey("mfb_editor_syntax_highlighting")
        val PREVIEW_JS_ENABLED = booleanPreferencesKey("mfb_preview_js_enabled")
        val PREVIEW_EXTERNAL_NETWORK = booleanPreferencesKey("mfb_preview_external_network")
        val PREVIEW_DESKTOP_VIEWPORT = booleanPreferencesKey("mfb_preview_desktop_viewport")
        val PREVIEW_DEFAULT_VIEWPORT = stringPreferencesKey("mfb_preview_default_viewport")
        val PREVIEW_ZOOM_CONTROLS = booleanPreferencesKey("mfb_preview_zoom_controls")
        val PREVIEW_LAST_HISTORY = stringPreferencesKey("mfb_preview_last_history")
        val BUILD_SERVER_URL = stringPreferencesKey("mfb_build_server_url")
        fun editorSessionKey(projectId: Long) = stringPreferencesKey("mfb_editor_session_$projectId")
        fun previewProjectConfigKey(projectId: Long) = stringPreferencesKey("mfb_preview_project_config_$projectId")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            when (preferences[PreferencesKeys.THEME_MODE]) {
                ThemeMode.LIGHT.name -> ThemeMode.LIGHT
                ThemeMode.DARK.name -> ThemeMode.DARK
                ThemeMode.SYSTEM.name -> ThemeMode.SYSTEM
                else -> ThemeMode.DARK
            }
        }

    val appLanguageFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.APP_LANGUAGE] ?: "en"
        }

    val lastViewedVersionCodeFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.LAST_VIEWED_VERSION_CODE] ?: 0
        }

    val projectSortOptionFlow: Flow<ProjectSortOption> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val raw = preferences[PreferencesKeys.PROJECT_SORT_OPTION]
            try {
                if (raw != null) ProjectSortOption.valueOf(raw) else ProjectSortOption.RECENTLY_MODIFIED
            } catch (_: Exception) {
                ProjectSortOption.RECENTLY_MODIFIED
            }
        }

    val projectFilterOptionFlow: Flow<ProjectFilterOption> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val raw = preferences[PreferencesKeys.PROJECT_FILTER_OPTION]
            try {
                if (raw != null) ProjectFilterOption.valueOf(raw) else ProjectFilterOption.ALL
            } catch (_: Exception) {
                ProjectFilterOption.ALL
            }
        }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun setAppLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LANGUAGE] = languageCode
        }
    }

    suspend fun setLastViewedVersionCode(versionCode: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_VIEWED_VERSION_CODE] = versionCode
        }
    }

    suspend fun setProjectSortOption(option: ProjectSortOption) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROJECT_SORT_OPTION] = option.name
        }
    }

    val editorSettingsFlow: Flow<EditorSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeRaw = preferences[PreferencesKeys.EDITOR_THEME]
            val themePreset = try {
                if (themeRaw != null) EditorThemePreset.valueOf(themeRaw) else EditorThemePreset.MATCH_APP
            } catch (_: Exception) {
                EditorThemePreset.MATCH_APP
            }
            EditorSettings(
                fontSizeSp = (preferences[PreferencesKeys.EDITOR_FONT_SIZE] ?: 14).coerceIn(11, 24),
                editorTheme = themePreset,
                showLineNumbers = preferences[PreferencesKeys.EDITOR_LINE_NUMBERS] ?: true,
                wordWrap = preferences[PreferencesKeys.EDITOR_WORD_WRAP] ?: false,
                autoSaveEnabled = preferences[PreferencesKeys.EDITOR_AUTO_SAVE] ?: false,
                indentWidth = (preferences[PreferencesKeys.EDITOR_INDENT_WIDTH] ?: 2).coerceIn(2, 8),
                useTabs = preferences[PreferencesKeys.EDITOR_USE_TABS] ?: false,
                syntaxHighlightingEnabled = preferences[PreferencesKeys.EDITOR_SYNTAX_HIGHLIGHTING] ?: true
            )
        }

    suspend fun setProjectFilterOption(option: ProjectFilterOption) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROJECT_FILTER_OPTION] = option.name
        }
    }

    suspend fun updateEditorSettings(transform: (EditorSettings) -> EditorSettings) {
        val current = editorSettingsFlow.first()
        val updated = transform(current)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EDITOR_FONT_SIZE] = updated.fontSizeSp.coerceIn(11, 24)
            preferences[PreferencesKeys.EDITOR_THEME] = updated.editorTheme.name
            preferences[PreferencesKeys.EDITOR_LINE_NUMBERS] = updated.showLineNumbers
            preferences[PreferencesKeys.EDITOR_WORD_WRAP] = updated.wordWrap
            preferences[PreferencesKeys.EDITOR_AUTO_SAVE] = updated.autoSaveEnabled
            preferences[PreferencesKeys.EDITOR_INDENT_WIDTH] = updated.indentWidth.coerceIn(2, 8)
            preferences[PreferencesKeys.EDITOR_USE_TABS] = updated.useTabs
            preferences[PreferencesKeys.EDITOR_SYNTAX_HIGHLIGHTING] = updated.syntaxHighlightingEnabled
        }
    }

    suspend fun saveEditorSession(snapshot: EditorSessionSnapshot) {
        context.dataStore.edit { preferences ->
            val key = PreferencesKeys.editorSessionKey(snapshot.projectId)
            if (snapshot.openTabPaths.isEmpty()) {
                preferences.remove(key)
            } else {
                val json = JSONObject().apply {
                    put("projectId", snapshot.projectId)
                    put("activeTabPath", snapshot.activeTabPath ?: JSONObject.NULL)
                    put("openTabPaths", JSONArray(snapshot.openTabPaths))
                }
                preferences[key] = json.toString()
            }
        }
    }

    suspend fun getEditorSession(projectId: Long): EditorSessionSnapshot? {
        val prefs = context.dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .first()
        val raw = prefs[PreferencesKeys.editorSessionKey(projectId)] ?: return null
        return try {
            val json = JSONObject(raw)
            val arr = json.optJSONArray("openTabPaths") ?: JSONArray()
            val paths = buildList {
                for (i in 0 until arr.length()) {
                    val p = arr.optString(i, "")
                    if (p.isNotBlank()) add(p)
                }
            }
            val active = if (json.isNull("activeTabPath")) null else json.optString("activeTabPath", "").ifBlank { null }
            EditorSessionSnapshot(
                projectId = projectId,
                openTabPaths = paths,
                activeTabPath = active
            )
        } catch (_: Exception) {
            null
        }
    }

    val previewSettingsFlow: Flow<PreviewSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val rawPreset = preferences[PreferencesKeys.PREVIEW_DEFAULT_VIEWPORT]
            val preset = try {
                if (rawPreset != null) ViewportPreset.valueOf(rawPreset) else ViewportPreset.RESPONSIVE
            } catch (_: Exception) {
                ViewportPreset.RESPONSIVE
            }
            PreviewSettings(
                javaScriptEnabled = preferences[PreferencesKeys.PREVIEW_JS_ENABLED] ?: true,
                externalNetworkEnabled = preferences[PreferencesKeys.PREVIEW_EXTERNAL_NETWORK] ?: false,
                desktopViewportMode = preferences[PreferencesKeys.PREVIEW_DESKTOP_VIEWPORT] ?: false,
                defaultViewportPreset = preset,
                zoomControlsEnabled = preferences[PreferencesKeys.PREVIEW_ZOOM_CONTROLS] ?: true
            )
        }

    suspend fun updatePreviewSettings(transform: (PreviewSettings) -> PreviewSettings) {
        val current = previewSettingsFlow.first()
        val updated = transform(current)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PREVIEW_JS_ENABLED] = updated.javaScriptEnabled
            preferences[PreferencesKeys.PREVIEW_EXTERNAL_NETWORK] = updated.externalNetworkEnabled
            preferences[PreferencesKeys.PREVIEW_DESKTOP_VIEWPORT] = updated.desktopViewportMode
            preferences[PreferencesKeys.PREVIEW_DEFAULT_VIEWPORT] = updated.defaultViewportPreset.name
            preferences[PreferencesKeys.PREVIEW_ZOOM_CONTROLS] = updated.zoomControlsEnabled
        }
    }

    suspend fun savePreviewHistory(entry: PreviewHistoryEntry) {
        context.dataStore.edit { preferences ->
            val json = JSONObject().apply {
                put("projectId", entry.projectId)
                put("entryPointRelativePath", entry.entryPointRelativePath)
                put("viewportPreset", entry.viewportPreset.name)
                put("zoomPercent", entry.zoomPercent.coerceIn(50, 200))
                put("lastPreviewedAt", entry.lastPreviewedAt)
            }
            val raw = json.toString()
            preferences[PreferencesKeys.PREVIEW_LAST_HISTORY] = raw
            preferences[PreferencesKeys.previewProjectConfigKey(entry.projectId)] = raw
        }
    }

    suspend fun clearProjectPreviewConfig(projectId: Long) {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.previewProjectConfigKey(projectId))
        }
    }

    suspend fun getProjectPreviewConfig(projectId: Long): PreviewHistoryEntry? {
        val prefs = context.dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .first()
        val raw = prefs[PreferencesKeys.previewProjectConfigKey(projectId)] ?: return null
        return parsePreviewHistoryJson(raw)
    }

    suspend fun getLastPreviewHistory(): PreviewHistoryEntry? {
        val prefs = context.dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .first()
        val raw = prefs[PreferencesKeys.PREVIEW_LAST_HISTORY] ?: return null
        return parsePreviewHistoryJson(raw)
    }

    private fun parsePreviewHistoryJson(raw: String): PreviewHistoryEntry? {
        return try {
            val json = JSONObject(raw)
            val preset = try {
                ViewportPreset.valueOf(json.optString("viewportPreset", ViewportPreset.RESPONSIVE.name))
            } catch (_: Exception) {
                ViewportPreset.RESPONSIVE
            }
            PreviewHistoryEntry(
                projectId = json.getLong("projectId"),
                entryPointRelativePath = json.optString("entryPointRelativePath", "index.html"),
                viewportPreset = preset,
                zoomPercent = json.optInt("zoomPercent", 100).coerceIn(50, 200),
                lastPreviewedAt = json.optLong("lastPreviewedAt", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }

    val buildServerUrlFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.BUILD_SERVER_URL] ?: ""
        }

    suspend fun setBuildServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BUILD_SERVER_URL] = url.trim()
        }
    }
}
