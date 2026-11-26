package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.Album
import com.example.domain.model.GalleryViewMode
import com.example.domain.model.HomeMediaTab
import com.example.domain.model.MediaItem
import com.example.domain.model.MediaSortOrder
import com.example.ui.GalleryUiState
import com.example.ui.MediaAccessLevel
import com.example.ui.SearchFilterChip
import com.example.ui.components.AlbumPickerBottomSheet
import com.example.ui.components.MGalleryWordmark
import com.example.ui.components.MediaGridThumbnail
import com.example.ui.components.MediaListRow
import com.example.ui.components.PermissionAndEmptyStateCard
import com.example.ui.components.SelectionBottomActionBar
import com.example.ui.components.SkeletonMediaGrid
import com.example.ui.components.SortAndLayoutSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    state: GalleryUiState,
    onSelectTab: (HomeMediaTab) -> Unit,
    onSelectQuickFilter: (SearchFilterChip) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMedia: (MediaItem, List<MediaItem>) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onFavoriteSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onMoveSelectedToVault: () -> Unit,
    onDeleteSelected: () -> Unit,
    onAddSelectedToAlbum: (String) -> Unit,
    onCreateAlbumWithSelected: (String) -> Unit,
    onOpenAlbum: (String, String) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenSmartTools: () -> Unit,
    onSortChange: (MediaSortOrder) -> Unit,
    onViewModeChange: (GalleryViewMode) -> Unit,
    onGridColsChange: (Int) -> Unit,
    onRequestMediaPermission: () -> Unit,
    onLaunchPhotoPicker: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortSheet by remember { mutableStateOf(false) }
    var showAlbumPicker by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar or Contextual Selection Top Bar
            if (state.isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${state.selectedKeys.size} selected",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.testTag("selection_count_text")
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = onSelectAll) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            } else {
                TopAppBar(
                    title = { MGalleryWordmark() },
                    actions = {
                        IconButton(
                            onClick = onOpenFavorites,
                            modifier = Modifier.testTag("home_favorites_button")
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = "Open Favorites")
                        }
                        IconButton(
                            onClick = onLaunchPhotoPicker,
                            modifier = Modifier.testTag("home_import_picker_button")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Import from Photo Picker")
                        }
                        IconButton(
                            onClick = { showSortSheet = true },
                            modifier = Modifier.testTag("home_sort_button")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = "View and sort options")
                        }
                        IconButton(
                            onClick = onOpenSearch,
                            modifier = Modifier.testTag("home_search_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("home_settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }

            // Photos / Videos / All Tabs
            PrimaryTabRow(
                selectedTabIndex = state.homeTab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                HomeMediaTab.entries.forEach { tab ->
                    Tab(
                        selected = state.homeTab == tab,
                        onClick = { onSelectTab(tab) },
                        text = { Text(tab.label, style = MaterialTheme.typography.titleSmall) },
                        modifier = Modifier.testTag("home_tab_${tab.name.lowercase()}")
                    )
                }
            }

            // Quick Filter Chips + Smart Tools shortcut
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    SearchFilterChip.ALL to "All",
                    SearchFilterChip.FAVORITES to "Favorites",
                    SearchFilterChip.SCREENSHOTS to "Screenshots",
                    SearchFilterChip.LARGE to "Large Files"
                ).forEach { (chip, label) ->
                    FilterChip(
                        selected = state.quickFilter == chip,
                        onClick = { onSelectQuickFilter(chip) },
                        label = { Text(label) }
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = onOpenSmartTools,
                    label = { Text("Insights") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("home_smart_insights_chip")
                )
                FilterChip(
                    selected = false,
                    onClick = onOpenRecycleBin,
                    label = {
                        Text(
                            if (state.trashItems.isEmpty()) "Trash"
                            else "Trash (${state.trashItems.size})"
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.DeleteSweep, null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("home_recycle_bin_chip")
                )
            }

            // Main scrollable body
            when {
                state.isLoading -> {
                    SkeletonMediaGrid(columns = state.settings.gridColumns)
                }

                state.filteredHomeMedia.isEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        item {
                            PermissionAndEmptyStateCard(
                                needsPermission = state.accessLevel == MediaAccessLevel.DENIED,
                                isPartialAccess = state.accessLevel == MediaAccessLevel.PARTIAL,
                                onGrantPermission = onRequestMediaPermission,
                                onPickWithPhotoPicker = onLaunchPhotoPicker,
                                onOpenSystemSettings = onOpenAppSettings
                            )
                        }
                    }
                }

                else -> {
                    val cols = state.settings.gridColumns.coerceIn(2, 5)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = if (state.isSelectionMode) 110.dp else 28.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Partial / Denied banner when imported picker items are shown
                        if (state.accessLevel != MediaAccessLevel.FULL) {
                            item("access_banner") {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (state.accessLevel == MediaAccessLevel.PARTIAL)
                                                    "Partial library access" else "Browsing imported media",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "Grant full access to index all device albums automatically.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(onClick = onRequestMediaPermission) {
                                            Text("Allow")
                                        }
                                    }
                                }
                            }
                        }

                        // Curated Discovery Sections (shown in All tab with All filter & not selecting)
                        if (state.homeTab == HomeMediaTab.ALL &&
                            state.quickFilter == SearchFilterChip.ALL &&
                            !state.isSelectionMode
                        ) {
                            // Favorites Shortcut + Smart Summary row
                            item("shortcuts_row") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ShortcutHighlightCard(
                                        title = "Favorites",
                                        subtitle = "${state.favorites.size} items",
                                        coverUri = state.favorites.firstOrNull()?.contentUri,
                                        icon = Icons.Default.Favorite,
                                        accentTint = Color(0xFFF43F5E),
                                        onClick = onOpenFavorites,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("home_favorites_shortcut")
                                    )
                                    ShortcutHighlightCard(
                                        title = "Recently Added",
                                        subtitle = "${state.recentlyAdded.size} recent",
                                        coverUri = state.recentlyAdded.firstOrNull()?.contentUri,
                                        icon = Icons.Default.PhotoLibrary,
                                        accentTint = MaterialTheme.colorScheme.primary,
                                        onClick = { onOpenAlbum("smart_recent_added", "Recently Added") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("home_recent_added_shortcut")
                                    )
                                }
                            }

                            // Recently Added Horizontal Strip
                            if (state.recentlyAdded.isNotEmpty()) {
                                item("recent_added_strip") {
                                    HorizontalMediaStrip(
                                        title = "Recently Added",
                                        items = state.recentlyAdded,
                                        onSeeAll = { onOpenAlbum("smart_recent_added", "Recently Added") },
                                        onItemClick = { item -> onOpenMedia(item, state.recentlyAdded) }
                                    )
                                }
                            }

                            // Albums Quick Strip
                            val combinedAlbums = (state.userAlbums + state.systemAlbums).take(10)
                            if (combinedAlbums.isNotEmpty()) {
                                item("albums_strip") {
                                    HorizontalAlbumsStrip(
                                        albums = combinedAlbums,
                                        onAlbumClick = { album -> onOpenAlbum(album.id, album.title) }
                                    )
                                }
                            }

                            // Recently Viewed Horizontal Strip
                            if (state.recentlyViewed.isNotEmpty()) {
                                item("recent_viewed_strip") {
                                    HorizontalMediaStrip(
                                        title = "Recently Viewed",
                                        items = state.recentlyViewed,
                                        onSeeAll = { onOpenAlbum("smart_recent_viewed", "Recently Viewed") },
                                        onItemClick = { item -> onOpenMedia(item, state.recentlyViewed) }
                                    )
                                }
                            }
                        }

                        // Chronological Sections
                        state.timelineSections.forEach { section ->
                            item(key = "hdr_${section.label}") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = section.label,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "${section.items.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (state.settings.viewMode == GalleryViewMode.LIST) {
                                items(
                                    items = section.items,
                                    key = { "list_${section.label}_${it.mediaKey}" }
                                ) { mediaItem ->
                                    MediaListRow(
                                        item = mediaItem,
                                        isSelectionMode = state.isSelectionMode,
                                        isSelected = mediaItem.mediaKey in state.selectedKeys,
                                        onClick = {
                                            if (state.isSelectionMode) onToggleSelect(mediaItem.mediaKey)
                                            else onOpenMedia(mediaItem, state.filteredHomeMedia)
                                        },
                                        onLongClick = { onToggleSelect(mediaItem.mediaKey) },
                                        onToggleFavorite = { onToggleFavorite(mediaItem) },
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            } else {
                                val rows = section.items.chunked(cols)
                                items(
                                    count = rows.size,
                                    key = { rowIdx -> "grid_${section.label}_$rowIdx" }
                                ) { rowIdx ->
                                    val rowItems = rows[rowIdx]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        rowItems.forEach { mediaItem ->
                                            MediaGridThumbnail(
                                                item = mediaItem,
                                                isSelectionMode = state.isSelectionMode,
                                                isSelected = mediaItem.mediaKey in state.selectedKeys,
                                                thumbnailQuality = state.settings.thumbnailQuality,
                                                animationsEnabled = state.settings.animationsEnabled,
                                                hapticsEnabled = state.settings.hapticsEnabled,
                                                onClick = {
                                                    if (state.isSelectionMode) onToggleSelect(mediaItem.mediaKey)
                                                    else onOpenMedia(mediaItem, state.filteredHomeMedia)
                                                },
                                                onLongClick = { onToggleSelect(mediaItem.mediaKey) },
                                                onFavoriteToggle = { onToggleFavorite(mediaItem) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(cols - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.isSelectionMode,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SelectionBottomActionBar(
                selectedCount = state.selectedKeys.size,
                allSelectedAreFavorites = state.selectedItems.isNotEmpty() && state.selectedItems.all { it.isFavorite },
                onFavorite = onFavoriteSelected,
                onAddToAlbum = { showAlbumPicker = true },
                onMoveToVault = onMoveSelectedToVault,
                onShare = onShareSelected,
                onDelete = onDeleteSelected
            )
        }
    }

    if (showSortSheet) {
        SortAndLayoutSheet(
            currentSort = state.sortOrder,
            viewMode = state.settings.viewMode,
            gridColumns = state.settings.gridColumns,
            onSortSelected = onSortChange,
            onViewModeSelected = onViewModeChange,
            onGridColumnsSelected = onGridColsChange,
            onDismiss = { showSortSheet = false }
        )
    }

    if (showAlbumPicker) {
        AlbumPickerBottomSheet(
            userAlbums = state.userAlbums,
            onPickAlbum = { albumId -> onAddSelectedToAlbum(albumId) },
            onCreateNewAlbum = { name -> onCreateAlbumWithSelected(name) },
            onDismiss = { showAlbumPicker = false }
        )
    }
}

@Composable
private fun ShortcutHighlightCard(
    title: String,
    subtitle: String,
    coverUri: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.height(82.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (coverUri != null) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.25f,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = accentTint.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = accentTint, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HorizontalMediaStrip(
    title: String,
    items: List<MediaItem>,
    onSeeAll: () -> Unit,
    onItemClick: (MediaItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onSeeAll) { Text("See all") }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { "${title}_${it.mediaKey}" }) { media ->
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onItemClick(media) }
                ) {
                    AsyncImage(
                        model = media.contentUri,
                        contentDescription = media.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalAlbumsStrip(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Albums",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                Card(
                    onClick = { onAlbumClick(album) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.width(124.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.2f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (album.coverUri != null) {
                                AsyncImage(
                                    model = album.coverUri,
                                    contentDescription = album.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = album.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${album.itemCount} items",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
