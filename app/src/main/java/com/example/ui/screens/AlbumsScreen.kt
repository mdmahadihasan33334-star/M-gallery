package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.repository.MediaRepository
import com.example.domain.model.Album
import com.example.ui.GalleryUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    state: GalleryUiState,
    onOpenAlbum: (String, String) -> Unit,
    onOpenFavorites: () -> Unit,
    onCreateAlbum: (String) -> Unit,
    onRenameAlbum: (String, String) -> Unit,
    onDeleteAlbum: (String) -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenSmartTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Album?>(null) }
    var deleteTarget by remember { mutableStateOf<Album?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Albums", style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    TextButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("create_album_top_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Smart Collections
                item("smart_collections") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Collections",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item("favs") {
                                SmartCollectionTile(
                                    title = "Favorites",
                                    count = "${state.favorites.size} items",
                                    icon = Icons.Default.Favorite,
                                    tint = Color(0xFFF43F5E),
                                    onClick = onOpenFavorites
                                )
                            }
                            item("recent_add") {
                                SmartCollectionTile(
                                    title = "Recently Added",
                                    count = "${state.recentlyAdded.size} items",
                                    icon = Icons.Default.Schedule,
                                    tint = MaterialTheme.colorScheme.primary,
                                    onClick = { onOpenAlbum("smart_recent_added", "Recently Added") }
                                )
                            }
                            item("recent_view") {
                                SmartCollectionTile(
                                    title = "Recently Viewed",
                                    count = "${state.recentlyViewed.size} items",
                                    icon = Icons.Default.History,
                                    tint = Color(0xFF06B6D4),
                                    onClick = { onOpenAlbum("smart_recent_viewed", "Recently Viewed") }
                                )
                            }
                            item("smart_insights") {
                                SmartCollectionTile(
                                    title = "Smart Insights",
                                    count = "Cleanup & Stats",
                                    icon = Icons.Default.AutoAwesome,
                                    tint = Color(0xFFF59E0B),
                                    onClick = onOpenSmartTools
                                )
                            }
                            item("recycle_bin") {
                                SmartCollectionTile(
                                    title = "Recycle Bin",
                                    count = "${state.trashItems.size} deleted",
                                    icon = Icons.Default.DeleteSweep,
                                    tint = Color(0xFF9CA3AF),
                                    onClick = onOpenRecycleBin
                                )
                            }
                        }
                    }
                }

                // User-Created Albums
                item("user_albums_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Your Albums", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${state.userAlbums.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.userAlbums.isEmpty()) {
                    item("user_albums_empty") {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showCreateDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Create your first album", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Organize media logically without duplicating files.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val rows = state.userAlbums.chunked(2)
                    items(rows.size, key = { "user_row_$it" }) { idx ->
                        val row = rows[idx]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { album ->
                                AlbumGridCard(
                                    album = album,
                                    showMenu = true,
                                    onClick = { onOpenAlbum(album.id, album.title) },
                                    onRename = { renameTarget = album },
                                    onDelete = { deleteTarget = album },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // System Device Albums
                item("system_albums_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Device Folders", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${state.systemAlbums.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.systemAlbums.isEmpty()) {
                    item("system_albums_empty") {
                        Text(
                            text = "No device folders indexed yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    val sysRows = state.systemAlbums.chunked(2)
                    items(sysRows.size, key = { "sys_row_$it" }) { idx ->
                        val row = sysRows[idx]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { album ->
                                AlbumGridCard(
                                    album = album,
                                    showMenu = false,
                                    onClick = { onOpenAlbum(album.id, album.title) },
                                    onRename = {},
                                    onDelete = {},
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("create_album_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create album")
        }
    }

    if (showCreateDialog) {
        AlbumNameDialog(
            title = "Create Album",
            initialName = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                onCreateAlbum(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    renameTarget?.let { target ->
        AlbumNameDialog(
            title = "Rename Album",
            initialName = target.title,
            confirmLabel = "Save",
            onConfirm = { newName ->
                onRenameAlbum(target.id, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${target.title}\"?") },
            text = { Text("This removes the album grouping only. Your original photos and videos remain safely in your library.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteAlbum(target.id)
                    deleteTarget = null
                }) { Text("Delete Album") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SmartCollectionTile(
    title: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.width(136.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Surface(
                color = tint.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlbumGridCard(
    album: Album,
    showMenu: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (album.coverUri != null) {
                    AsyncImage(
                        model = album.coverUri,
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                }
                if (showMenu) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Album options", tint = Color.White)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    menuExpanded = false
                                    onRename()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete album") },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${album.itemCount} items • ${MediaRepository.formatBytes(album.totalSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AlbumNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Album name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("album_name_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("album_name_confirm_button")
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
