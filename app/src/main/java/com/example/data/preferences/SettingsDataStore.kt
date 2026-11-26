package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AccentPreset
import com.example.domain.model.AppSettings
import com.example.domain.model.GalleryViewMode
import com.example.domain.model.MediaSortOrder
import com.example.domain.model.ThemeMode
import com.example.domain.model.ThumbnailQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "mgallery_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val THUMBNAIL_QUALITY = stringPreferencesKey("thumbnail_quality")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val DEFAULT_SORT = stringPreferencesKey("default_sort")
        val VAULT_AUTO_LOCK_SECONDS = intPreferencesKey("vault_auto_lock_seconds")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val TRASH_RETENTION_DAYS = intPreferencesKey("trash_retention_days")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DARK,
            accentPreset = prefs[Keys.ACCENT_PRESET]?.let { runCatching { AccentPreset.valueOf(it) }.getOrNull() }
                ?: AccentPreset.VIOLET,
            gridColumns = (prefs[Keys.GRID_COLUMNS] ?: 3).coerceIn(2, 5),
            viewMode = prefs[Keys.VIEW_MODE]?.let { runCatching { GalleryViewMode.valueOf(it) }.getOrNull() }
                ?: GalleryViewMode.GRID,
            thumbnailQuality = prefs[Keys.THUMBNAIL_QUALITY]?.let { runCatching { ThumbnailQuality.valueOf(it) }.getOrNull() }
                ?: ThumbnailQuality.STANDARD,
            animationsEnabled = prefs[Keys.ANIMATIONS_ENABLED] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            defaultSort = prefs[Keys.DEFAULT_SORT]?.let { runCatching { MediaSortOrder.valueOf(it) }.getOrNull() }
                ?: MediaSortOrder.DATE_DESC,
            vaultAutoLockSeconds = prefs[Keys.VAULT_AUTO_LOCK_SECONDS] ?: 0,
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: true,
            trashRetentionDays = prefs[Keys.TRASH_RETENTION_DAYS] ?: 30
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentPreset(preset: AccentPreset) {
        context.settingsDataStore.edit { it[Keys.ACCENT_PRESET] = preset.name }
    }

    suspend fun setGridColumns(columns: Int) {
        context.settingsDataStore.edit { it[Keys.GRID_COLUMNS] = columns.coerceIn(2, 5) }
    }

    suspend fun setViewMode(mode: GalleryViewMode) {
        context.settingsDataStore.edit { it[Keys.VIEW_MODE] = mode.name }
    }

    suspend fun setThumbnailQuality(quality: ThumbnailQuality) {
        context.settingsDataStore.edit { it[Keys.THUMBNAIL_QUALITY] = quality.name }
    }

    suspend fun setAnimationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ANIMATIONS_ENABLED] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setDefaultSort(sortOrder: MediaSortOrder) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_SORT] = sortOrder.name }
    }

    suspend fun setVaultAutoLockSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.VAULT_AUTO_LOCK_SECONDS] = seconds }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setTrashRetentionDays(days: Int) {
        context.settingsDataStore.edit { it[Keys.TRASH_RETENTION_DAYS] = days }
    }
}
