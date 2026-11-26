package com.example.ui

import android.app.PendingIntent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.db.AlbumMediaCrossRef
import com.example.data.local.db.UserAlbumEntity
import com.example.domain.model.AccentPreset
import com.example.domain.model.Album
import com.example.domain.model.AlbumKind
import com.example.domain.model.AppSettings
import com.example.domain.model.GalleryViewMode
import com.example.domain.model.HomeMediaTab
import com.example.domain.model.MediaExifInfo
import com.example.domain.model.MediaItem
import com.example.domain.model.MediaSortOrder
import com.example.domain.model.MediaType
import com.example.domain.model.SmartAnalysisState
import com.example.domain.model.ThemeMode
import com.example.domain.model.ThumbnailQuality
import com.example.domain.model.TimelineSection
import com.example.domain.model.TrashItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class MediaAccessLevel {
    FULL,
    PARTIAL,
    DENIED
}

enum class SearchFilterChip(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    SCREENSHOTS("Screenshots"),
    FAVORITES("Favorites"),
    LARGE("Large (>10 MB)")
}

data class PendingDeleteConsent(
    val pendingIntent: PendingIntent,
    val stagedTrashIds: List<String>
)

data class GalleryUiState(
    val isLoading: Boolean = true,
    val accessLevel: MediaAccessLevel = MediaAccessLevel.DENIED,
    val homeTab: HomeMediaTab = HomeMediaTab.ALL,
    val sortOrder: MediaSortOrder = MediaSortOrder.DATE_DESC,
    val quickFilter: SearchFilterChip = SearchFilterChip.ALL,
    val allMedia: List<MediaItem> = emptyList(),
    val filteredHomeMedia: List<MediaItem> = emptyList(),
    val timelineSections: List<TimelineSection> = emptyList(),
    val recentlyAdded: List<MediaItem> = emptyList(),
    val recentlyViewed: List<MediaItem> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
    val userAlbums: List<Album> = emptyList(),
    val systemAlbums: List<Album> = emptyList(),
    val albumMediaMap: Map<String, List<MediaItem>> = emptyMap(),
    val selectedKeys: Set<String> = emptySet(),
    val trashItems: List<TrashItem> = emptyList(),
    val settings: AppSettings = AppSettings()
) {
    val isSelectionMode: Boolean get() = selectedKeys.isNotEmpty()
    val selectedItems: List<MediaItem>
        get() = allMedia.filter { it.mediaKey in selectedKeys }
}

class GalleryViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val mediaRepo = container.mediaRepository
    private val albumVaultRepo = container.albumAndVaultRepository
    private val settingsStore = container.settingsDataStore
    private val analyzer = container.mediaAnalyzer

    private val refreshSignal = MutableStateFlow(0)
    private val accessLevelState = MutableStateFlow(MediaAccessLevel.DENIED)
    private val homeTabState = MutableStateFlow(HomeMediaTab.ALL)
    private val activeSortOverride = MutableStateFlow<MediaSortOrder?>(null)
    private val quickFilterState = MutableStateFlow(SearchFilterChip.ALL)
    private val selectedKeysState = MutableStateFlow<Set<String>>(emptySet())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchFilter = MutableStateFlow(SearchFilterChip.ALL)
    val searchFilter: StateFlow<SearchFilterChip> = _searchFilter.asStateFlow()

    private val _smartState = MutableStateFlow(SmartAnalysisState())
    val smartState: StateFlow<SmartAnalysisState> = _smartState.asStateFlow()

    private val _deleteConsentEvents = MutableSharedFlow<PendingDeleteConsent>()
    val deleteConsentEvents: SharedFlow<PendingDeleteConsent> = _deleteConsentEvents.asSharedFlow()

    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            albumVaultRepo.purgeExpiredTrash()
        }
    }

    private val rawMediaFlow = mediaRepo.observeAllAccessibleMedia(refreshSignal)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val settingsFlow = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val controlsCombineFlow = combine(
        accessLevelState,
        homeTabState,
        activeSortOverride,
        quickFilterState,
        selectedKeysState
    ) { access, tab, sortOverride, quickFilter, selected ->
        ControlsTuple(access, tab, sortOverride, quickFilter, selected)
    }

    private val collectionsCombineFlow = combine(
        albumVaultRepo.recentlyViewedKeys,
        albumVaultRepo.userAlbums,
        albumVaultRepo.albumCrossRefs,
        albumVaultRepo.trashItems
    ) { recentViewedKeys, userAlbums, crossRefs, trash ->
        CollectionsTuple(recentViewedKeys, userAlbums, crossRefs, trash)
    }

    val uiState: StateFlow<GalleryUiState> = combine(
        rawMediaFlow,
        settingsFlow,
        controlsCombineFlow,
        collectionsCombineFlow
    ) { media, settings, controls, collections ->
        val effectiveSort = controls.sortOverride ?: settings.defaultSort
        val mediaByKey = media.associateBy { it.mediaKey }

        // Apply tab + quick filter + sort
        val tabFiltered = when (controls.homeTab) {
            HomeMediaTab.ALL -> media
            HomeMediaTab.PHOTOS -> media.filter { it.mediaType == MediaType.PHOTO }
            HomeMediaTab.VIDEOS -> media.filter { it.mediaType == MediaType.VIDEO }
        }

        val quickFiltered = applyFilterChip(tabFiltered, controls.quickFilter)
        val sortedHome = sortMedia(quickFiltered, effectiveSort)
        val sections = groupChronologically(sortedHome, effectiveSort)

        val favorites = sortMedia(media.filter { it.isFavorite }, MediaSortOrder.DATE_DESC)
        val recentlyAdded = media.sortedByDescending { it.effectiveTimestampMs }.take(15)
        val recentlyViewed = collections.recentViewedKeys.mapNotNull { mediaByKey[it] }.take(15)

        // Build user albums + media lookup map
        val crossRefsByAlbum = collections.crossRefs.groupBy { it.albumId }
        val albumMap = HashMap<String, List<MediaItem>>()

        val builtUserAlbums = collections.userAlbums.map { entity ->
            val refs = crossRefsByAlbum[entity.albumId].orEmpty()
            val albumItems = refs.mapNotNull { mediaByKey[it.mediaKey] }
            albumMap[entity.albumId] = albumItems
            Album(
                id = entity.albumId,
                title = entity.name,
                coverUri = entity.coverUri ?: albumItems.firstOrNull()?.contentUri,
                itemCount = albumItems.size,
                kind = AlbumKind.USER_ALBUM,
                totalSizeBytes = albumItems.sumOf { it.sizeBytes }
            )
        }

        // System device albums (Camera, Screenshots, Downloads, etc.)
        val builtSystemAlbums = media.groupBy { it.bucketId }
            .entries
            .map { (bucketId, bucketItems) ->
                val sortedBucket = bucketItems.sortedByDescending { it.effectiveTimestampMs }
                albumMap[bucketId] = sortedBucket
                Album(
                    id = bucketId,
                    title = sortedBucket.firstOrNull()?.bucketName ?: "Folder",
                    coverUri = sortedBucket.firstOrNull()?.contentUri,
                    itemCount = sortedBucket.size,
                    kind = AlbumKind.SYSTEM_ALBUM,
                    totalSizeBytes = sortedBucket.sumOf { it.sizeBytes }
                )
            }
            .sortedWith(
                compareByDescending<Album> { it.title.equals("Camera", ignoreCase = true) }
                    .thenByDescending { it.title.equals("Screenshots", ignoreCase = true) }
                    .thenByDescending { it.itemCount }
            )

        // Also register smart album keys in albumMap for AlbumDetailScreen
        albumMap["smart_favorites"] = favorites
        albumMap["smart_recent_added"] = media.sortedByDescending { it.effectiveTimestampMs }.take(50)
        albumMap["smart_recent_viewed"] = collections.recentViewedKeys.mapNotNull { mediaByKey[it] }

        // Keep storage breakdown updated automatically
        val breakdown = analyzer.computeStorageBreakdown(
            items = media,
            vaultBytes = albumVaultRepo.getVaultTotalBytes(),
            trashBytes = albumVaultRepo.getTrashTotalBytes()
        )
        if (!_smartState.value.isScanning) {
            _smartState.value = _smartState.value.copy(breakdown = breakdown)
        }

        GalleryUiState(
            isLoading = false,
            accessLevel = controls.accessLevel,
            homeTab = controls.homeTab,
            sortOrder = effectiveSort,
            quickFilter = controls.quickFilter,
            allMedia = media,
            filteredHomeMedia = sortedHome,
            timelineSections = sections,
            recentlyAdded = recentlyAdded,
            recentlyViewed = recentlyViewed,
            favorites = favorites,
            userAlbums = builtUserAlbums,
            systemAlbums = builtSystemAlbums,
            albumMediaMap = albumMap,
            selectedKeys = controls.selectedKeys.filter { it in mediaByKey }.toSet(),
            trashItems = collections.trashItems,
            settings = settings
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GalleryUiState())

    val searchResults: StateFlow<List<MediaItem>> = combine(
        rawMediaFlow,
        searchQuery,
        searchFilter,
        settingsFlow
    ) { media, query, filter, settings ->
        val filtered = applyFilterChip(media, filter)
        val trimmed = query.trim()
        val matched = if (trimmed.isEmpty()) {
            filtered
        } else {
            val yearFormat = SimpleDateFormat("yyyy", Locale.US)
            val monthFormat = SimpleDateFormat("MMMM MMM yyyy", Locale.getDefault())
            filtered.filter { item ->
                item.displayName.contains(trimmed, ignoreCase = true) ||
                    item.bucketName.contains(trimmed, ignoreCase = true) ||
                    item.mimeType.contains(trimmed, ignoreCase = true) ||
                    item.relativePath.contains(trimmed, ignoreCase = true) ||
                    (item.effectiveTimestampMs > 0 && (
                        yearFormat.format(Date(item.effectiveTimestampMs)).contains(trimmed, ignoreCase = true) ||
                            monthFormat.format(Date(item.effectiveTimestampMs)).contains(trimmed, ignoreCase = true)
                        ))
            }
        }
        sortMedia(matched, settings.defaultSort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateAccessLevel(level: MediaAccessLevel) {
        accessLevelState.value = level
        refreshMedia()
    }

    fun refreshMedia() {
        refreshSignal.value += 1
    }

    fun selectHomeTab(tab: HomeMediaTab) {
        homeTabState.value = tab
    }

    fun setSortOrder(order: MediaSortOrder) {
        activeSortOverride.value = order
    }

    fun setQuickFilter(filter: SearchFilterChip) {
        quickFilterState.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchFilter(filter: SearchFilterChip) {
        _searchFilter.value = filter
    }

    // --- Selection ---

    fun toggleSelection(mediaKey: String) {
        val current = selectedKeysState.value.toMutableSet()
        if (!current.add(mediaKey)) current.remove(mediaKey)
        selectedKeysState.value = current
    }

    fun selectAllVisible(items: List<MediaItem>) {
        selectedKeysState.value = items.map { it.mediaKey }.toSet()
    }

    fun clearSelection() {
        selectedKeysState.value = emptySet()
    }

    // --- Photo Picker Imports ---

    fun importPickerUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = mediaRepo.importPickerUris(uris)
            if (imported.isNotEmpty()) {
                _snackbarMessages.emit("Imported ${imported.size} item(s) to M Gallery")
            }
        }
    }

    // --- Favorites & Viewed ---

    fun toggleFavorite(item: MediaItem) {
        val nextFavorite = !item.isFavorite
        viewModelScope.launch {
            albumVaultRepo.setFavorite(item.mediaKey, nextFavorite)
            _snackbarMessages.emit(
                if (nextFavorite) "Added \"${item.displayName}\" to Favorites"
                else "Removed \"${item.displayName}\" from Favorites"
            )
        }
    }

    fun setFavoriteForSelected(favorite: Boolean) {
        val selected = uiState.value.selectedItems
        if (selected.isEmpty()) return
        viewModelScope.launch {
            albumVaultRepo.setFavorites(selected.map { it.mediaKey }, favorite)
            clearSelection()
            _snackbarMessages.emit(
                if (favorite) "Added ${selected.size} item(s) to Favorites"
                else "Removed ${selected.size} item(s) from Favorites"
            )
        }
    }

    fun toggleFavoriteForSelected() {
        val selected = uiState.value.selectedItems
        if (selected.isEmpty()) return
        val shouldFavorite = !selected.all { it.isFavorite }
        setFavoriteForSelected(shouldFavorite)
    }

    fun clearAllFavorites() {
        viewModelScope.launch {
            albumVaultRepo.clearAllFavorites()
            clearSelection()
            _snackbarMessages.emit("Cleared all favorites")
        }
    }

    fun recordViewed(item: MediaItem) {
        viewModelScope.launch {
            albumVaultRepo.recordViewed(item.mediaKey)
        }
    }

    suspend fun loadExif(item: MediaItem): MediaExifInfo = mediaRepo.loadExifInfo(item)

    fun buildShareIntent(items: List<MediaItem>) = mediaRepo.buildShareIntent(items)

    // --- Albums ---

    fun createUserAlbum(name: String, initialItems: List<MediaItem> = emptyList()) {
        viewModelScope.launch {
            albumVaultRepo.createAlbum(name, initialItems)
            clearSelection()
            _snackbarMessages.emit("Album \"$name\" created")
        }
    }

    fun renameUserAlbum(albumId: String, newName: String) {
        viewModelScope.launch {
            albumVaultRepo.renameAlbum(albumId, newName)
            _snackbarMessages.emit("Album renamed")
        }
    }

    fun updateAlbumCover(albumId: String, coverUri: String?) {
        viewModelScope.launch {
            albumVaultRepo.updateAlbumCover(albumId, coverUri)
            _snackbarMessages.emit("Album cover updated")
        }
    }

    fun deleteUserAlbum(albumId: String) {
        viewModelScope.launch {
            albumVaultRepo.deleteAlbum(albumId)
            _snackbarMessages.emit("Album removed (original media files preserved)")
        }
    }

    fun addItemsToAlbum(albumId: String, items: List<MediaItem>) {
        viewModelScope.launch {
            albumVaultRepo.addMediaToAlbum(albumId, items)
            clearSelection()
            _snackbarMessages.emit("Added ${items.size} item(s) to album")
        }
    }

    fun removeItemFromAlbum(albumId: String, mediaKey: String) {
        viewModelScope.launch {
            albumVaultRepo.removeMediaFromAlbum(albumId, mediaKey)
            _snackbarMessages.emit("Removed from album")
        }
    }

    // --- Recycle Bin & Scoped-Storage Deletion ---

    fun moveItemsToRecycleBin(items: List<MediaItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val retention = uiState.value.settings.trashRetentionDays
            val stagedIds = mutableListOf<String>()
            val storeUrisToDelete = mutableListOf<Uri>()

            for (item in items) {
                val staged = albumVaultRepo.stageToRecycleBin(item, retention)
                if (staged != null) {
                    stagedIds.add(staged.id)
                    if (!item.isImportedPickerItem) {
                        storeUrisToDelete.add(Uri.parse(item.contentUri))
                    }
                }
            }

            clearSelection()

            if (storeUrisToDelete.isEmpty()) {
                if (stagedIds.isNotEmpty()) {
                    _snackbarMessages.emit("Moved ${stagedIds.size} item(s) to Recycle Bin")
                }
                return@launch
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pi = mediaRepo.createDeletePendingIntent(storeUrisToDelete)
                if (pi != null) {
                    _deleteConsentEvents.emit(PendingDeleteConsent(pi, stagedIds))
                }
            } else {
                val deleted = mediaRepo.deleteMediaDirectlyLegacy(storeUrisToDelete)
                refreshMedia()
                _snackbarMessages.emit("Moved $deleted item(s) to Recycle Bin")
            }
        }
    }

    fun onDeleteConsentResult(approved: Boolean, stagedIds: List<String>) {
        viewModelScope.launch {
            if (approved) {
                refreshMedia()
                _snackbarMessages.emit("Moved ${stagedIds.size} item(s) to Recycle Bin")
            } else {
                albumVaultRepo.rollbackPendingTrash(stagedIds)
                _snackbarMessages.emit("Deletion cancelled")
            }
        }
    }

    fun restoreTrashItem(item: TrashItem) {
        viewModelScope.launch {
            val ok = albumVaultRepo.restoreFromRecycleBin(item)
            refreshMedia()
            _snackbarMessages.emit(
                if (ok) "Restored \"${item.displayName}\""
                else "Could not restore file"
            )
        }
    }

    fun restoreTrashItems(items: List<TrashItem>) {
        viewModelScope.launch {
            var count = 0
            items.forEach { if (albumVaultRepo.restoreFromRecycleBin(it)) count++ }
            refreshMedia()
            _snackbarMessages.emit("Restored $count item(s) to gallery")
        }
    }

    fun permanentlyDeleteTrashItem(item: TrashItem) {
        viewModelScope.launch {
            albumVaultRepo.permanentlyDeleteTrashItem(item)
            _snackbarMessages.emit("Permanently deleted \"${item.displayName}\"")
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch {
            albumVaultRepo.emptyRecycleBin()
            _snackbarMessages.emit("Recycle Bin emptied")
        }
    }

    // --- Smart Insights ---

    fun startSmartScan() {
        if (_smartState.value.isScanning) return
        val items = uiState.value.allMedia
        scanJob = viewModelScope.launch {
            _smartState.value = _smartState.value.copy(
                isScanning = true,
                progress = 0.05f,
                statusText = "Preparing scan..."
            )
            val result = analyzer.analyzeLibrary(
                items = items,
                vaultBytes = albumVaultRepo.getVaultTotalBytes(),
                trashBytes = albumVaultRepo.getTrashTotalBytes(),
                onProgress = { progress, label ->
                    _smartState.value = _smartState.value.copy(
                        isScanning = true,
                        progress = progress,
                        statusText = label
                    )
                }
            )
            _smartState.value = result
        }
    }

    fun cancelSmartScan() {
        scanJob?.cancel()
        _smartState.value = _smartState.value.copy(
            isScanning = false,
            statusText = "Scan cancelled"
        )
    }

    // --- Settings Persistence ---

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(mode) }
    fun setAccentPreset(preset: AccentPreset) = viewModelScope.launch { settingsStore.setAccentPreset(preset) }
    fun setGridColumns(cols: Int) = viewModelScope.launch { settingsStore.setGridColumns(cols) }
    fun setViewMode(mode: GalleryViewMode) = viewModelScope.launch { settingsStore.setViewMode(mode) }
    fun setThumbnailQuality(quality: ThumbnailQuality) = viewModelScope.launch { settingsStore.setThumbnailQuality(quality) }
    fun setAnimationsEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setAnimationsEnabled(enabled) }
    fun setHapticsEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setHapticsEnabled(enabled) }
    fun setDefaultSort(sort: MediaSortOrder) = viewModelScope.launch {
        activeSortOverride.value = null
        settingsStore.setDefaultSort(sort)
    }
    fun setVaultAutoLockSeconds(sec: Int) = viewModelScope.launch { settingsStore.setVaultAutoLockSeconds(sec) }
    fun setBiometricEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setBiometricEnabled(enabled) }
    fun setTrashRetentionDays(days: Int) = viewModelScope.launch { settingsStore.setTrashRetentionDays(days) }

    // --- Helpers ---

    private fun applyFilterChip(items: List<MediaItem>, filter: SearchFilterChip): List<MediaItem> {
        return when (filter) {
            SearchFilterChip.ALL -> items
            SearchFilterChip.PHOTOS -> items.filter { it.mediaType == MediaType.PHOTO }
            SearchFilterChip.VIDEOS -> items.filter { it.mediaType == MediaType.VIDEO }
            SearchFilterChip.SCREENSHOTS -> items.filter { it.isScreenshot }
            SearchFilterChip.FAVORITES -> items.filter { it.isFavorite }
            SearchFilterChip.LARGE -> items.filter { it.sizeBytes >= 10L * 1024L * 1024L }
        }
    }

    companion object {
        fun sortMedia(items: List<MediaItem>, order: MediaSortOrder): List<MediaItem> {
            return when (order) {
                MediaSortOrder.DATE_DESC -> items.sortedByDescending { it.effectiveTimestampMs }
                MediaSortOrder.DATE_ASC -> items.sortedBy { it.effectiveTimestampMs }
                MediaSortOrder.NAME_ASC -> items.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
                MediaSortOrder.SIZE_DESC -> items.sortedByDescending { it.sizeBytes }
                MediaSortOrder.TYPE -> items.sortedWith(
                    compareBy<MediaItem> { it.mediaType.ordinal }
                        .thenByDescending { it.effectiveTimestampMs }
                )
            }
        }

        fun groupChronologically(items: List<MediaItem>, order: MediaSortOrder): List<TimelineSection> {
            if (items.isEmpty()) return emptyList()
            if (order != MediaSortOrder.DATE_DESC && order != MediaSortOrder.DATE_ASC) {
                return listOf(TimelineSection(order.label, items))
            }

            val calNow = Calendar.getInstance()
            val todayYear = calNow.get(Calendar.YEAR)
            val todayDay = calNow.get(Calendar.DAY_OF_YEAR)

            val calItem = Calendar.getInstance()
            val monthYearFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val dayFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

            return items.groupBy { item ->
                val ts = item.effectiveTimestampMs
                if (ts <= 0L) {
                    "Undated"
                } else {
                    calItem.timeInMillis = ts
                    val y = calItem.get(Calendar.YEAR)
                    val d = calItem.get(Calendar.DAY_OF_YEAR)
                    when {
                        y == todayYear && d == todayDay -> "Today"
                        y == todayYear && d == todayDay - 1 -> "Yesterday"
                        y == todayYear -> dayFmt.format(Date(ts))
                        else -> monthYearFmt.format(Date(ts))
                    }
                }
            }.map { (label, groupItems) -> TimelineSection(label, groupItems) }
        }

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return GalleryViewModel(container) as T
                }
            }
    }
}

private data class ControlsTuple(
    val accessLevel: MediaAccessLevel,
    val homeTab: HomeMediaTab,
    val sortOverride: MediaSortOrder?,
    val quickFilter: SearchFilterChip,
    val selectedKeys: Set<String>
)

private data class CollectionsTuple(
    val recentViewedKeys: List<String>,
    val userAlbums: List<UserAlbumEntity>,
    val crossRefs: List<AlbumMediaCrossRef>,
    val trashItems: List<TrashItem>
)
