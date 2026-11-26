package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.repository.MediaRepository
import com.example.domain.model.Album
import com.example.domain.model.GalleryViewMode
import com.example.domain.model.MediaExifInfo
import com.example.domain.model.MediaItem
import com.example.domain.model.MediaSortOrder
import com.example.domain.model.MediaType
import com.example.domain.model.ThumbnailQuality

@Composable
fun MGalleryWordmark(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val onBg = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(30.dp)) {
            val w = size.width
            val h = size.height
            drawRoundRect(
                brush = Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.55f))),
                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                size = Size(w - 2.dp.toPx(), h - 2.dp.toPx()),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
            val mPath = Path().apply {
                moveTo(w * 0.24f, h * 0.72f)
                lineTo(w * 0.24f, h * 0.30f)
                lineTo(w * 0.50f, h * 0.56f)
                lineTo(w * 0.76f, h * 0.30f)
                lineTo(w * 0.76f, h * 0.72f)
            }
            drawPath(
                path = mPath,
                color = onBg,
                style = Stroke(
                    width = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "M Gallery",
            style = MaterialTheme.typography.headlineSmall,
            color = onBg
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridThumbnail(
    item: MediaItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    thumbnailQuality: ThumbnailQuality,
    animationsEnabled: Boolean,
    hapticsEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFavoriteToggle: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val targetScale = if (isSelected) 0.91f else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (animationsEnabled) spring(stiffness = 480f) else tween(0),
        label = "thumbScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "thumbBorder"
    )

    val imageRequest = remember(item.contentUri, thumbnailQuality) {
        ImageRequest.Builder(context)
            .data(Uri.parse(item.contentUri))
            .size(thumbnailQuality.maxPx)
            .crossfade(animationsEnabled)
            .build()
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (isSelected) 2.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .combinedClickable(
                onClick = {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Video duration pill
        if (item.mediaType == MediaType.VIDEO) {
            Surface(
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = MediaRepository.formatDuration(item.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }

        // Favorite badge (optionally interactive when onFavoriteToggle is supplied)
        if ((item.isFavorite || onFavoriteToggle != null) && !isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .then(
                        if (onFavoriteToggle != null) {
                            Modifier
                                .clickable {
                                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onFavoriteToggle()
                                }
                                .testTag("favorite_toggle_${item.mediaKey}")
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (item.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (item.isFavorite) Color(0xFFF43F5E) else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Selection indicator
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.45f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaListRow(
    item: MediaItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val request = remember(item.contentUri) {
        ImageRequest.Builder(context)
            .data(Uri.parse(item.contentUri))
            .size(240)
            .build()
    }
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.mediaType == MediaType.VIDEO) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(22.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val dims = if (item.width > 0 && item.height > 0) "${item.width}×${item.height} • " else ""
                val dur = if (item.mediaType == MediaType.VIDEO && item.durationMs > 0L) {
                    "${MediaRepository.formatDuration(item.durationMs)} • "
                } else ""
                Text(
                    text = "$dims$dur${MediaRepository.formatBytes(item.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item.bucketName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.action_favorite),
                        tint = if (item.isFavorite) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SkeletonMediaGrid(columns: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(4) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(columns) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionAndEmptyStateCard(
    needsPermission: Boolean,
    isPartialAccess: Boolean,
    onGrantPermission: () -> Unit,
    onPickWithPhotoPicker: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_gallery_empty),
                contentDescription = "M Gallery media state illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when {
                    needsPermission -> stringResource(R.string.permission_title)
                    isPartialAccess -> "Limited Media Access Active"
                    else -> stringResource(R.string.empty_gallery_title)
                },
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    needsPermission -> stringResource(R.string.permission_description)
                    isPartialAccess -> "You granted access to selected items. You can allow more items, grant full access, or import via the Photo Picker."
                    else -> stringResource(R.string.empty_gallery_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (needsPermission || isPartialAccess) {
                Button(
                    onClick = onGrantPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_media_permission_button")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isPartialAccess) "Update Media Access" else "Allow Media Access")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = onPickWithPhotoPicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("photo_picker_import_button")
            ) {
                Icon(Icons.Default.Collections, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import via Photo Picker")
            }
            if (needsPermission) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onOpenSystemSettings,
                    modifier = Modifier.testTag("open_system_settings_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_open_settings))
                }
            }
        }
    }
}

@Composable
fun SelectionBottomActionBar(
    selectedCount: Int,
    onFavorite: () -> Unit,
    onAddToAlbum: () -> Unit,
    onMoveToVault: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    allSelectedAreFavorites: Boolean = false
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectionActionItem(
                icon = if (allSelectedAreFavorites) Icons.Default.FavoriteBorder else Icons.Default.Favorite,
                label = if (allSelectedAreFavorites) "Unfavorite" else "Favorite",
                onClick = onFavorite,
                tag = "selection_favorite"
            )
            SelectionActionItem(Icons.AutoMirrored.Filled.DriveFileMove, "Album", onAddToAlbum, "selection_album")
            SelectionActionItem(Icons.Default.Lock, "Vault", onMoveToVault, "selection_vault")
            SelectionActionItem(Icons.Default.Share, "Share", onShare, "selection_share")
            SelectionActionItem(Icons.Default.DeleteOutline, "Trash", onDelete, "selection_delete", isDestructive = true)
        }
    }
}

@Composable
private fun SelectionActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String,
    isDestructive: Boolean = false
) {
    val tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(tag)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortAndLayoutSheet(
    currentSort: MediaSortOrder,
    viewMode: GalleryViewMode,
    gridColumns: Int,
    onSortSelected: (MediaSortOrder) -> Unit,
    onViewModeSelected: (GalleryViewMode) -> Unit,
    onGridColumnsSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("View & Sort", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(14.dp))

            Text("Layout", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = viewMode == GalleryViewMode.GRID,
                    onClick = { onViewModeSelected(GalleryViewMode.GRID) },
                    label = { Text("Grid") },
                    leadingIcon = { Icon(Icons.Default.GridView, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = viewMode == GalleryViewMode.LIST,
                    onClick = { onViewModeSelected(GalleryViewMode.LIST) },
                    label = { Text("List") },
                    leadingIcon = { Icon(Icons.Default.ViewList, null, Modifier.size(16.dp)) }
                )
            }

            if (viewMode == GalleryViewMode.GRID) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Grid Columns", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4, 5).forEach { cols ->
                        FilterChip(
                            selected = gridColumns == cols,
                            onClick = { onGridColumnsSelected(cols) },
                            label = { Text("$cols col") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(14.dp))
            Text("Sort By", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))

            MediaSortOrder.entries.forEach { order ->
                val selected = order == currentSort
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onSortSelected(order)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = order.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPickerBottomSheet(
    userAlbums: List<Album>,
    onPickAlbum: (String) -> Unit,
    onCreateNewAlbum: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newAlbumName by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("Add to Album", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newAlbumName,
                    onValueChange = { newAlbumName = it },
                    placeholder = { Text("New album name...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        if (newAlbumName.isNotBlank()) {
                            onCreateNewAlbum(newAlbumName.trim())
                            onDismiss()
                        }
                    },
                    enabled = newAlbumName.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Create")
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            if (userAlbums.isEmpty()) {
                Text(
                    text = "No custom albums yet. Create one above to organize without duplicating files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.height(240.dp)
                ) {
                    items(userAlbums, key = { it.id }) { album ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onPickAlbum(album.id)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(album.title, style = MaterialTheme.typography.titleMedium)
                                    Text("${album.itemCount} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExifInfoBottomSheet(
    item: MediaItem,
    exifInfo: MediaExifInfo?,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(item.displayName, style = MaterialTheme.typography.titleLarge)
            if (exifInfo == null) {
                Text("Loading metadata...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                InfoRow("Captured", exifInfo.captureDateFormatted)
                InfoRow("Resolution", buildString {
                    append(exifInfo.resolution)
                    exifInfo.megapixels?.let { append(" • $it") }
                })
                InfoRow("File Size", "${exifInfo.fileSizeFormatted} (${exifInfo.mimeType})")
                exifInfo.durationFormatted?.let { InfoRow("Duration", it) }
                InfoRow("Location", exifInfo.folderPath)
                exifInfo.cameraMakeModel?.let { InfoRow("Camera", it) }
                val cameraStats = listOfNotNull(
                    exifInfo.focalLength,
                    exifInfo.aperture,
                    exifInfo.shutterSpeed,
                    exifInfo.iso
                ).joinToString(" • ")
                if (cameraStats.isNotBlank()) {
                    InfoRow("Exposure", cameraStats)
                }
                exifInfo.locationLatLong?.let { InfoRow("GPS Coordinates", it) }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
