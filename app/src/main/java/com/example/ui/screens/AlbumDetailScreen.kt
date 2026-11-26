package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.MediaItem
import com.example.domain.model.MediaSortOrder
import com.example.ui.GalleryUiState
import com.example.ui.GalleryViewModel
import com.example.ui.components.AlbumPickerBottomSheet
import com.example.ui.components.MediaGridThumbnail
import com.example.ui.components.SelectionBottomActionBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    albumTitle: String,
    state: GalleryUiState,
    onBack: () -> Unit,
    onOpenMedia: (MediaItem, List<MediaItem>) -> Unit,
    onAddItemsToAlbum: (String, List<MediaItem>) -> Unit,
    onRemoveItemFromUserAlbum: (String, String) -> Unit,
    onRenameAlbum: (String, String) -> Unit,
    onUpdateCover: (String, String?) -> Unit,
    onDeleteAlbum: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: (List<MediaItem>) -> Unit,
    onClearSelection: () -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onFavoriteSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onMoveSelectedToVault: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCreateAlbumWithSelected: (String) -> Unit
) {
    val isUserAlbum = albumId.startsWith("user_")
    var localSort by remember { mutableStateOf(state.sortOrder) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAddMediaSheet by remember { mutableStateOf(false) }
    var showCoverPickerSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAlbumPicker by remember { mutableStateOf(false) }

    val rawItems = state.albumMediaMap[albumId].orEmpty()
    val sortedItems = remember(rawItems, localSort) {
        GalleryViewModel.sortMedia(rawItems, localSort)
    }

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
                        IconButton(onClick = { onSelectAll(sortedItems) }) {
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
                            Text(albumTitle, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${sortedItems.size} items",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (isUserAlbum) {
                            IconButton(onClick = { showAddMediaSheet = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Add media to album")
                            }
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort album")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                MediaSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.label) },
                                        onClick = {
                                            localSort = order
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        if (isUserAlbum) {
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Album options")
                                }
                                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Rename album") },
                                        onClick = {
                                            showMoreMenu = false
                                            showRenameDialog = true
                                        }
                                    )
                                    if (sortedItems.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Choose cover photo") },
                                            onClick = {
                                                showMoreMenu = false
                                                showCoverPickerSheet = true
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Delete album") },
                                        onClick = {
                                            showMoreMenu = false
                                            showDeleteConfirm = true
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
            if (sortedItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("This album is empty", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (isUserAlbum) "Tap + to add photos or videos from your library without duplicating files."
                        else "No items currently in this collection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isUserAlbum) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { showAddMediaSheet = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Add Media")
                        }
                    }
                }
            } else {
                val cols = state.settings.gridColumns.coerceIn(2, 5)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = if (state.isSelectionMode) 110.dp else 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sortedItems, key = { it.mediaKey }) { item ->
                        MediaGridThumbnail(
                            item = item,
                            isSelectionMode = state.isSelectionMode,
                            isSelected = item.mediaKey in state.selectedKeys,
                            thumbnailQuality = state.settings.thumbnailQuality,
                            animationsEnabled = state.settings.animationsEnabled,
                            hapticsEnabled = state.settings.hapticsEnabled,
                            onClick = {
                                if (state.isSelectionMode) onToggleSelect(item.mediaKey)
                                else onOpenMedia(item, sortedItems)
                            },
                            onLongClick = { onToggleSelect(item.mediaKey) },
                            onFavoriteToggle = { onToggleFavorite(item) }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = state.isSelectionMode,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column {
                    if (isUserAlbum) {
                        TextButton(
                            onClick = {
                                state.selectedKeys.forEach { key -> onRemoveItemFromUserAlbum(albumId, key) }
                                onClearSelection()
                            },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 4.dp)
                        ) {
                            Text("Remove selected from this album")
                        }
                    }
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
        }
    }

    if (showAddMediaSheet) {
        val existingKeys = rawItems.mapTo(HashSet()) { it.mediaKey }
        val candidates = state.allMedia.filter { it.mediaKey !in existingKeys }
        var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
        ModalBottomSheet(onDismissRequest = { showAddMediaSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Add Media (${picked.size})", style = MaterialTheme.typography.titleLarge)
                    Button(
                        onClick = {
                            val selected = candidates.filter { it.mediaKey in picked }
                            onAddItemsToAlbum(albumId, selected)
                            showAddMediaSheet = false
                        },
                        enabled = picked.isNotEmpty()
                    ) { Text("Add") }
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.height(340.dp)
                ) {
                    items(candidates, key = { it.mediaKey }) { item ->
                        val isPicked = item.mediaKey in picked
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    picked = if (isPicked) picked - item.mediaKey else picked + item.mediaKey
                                }
                        ) {
                            AsyncImage(
                                model = item.contentUri,
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Icon(
                                imageVector = if (isPicked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isPicked) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCoverPickerSheet) {
        ModalBottomSheet(onDismissRequest = { showCoverPickerSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text("Select Album Cover", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.height(300.dp)
                ) {
                    items(sortedItems, key = { it.mediaKey }) { item ->
                        AsyncImage(
                            model = item.contentUri,
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onUpdateCover(albumId, item.contentUri)
                                    showCoverPickerSheet = false
                                }
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        var newName by remember(albumTitle) { mutableStateOf(albumTitle) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Album") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameAlbum(albumId, newName)
                        showRenameDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"$albumTitle\"?") },
            text = { Text("Original photos and videos will not be deleted from your device.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteAlbum(albumId)
                    showDeleteConfirm = false
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showAlbumPicker) {
        AlbumPickerBottomSheet(
            userAlbums = state.userAlbums,
            onPickAlbum = { targetId ->
                onAddItemsToAlbum(targetId, state.selectedItems)
                onClearSelection()
            },
            onCreateNewAlbum = { name -> onCreateAlbumWithSelected(name) },
            onDismiss = { showAlbumPicker = false }
        )
    }
}
