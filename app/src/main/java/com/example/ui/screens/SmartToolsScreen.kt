package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.domain.model.DuplicateGroup
import com.example.domain.model.MediaItem
import com.example.domain.model.SmartAnalysisState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartToolsScreen(
    smartState: SmartAnalysisState,
    onBack: () -> Unit,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onOpenMedia: (MediaItem, List<MediaItem>) -> Unit,
    onDeleteItems: (List<MediaItem>) -> Unit
) {
    var confirmGroupCleanup by remember { mutableStateOf<DuplicateGroup?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Insights", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("smart_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Storage Usage Dashboard
            item("storage_card") {
                val b = smartState.breakdown
                val total = b.totalManagedBytes.coerceAtLeast(1L).toFloat()
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Storage Usage", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${MediaRepository.formatBytes(b.totalManagedBytes)} indexed across device & vault",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        // Stacked Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            val pWeight = (b.photosBytes / total).coerceAtLeast(0.01f)
                            val vWeight = (b.videosBytes / total).coerceAtLeast(0.01f)
                            val vaultWeight = (b.vaultBytes / total)
                            val trashWeight = (b.trashBytes / total)

                            Box(Modifier.weight(pWeight).fillMaxHeight().background(Color(0xFF8B5CF6)))
                            Box(Modifier.weight(vWeight).fillMaxHeight().background(Color(0xFF06B6D4)))
                            if (vaultWeight > 0.005f) {
                                Box(Modifier.weight(vaultWeight).fillMaxHeight().background(Color(0xFF10B981)))
                            }
                            if (trashWeight > 0.005f) {
                                Box(Modifier.weight(trashWeight).fillMaxHeight().background(Color(0xFFF59E0B)))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        StorageLegendRow("Photos (${b.photosCount})", MediaRepository.formatBytes(b.photosBytes), Color(0xFF8B5CF6))
                        StorageLegendRow("Videos (${b.videosCount})", MediaRepository.formatBytes(b.videosBytes), Color(0xFF06B6D4))
                        StorageLegendRow("Private Vault", MediaRepository.formatBytes(b.vaultBytes), Color(0xFF10B981))
                        StorageLegendRow("Recycle Bin", MediaRepository.formatBytes(b.trashBytes), Color(0xFFF59E0B))

                        if (b.topBuckets.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Largest Folders", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            b.topBuckets.forEach { (name, bytes) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(MediaRepository.formatBytes(bytes), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            // Duplicate & Similarity Scanner Trigger Card
            item("scanner_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Duplicates, Similar & Large Files", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Offline SHA-256 hash & perceptual structure scan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (smartState.isScanning) {
                            LinearProgressIndicator(
                                progress = { smartState.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(smartState.statusText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = onCancelScan) { Text("Cancel") }
                            }
                        } else {
                            Button(
                                onClick = onStartScan,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("smart_scan_button")
                            ) {
                                Text(if (smartState.hasCompletedScan) "Rescan Library" else "Scan Library Now")
                            }
                        }
                    }
                }
            }

            // Exact Duplicates
            if (smartState.hasCompletedScan) {
                item("exact_header") {
                    Text(
                        text = "Exact Duplicates (${smartState.exactDuplicates.size} groups)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (smartState.exactDuplicates.isEmpty()) {
                    item("exact_empty") {
                        Text("No exact duplicate files found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(smartState.exactDuplicates, key = { it.groupId }) { group ->
                        DuplicateGroupCard(
                            group = group,
                            actionLabel = "Trash Extra Copies (${group.items.size - 1})",
                            onOpenMedia = { m -> onOpenMedia(m, group.items) },
                            onAction = { confirmGroupCleanup = group }
                        )
                    }
                }

                // Visually Similar (kept clearly separate from exact duplicates)
                item("similar_header") {
                    Text(
                        text = "Visually Similar Photos (${smartState.visuallySimilar.size} groups)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (smartState.visuallySimilar.isEmpty()) {
                    item("similar_empty") {
                        Text("No visually similar burst groups detected.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(smartState.visuallySimilar, key = { it.groupId }) { group ->
                        DuplicateGroupCard(
                            group = group,
                            actionLabel = "Review & Trash Extras",
                            onOpenMedia = { m -> onOpenMedia(m, group.items) },
                            onAction = { confirmGroupCleanup = group }
                        )
                    }
                }

                // Large Files
                item("large_header") {
                    Text(
                        text = "Largest Files (${smartState.largeFiles.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(smartState.largeFiles.take(15), key = { "lg_${it.mediaKey}" }) { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenMedia(item, smartState.largeFiles) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = item.contentUri,
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${MediaRepository.formatBytes(item.sizeBytes)} • ${item.bucketName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteItems(listOf(item)) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Move to Recycle Bin", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    confirmGroupCleanup?.let { group ->
        val extras = group.items.drop(1)
        AlertDialog(
            onDismissRequest = { confirmGroupCleanup = null },
            title = { Text("Move ${extras.size} Copy/Copies to Recycle Bin?") },
            text = {
                Text(
                    "Keeps the oldest file (${group.items.firstOrNull()?.displayName}) and moves ${extras.size} other file(s) (${MediaRepository.formatBytes(group.reclaimableBytes)}) to the Recycle Bin."
                )
            },
            confirmButton = {
                Button(onClick = {
                    onDeleteItems(extras)
                    confirmGroupCleanup = null
                }) { Text("Move to Recycle Bin") }
            },
            dismissButton = {
                TextButton(onClick = { confirmGroupCleanup = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StorageLegendRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    actionLabel: String,
    onOpenMedia: (MediaItem) -> Unit,
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${group.items.size} items", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Save ${MediaRepository.formatBytes(group.reclaimableBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(group.items, key = { it.mediaKey }) { item ->
                    AsyncImage(
                        model = item.contentUri,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenMedia(item) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(actionLabel)
            }
        }
    }
}
