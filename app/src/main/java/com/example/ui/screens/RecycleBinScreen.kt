package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.example.domain.model.TrashItem
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecycleBinScreen(
    trashItems: List<TrashItem>,
    retentionDays: Int,
    onBack: () -> Unit,
    onRestoreItems: (List<TrashItem>) -> Unit,
    onPermanentlyDelete: (TrashItem) -> Unit,
    onEmptyAll: () -> Unit
) {
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var inspectItem by remember { mutableStateOf<TrashItem?>(null) }
    var showEmptyConfirm by remember { mutableStateOf(false) }
    var confirmPermanentItem by remember { mutableStateOf<TrashItem?>(null) }

    val selectedList = remember(trashItems, selectedIds) {
        trashItems.filter { it.id in selectedIds }
    }

    Scaffold(
        topBar = {
            if (selectedIds.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            onRestoreItems(selectedList)
                            selectedIds = emptySet()
                        }) { Text("Restore") }
                        TextButton(onClick = {
                            selectedList.forEach { onPermanentlyDelete(it) }
                            selectedIds = emptySet()
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Recycle Bin", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Auto-cleanup after $retentionDays days",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("recycle_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (trashItems.isNotEmpty()) {
                            TextButton(
                                onClick = { onRestoreItems(trashItems) },
                                modifier = Modifier.testTag("recycle_restore_all_button")
                            ) { Text("Restore All") }
                            IconButton(
                                onClick = { showEmptyConfirm = true },
                                modifier = Modifier.testTag("recycle_empty_all_button")
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "Empty Recycle Bin", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Items deleted through M Gallery are held here for $retentionDays days before automatic permanent removal. Files deleted outside M Gallery or permanently purged cannot be recovered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (trashItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Recycle Bin is empty", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Deleted items appear here until restored or expired.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(trashItems, key = { it.id }) { item ->
                        val isSelected = item.id in selectedIds
                        val previewPath = item.trashedThumbPath ?: item.trashedFilePath

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (selectedIds.isNotEmpty()) {
                                            selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                        } else {
                                            inspectItem = item
                                        }
                                    },
                                    onLongClick = {
                                        selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                    }
                                )
                        ) {
                            AsyncImage(
                                model = File(previewPath),
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.68f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "${item.daysRemaining}d left",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (selectedIds.isNotEmpty()) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
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
    }

    inspectItem?.let { item ->
        AlertDialog(
            onDismissRequest = { inspectItem = null },
            title = {
                Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsyncImage(
                        model = File(item.trashedThumbPath ?: item.trashedFilePath),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Text(
                        "${MediaRepository.formatBytes(item.sizeBytes)} • ${item.daysRemaining} day(s) before auto-removal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreItems(listOf(item))
                        inspectItem = null
                    },
                    modifier = Modifier.testTag("recycle_item_restore_button")
                ) {
                    Icon(Icons.Default.Restore, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Restore")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        confirmPermanentItem = item
                        inspectItem = null
                    }) { Text("Delete Forever", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { inspectItem = null }) { Text("Close") }
                }
            }
        )
    }

    confirmPermanentItem?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmPermanentItem = null },
            title = { Text("Permanently Delete?") },
            text = { Text("\"${item.displayName}\" will be erased forever and cannot be recovered.") },
            confirmButton = {
                Button(onClick = {
                    onPermanentlyDelete(item)
                    confirmPermanentItem = null
                }) { Text("Delete Forever") }
            },
            dismissButton = {
                TextButton(onClick = { confirmPermanentItem = null }) { Text("Cancel") }
            }
        )
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text("Empty Recycle Bin?") },
            text = { Text("All ${trashItems.size} item(s) will be permanently erased and cannot be recovered.") },
            confirmButton = {
                Button(onClick = {
                    onEmptyAll()
                    showEmptyConfirm = false
                }) { Text("Empty All") }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
