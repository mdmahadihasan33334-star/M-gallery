package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.GalleryViewMode
import com.example.domain.model.HomeMediaTab
import com.example.domain.model.MediaItem
import com.example.domain.model.MediaSortOrder
import com.example.domain.model.MediaType
import com.example.ui.GalleryUiState
import com.example.ui.GalleryViewModel
import com.example.ui.components.AlbumPickerBottomSheet
import com.example.ui.components.MediaGridThumbnail
import com.example.ui.components.MediaListRow
import com.example.ui.components.SelectionBottomActionBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    state: GalleryUiState,
    onBack: () -> Unit,
    onOpenMedia: (MediaItem, List<MediaItem>) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onToggleFavoriteSelected: () -> Unit,
    onClearAllFavorites: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: (List<MediaItem>) -> Unit,
    onClearSelection: () -> Unit,
    onAddSelectedToAlbum: (String) -> Unit,
    onCreateAlbumWithSelected: (String) -> Unit,
    onMoveSelectedToVault: () -> Unit,
    onShareSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onViewModeChange: (GalleryViewMode) -> Unit
) {
    var activeFilter by remember { mutableStateOf(HomeMediaTab.ALL) }
    var sortOrder by remember { mutableStateOf(state.sortOrder) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showAlbumPicker by remember { mutableStateOf(false) }

    val favoriteItems = state.favorites
    val filteredFavorites = remember(favoriteItems, activeFilter, sortOrder) {
        val typed = when (activeFilter) {
            HomeMediaTab.ALL -> favoriteItems
            HomeMediaTab.PHOTOS -> favoriteItems.filter { it.mediaType == MediaType.PHOTO }
            HomeMediaTab.VIDEOS -> favoriteItems.filter { it.mediaType == MediaType.VIDEO }
        }
        GalleryViewModel.sortMedia(typed, sortOrder)
    }

    val photoCount = remember(favoriteItems) { favoriteItems.count { it.mediaType == MediaType.PHOTO } }
    val videoCount = remember(favoriteItems) { favoriteItems.count { it.mediaType == MediaType.VIDEO } }

    Scaffold(
        topBar = {
            if (state.isSelectionMode) {
                TopAppBar(
                    title = { Text("${state.selectedKeys.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { onSelectAll(filteredFavorites) }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Favorites", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = if (favoriteItems.isEmpty()) "No favorites saved"
                                else "$photoCount photos • $videoCount videos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("favorites_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val nextMode = if (state.settings.viewMode == GalleryViewMode.GRID) {
                                    GalleryViewMode.LIST
                                } else GalleryViewMode.GRID
                                onViewModeChange(nextMode)
                            },
                            modifier = Modifier.testTag("favorites_view_mode_button")
                        ) {
                            Icon(
                                imageVector = if (state.settings.viewMode == GalleryViewMode.GRID) {
                                    Icons.Default.ViewList
                                } else Icons.Default.GridView,
                                contentDescription = "Toggle grid or list view"
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort favorites")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                MediaSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.label) },
                                        onClick = {
                                            sortOrder = order
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        if (favoriteItems.isNotEmpty()) {
                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier.testTag("favorites_more_button")
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Favorites options")
                                }
                                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Clear all favorites") },
                                        onClick = {
                                            showMoreMenu = false
                                            showClearConfirm = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (favoriteItems.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HomeMediaTab.entries.forEach { tab ->
                            FilterChip(
                                selected = activeFilter == tab,
                                onClick = { activeFilter = tab },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("favorites_filter_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                if (filteredFavorites.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = Color(0xFFF43F5E).copy(alpha = 0.14f),
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color(0xFFF43F5E),
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (favoriteItems.isEmpty()) "No Favorites Yet" else "No matching ${activeFilter.label.lowercase()}",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (favoriteItems.isEmpty()) {
                                "Tap the heart icon on any photo or video to save it here. Your favorites are stored locally in M Gallery."
                            } else {
                                "Try switching the filter chip above to see your other favorited media."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (favoriteItems.isEmpty()) {
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = onBack,
                                modifier = Modifier.testTag("favorites_browse_button")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Browse Gallery")
                            }
                        }
                    }
                } else if (state.settings.viewMode == GalleryViewMode.LIST) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 6.dp,
                            bottom = if (state.isSelectionMode) 110.dp else 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredFavorites, key = { it.mediaKey }) { item ->
                            MediaListRow(
                                item = item,
                                isSelectionMode = state.isSelectionMode,
                                isSelected = item.mediaKey in state.selectedKeys,
                                onClick = {
                                    if (state.isSelectionMode) onToggleSelect(item.mediaKey)
                                    else onOpenMedia(item, filteredFavorites)
                                },
                                onLongClick = { onToggleSelect(item.mediaKey) },
                                onToggleFavorite = { onToggleFavorite(item) }
                            )
                        }
                    }
                } else {
                    val cols = state.settings.gridColumns.coerceIn(2, 5)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(cols),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 6.dp,
                            bottom = if (state.isSelectionMode) 110.dp else 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredFavorites, key = { it.mediaKey }) { item ->
                            MediaGridThumbnail(
                                item = item,
                                isSelectionMode = state.isSelectionMode,
                                isSelected = item.mediaKey in state.selectedKeys,
                                thumbnailQuality = state.settings.thumbnailQuality,
                                animationsEnabled = state.settings.animationsEnabled,
                                hapticsEnabled = state.settings.hapticsEnabled,
                                onClick = {
                                    if (state.isSelectionMode) onToggleSelect(item.mediaKey)
                                    else onOpenMedia(item, filteredFavorites)
                                },
                                onLongClick = { onToggleSelect(item.mediaKey) },
                                onFavoriteToggle = { onToggleFavorite(item) }
                            )
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
                    onFavorite = onToggleFavoriteSelected,
                    onAddToAlbum = { showAlbumPicker = true },
                    onMoveToVault = onMoveSelectedToVault,
                    onShare = onShareSelected,
                    onDelete = onDeleteSelected
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All Favorites?") },
            text = { Text("This removes the favorite mark from all ${favoriteItems.size} item(s). Your original photos and videos remain untouched in your library.") },
            confirmButton = {
                Button(onClick = {
                    onClearAllFavorites()
                    showClearConfirm = false
                }) { Text("Clear Favorites") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
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
