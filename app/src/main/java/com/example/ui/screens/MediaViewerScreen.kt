package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.repository.MediaRepository
import com.example.domain.model.Album
import com.example.domain.model.MediaExifInfo
import com.example.domain.model.MediaItem
import com.example.domain.model.MediaType
import com.example.ui.components.AlbumPickerBottomSheet
import com.example.ui.components.ExifInfoBottomSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MediaViewerScreen(
    items: List<MediaItem>,
    initialMediaKey: String,
    userAlbums: List<Album>,
    onBack: () -> Unit,
    onViewed: (MediaItem) -> Unit,
    onLoadExif: suspend (MediaItem) -> MediaExifInfo,
    onToggleFavorite: (MediaItem) -> Unit,
    onOpenVideoPlayer: (MediaItem) -> Unit,
    onOpenPhotoEditor: (MediaItem) -> Unit,
    onShareItem: (MediaItem) -> Unit,
    onAddItemToAlbum: (String, MediaItem) -> Unit,
    onCreateAlbumWithItem: (String, MediaItem) -> Unit,
    onMoveToVault: (MediaItem) -> Unit,
    onDeleteItem: (MediaItem) -> Unit
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val startIndex = remember(items, initialMediaKey) {
        items.indexOfFirst { it.mediaKey == initialMediaKey }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { items.size })
    val currentItem = items.getOrNull(pagerState.currentPage) ?: items.first()

    var showControls by remember { mutableStateOf(true) }
    var isCurrentZoomed by remember { mutableStateOf(false) }
    var showExifSheet by remember { mutableStateOf(false) }
    var exifInfo by remember { mutableStateOf<MediaExifInfo?>(null) }
    var showAlbumPicker by remember { mutableStateOf(false) }

    LaunchedEffect(currentItem.mediaKey) {
        onViewed(currentItem)
        exifInfo = null
    }

    LaunchedEffect(showExifSheet, currentItem.mediaKey) {
        if (showExifSheet) {
            exifInfo = onLoadExif(currentItem)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isCurrentZoomed,
            key = { idx -> items[idx].mediaKey },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items[page]
            if (item.mediaType == MediaType.VIDEO) {
                VideoPreviewPage(
                    item = item,
                    onTapBackground = { showControls = !showControls },
                    onPlayClick = { onOpenVideoPlayer(item) }
                )
            } else {
                ZoomablePhotoPage(
                    item = item,
                    isActivePage = page == pagerState.currentPage,
                    onZoomChanged = { zoomed ->
                        if (page == pagerState.currentPage) isCurrentZoomed = zoomed
                    },
                    onSingleTap = { showControls = !showControls }
                )
            }
        }

        // Top HUD
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val dateFmt = remember { SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()) }
            val dateLabel = if (currentItem.effectiveTimestampMs > 0L) {
                dateFmt.format(Date(currentItem.effectiveTimestampMs))
            } else ""

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("viewer_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentItem.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (dateLabel.isNotBlank()) {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
                IconButton(
                    onClick = { showExifSheet = true },
                    modifier = Modifier.testTag("viewer_info_button")
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Photo details", tint = Color.White)
                }
            }
        }

        // Bottom HUD
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ViewerActionButton(
                    icon = if (currentItem.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    label = "Favorite",
                    tint = if (currentItem.isFavorite) Color(0xFFF43F5E) else Color.White,
                    tag = "viewer_favorite_button",
                    onClick = { onToggleFavorite(currentItem) }
                )
                if (currentItem.mediaType == MediaType.PHOTO) {
                    ViewerActionButton(
                        icon = Icons.Default.Edit,
                        label = "Edit",
                        tag = "viewer_edit_button",
                        onClick = { onOpenPhotoEditor(currentItem) }
                    )
                }
                ViewerActionButton(
                    icon = Icons.Default.Share,
                    label = "Share",
                    tag = "viewer_share_button",
                    onClick = { onShareItem(currentItem) }
                )
                ViewerActionButton(
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    label = "Album",
                    tag = "viewer_album_button",
                    onClick = { showAlbumPicker = true }
                )
                ViewerActionButton(
                    icon = Icons.Default.Lock,
                    label = "Vault",
                    tag = "viewer_vault_button",
                    onClick = { onMoveToVault(currentItem) }
                )
                ViewerActionButton(
                    icon = Icons.Default.DeleteOutline,
                    label = "Trash",
                    tint = Color(0xFFF87171),
                    tag = "viewer_delete_button",
                    onClick = { onDeleteItem(currentItem) }
                )
            }
        }
    }

    if (showExifSheet) {
        ExifInfoBottomSheet(
            item = currentItem,
            exifInfo = exifInfo,
            onDismiss = { showExifSheet = false }
        )
    }

    if (showAlbumPicker) {
        AlbumPickerBottomSheet(
            userAlbums = userAlbums,
            onPickAlbum = { albumId -> onAddItemToAlbum(albumId, currentItem) },
            onCreateNewAlbum = { name -> onCreateAlbumWithItem(name, currentItem) },
            onDismiss = { showAlbumPicker = false }
        )
    }
}

@Composable
private fun ZoomablePhotoPage(
    item: MediaItem,
    isActivePage: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onSingleTap: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember(item.mediaKey) { mutableFloatStateOf(1f) }
    var offset by remember(item.mediaKey) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(isActivePage) {
        if (!isActivePage) {
            scale = 1f
            offset = Offset.Zero
            onZoomChanged(false)
        }
    }

    val request = remember(item.contentUri) {
        ImageRequest.Builder(context)
            .data(Uri.parse(item.contentUri))
            .size(2048)
            .crossfade(false)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(item.mediaKey) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChanged(false)
                        } else {
                            scale = 2.6f
                            val cx = containerSize.width / 2f
                            val cy = containerSize.height / 2f
                            offset = Offset(
                                x = (cx - tapOffset.x) * 1.4f,
                                y = (cy - tapOffset.y) * 1.4f
                            )
                            onZoomChanged(true)
                        }
                    }
                )
            }
            .pointerInput(item.mediaKey) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    if (newScale <= 1.02f) {
                        scale = 1f
                        offset = Offset.Zero
                        onZoomChanged(false)
                    } else {
                        val maxX = (containerSize.width * (newScale - 1f)) / 2f
                        val maxY = (containerSize.height * (newScale - 1f)) / 2f
                        scale = newScale
                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                        )
                        onZoomChanged(true)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = request,
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

@Composable
private fun VideoPreviewPage(
    item: MediaItem,
    onTapBackground: () -> Unit,
    onPlayClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onTapBackground),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = item.contentUri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                onClick = onPlayClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                modifier = Modifier
                    .size(72.dp)
                    .testTag("viewer_play_video_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
            if (item.durationMs > 0L) {
                Spacer(modifier = Modifier.size(10.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "${MediaRepository.formatDuration(item.durationMs)} • ${MediaRepository.formatBytes(item.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tag: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(tag)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.size(3.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}
