package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.editor.AspectRatioPreset
import com.example.domain.editor.FilterPreset
import com.example.domain.editor.NormalizedCropRect
import com.example.domain.editor.PhotoEditState
import com.example.ui.editor.EditorSaveResult
import com.example.ui.editor.PhotoEditorViewModel
import kotlin.math.abs

private enum class EditorToolTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    CROP("Crop", Icons.Default.Crop),
    TRANSFORM("Rotate", Icons.AutoMirrored.Filled.RotateRight),
    ADJUST("Adjust", Icons.Default.Tune),
    FILTERS("Filters", Icons.Default.AutoFixHigh)
}

private enum class AdjustParam(val label: String) {
    BRIGHTNESS("Brightness"),
    CONTRAST("Contrast"),
    SATURATION("Saturation"),
    WARMTH("Warmth"),
    VIGNETTE("Vignette")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    mediaUri: String,
    mediaName: String,
    viewModel: PhotoEditorViewModel,
    onSavedAndExit: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var activeTab by remember { mutableIntStateOf(0) }
    var activeAdjust by remember { mutableStateOf(AdjustParam.BRIGHTNESS) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showReplaceWarning by remember { mutableStateOf(false) }

    val writeConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            viewModel.executeOverwriteOriginal()
        }
    }

    LaunchedEffect(mediaUri) {
        viewModel.loadImage(mediaUri, mediaName)
    }

    LaunchedEffect(Unit) {
        viewModel.saveResults.collect { result ->
            when (result) {
                is EditorSaveResult.SavedCopy -> onSavedAndExit(result.message)
                is EditorSaveResult.OverwrittenOriginal -> onSavedAndExit("Replaced original photo")
                is EditorSaveResult.NeedsWriteConsent -> {
                    writeConsentLauncher.launch(IntentSenderRequest.Builder(result.pendingIntent).build())
                }
                is EditorSaveResult.Error -> {
                    snackbarHostState.showSnackbar(result.message)
                }
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF0B0C0E),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit", color = Color.White, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel editing", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = state.canUndo) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (state.canUndo) Color.White else Color.Gray
                        )
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = state.canRedo) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (state.canRedo) Color.White else Color.Gray
                        )
                    }
                    IconButton(
                        onClick = { viewModel.resetAll() },
                        enabled = !state.editState.isDefault
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = "Reset edits",
                            tint = if (!state.editState.isDefault) Color.White else Color.Gray
                        )
                    }
                    IconButton(
                        onClick = { showSaveDialog = true },
                        enabled = !state.isSaving && state.previewBitmap != null,
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save photo", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0C0E))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Image Canvas Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator()
                    state.errorMessage != null -> Text(state.errorMessage ?: "", color = Color.White)
                    else -> {
                        val displayBmp = if (state.isComparingOriginal) state.sourceBitmap else state.previewBitmap
                        if (displayBmp != null) {
                            BoxWithConstraints(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                val density = LocalDensity.current
                                val maxW = with(density) { maxWidth.toPx() }
                                val maxH = with(density) { maxHeight.toPx() }
                                val imgRatio = displayBmp.width.toFloat() / displayBmp.height.coerceAtLeast(1)
                                val boxRatio = maxW / maxH.coerceAtLeast(1f)
                                val (renderW, renderH) = if (imgRatio > boxRatio) {
                                    maxW to (maxW / imgRatio)
                                } else {
                                    (maxH * imgRatio) to maxH
                                }
                                val renderWDp = with(density) { renderW.toDp() }
                                val renderHDp = with(density) { renderH.toDp() }

                                Box(modifier = Modifier.size(renderWDp, renderHDp)) {
                                    Image(
                                        bitmap = displayBmp.asImageBitmap(),
                                        contentDescription = "Edited photo preview",
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (EditorToolTab.entries[activeTab] == EditorToolTab.CROP && !state.isComparingOriginal) {
                                        InteractiveCropOverlay(
                                            cropRect = state.editState.cropRect,
                                            aspectRatio = state.editState.aspectPreset.ratio,
                                            imageAspectRatio = imgRatio,
                                            onCropChange = { newRect ->
                                                viewModel.applyEdit(commitToHistory = false) { it.copy(cropRect = newRect) }
                                            },
                                            onCropCommit = { prevRect ->
                                                viewModel.pushUndoCheckpoint(state.editState.copy(cropRect = prevRect))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Hold-to-Compare Before/After pill
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            viewModel.setComparingOriginal(true)
                                            tryAwaitRelease()
                                            viewModel.setComparingOriginal(false)
                                        }
                                    )
                                }
                                .testTag("editor_compare_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Compare, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (state.isComparingOriginal) "Original" else "Hold to Compare",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                if (state.isSaving) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Saving high-resolution image...", color = Color.White)
                        }
                    }
                }
            }

            // Tool Controls Panel
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 12.dp)
                ) {
                    when (EditorToolTab.entries[activeTab]) {
                        EditorToolTab.CROP -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AspectRatioPreset.entries.forEach { preset ->
                                    FilterChip(
                                        selected = state.editState.aspectPreset == preset,
                                        onClick = { viewModel.setAspectRatio(preset) },
                                        label = { Text(preset.label) }
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.applyEdit {
                                            it.copy(
                                                cropRect = NormalizedCropRect(),
                                                aspectPreset = AspectRatioPreset.FREE
                                            )
                                        }
                                    }
                                ) { Text("Reset Crop") }
                            }
                        }

                        EditorToolTab.TRANSFORM -> {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    OutlinedButton(onClick = { viewModel.rotate90(clockwise = false) }) {
                                        Icon(Icons.AutoMirrored.Filled.RotateLeft, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("-90°")
                                    }
                                    OutlinedButton(onClick = { viewModel.rotate90(clockwise = true) }) {
                                        Icon(Icons.AutoMirrored.Filled.RotateRight, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("+90°")
                                    }
                                    OutlinedButton(onClick = { viewModel.toggleFlipHorizontal() }) {
                                        Icon(Icons.Default.Flip, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Flip H")
                                    }
                                    OutlinedButton(onClick = { viewModel.toggleFlipVertical() }) {
                                        Text("Flip V")
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                var checkpoint by remember { mutableStateOf<PhotoEditState?>(null) }
                                Text(
                                    "Straighten: ${"%.0f".format(state.editState.straightAngleDeg)}°",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = state.editState.straightAngleDeg,
                                    onValueChange = { deg ->
                                        if (checkpoint == null) checkpoint = state.editState
                                        viewModel.updateSliderPreview { it.copy(straightAngleDeg = deg) }
                                    },
                                    onValueChangeFinished = {
                                        checkpoint?.let { viewModel.pushUndoCheckpoint(it) }
                                        checkpoint = null
                                    },
                                    valueRange = -45f..45f
                                )
                            }
                        }

                        EditorToolTab.ADJUST -> {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AdjustParam.entries.forEach { param ->
                                        FilterChip(
                                            selected = activeAdjust == param,
                                            onClick = { activeAdjust = param },
                                            label = { Text(param.label) }
                                        )
                                    }
                                }
                                val (value, range, setter) = when (activeAdjust) {
                                    AdjustParam.BRIGHTNESS -> Triple(state.editState.brightness, -80f..80f) { v: Float ->
                                        viewModel.updateSliderPreview { it.copy(brightness = v) }
                                    }
                                    AdjustParam.CONTRAST -> Triple(state.editState.contrast, 0.5f..1.5f) { v: Float ->
                                        viewModel.updateSliderPreview { it.copy(contrast = v) }
                                    }
                                    AdjustParam.SATURATION -> Triple(state.editState.saturation, 0f..2f) { v: Float ->
                                        viewModel.updateSliderPreview { it.copy(saturation = v) }
                                    }
                                    AdjustParam.WARMTH -> Triple(state.editState.warmth, -1f..1f) { v: Float ->
                                        viewModel.updateSliderPreview { it.copy(warmth = v) }
                                    }
                                    AdjustParam.VIGNETTE -> Triple(state.editState.vignette, 0f..1f) { v: Float ->
                                        viewModel.updateSliderPreview { it.copy(vignette = v) }
                                    }
                                }
                                var checkpoint by remember { mutableStateOf<PhotoEditState?>(null) }
                                Slider(
                                    value = value,
                                    onValueChange = { v ->
                                        if (checkpoint == null) checkpoint = state.editState
                                        setter(v)
                                    },
                                    onValueChangeFinished = {
                                        checkpoint?.let { viewModel.pushUndoCheckpoint(it) }
                                        checkpoint = null
                                    },
                                    valueRange = range
                                )
                            }
                        }

                        EditorToolTab.FILTERS -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilterPreset.entries.forEach { preset ->
                                    val selected = state.editState.filterPreset == preset
                                    Surface(
                                        onClick = { viewModel.setFilter(preset) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .border(
                                                width = if (selected) 1.5.dp else 0.dp,
                                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                    ) {
                                        Text(
                                            text = preset.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Tool Selector Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        EditorToolTab.entries.forEachIndexed { idx, tab ->
                            val selected = activeTab == idx
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { activeTab = idx }
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .testTag("editor_tab_${tab.name.lowercase()}")
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Edited Photo") },
            text = {
                Text("By default, M Gallery preserves your original photo and saves your edits as a new copy in Pictures/M Gallery.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveDialog = false
                        viewModel.saveAsNewCopy()
                    },
                    modifier = Modifier.testTag("editor_save_copy_button")
                ) { Text("Save as New Copy") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showSaveDialog = false
                        showReplaceWarning = true
                    }) { Text("Replace Original") }
                    TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    if (showReplaceWarning) {
        AlertDialog(
            onDismissRequest = { showReplaceWarning = false },
            title = { Text("Overwrite Original Photo?") },
            text = {
                Text("This permanently replaces the original file with your edited version and cannot be undone once saved.")
            },
            confirmButton = {
                Button(onClick = {
                    showReplaceWarning = false
                    viewModel.requestReplaceOriginal()
                }) { Text("Overwrite Original") }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceWarning = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun InteractiveCropOverlay(
    cropRect: NormalizedCropRect,
    aspectRatio: Float?,
    imageAspectRatio: Float,
    onCropChange: (NormalizedCropRect) -> Unit,
    onCropCommit: (NormalizedCropRect) -> Unit
) {
    var activeHandle by remember { mutableIntStateOf(-1) } // 0:TL, 1:TR, 2:BL, 3:BR, 4:Move
    var startRect by remember { mutableStateOf(cropRect) }
    val primary = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(aspectRatio, imageAspectRatio) {
                detectDragGestures(
                    onDragStart = { offset ->
                        startRect = cropRect
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val l = cropRect.left * w
                        val t = cropRect.top * h
                        val r = cropRect.right * w
                        val b = cropRect.bottom * h
                        val tol = 44.dp.toPx()

                        activeHandle = when {
                            abs(offset.x - l) < tol && abs(offset.y - t) < tol -> 0
                            abs(offset.x - r) < tol && abs(offset.y - t) < tol -> 1
                            abs(offset.x - l) < tol && abs(offset.y - b) < tol -> 2
                            abs(offset.x - r) < tol && abs(offset.y - b) < tol -> 3
                            offset.x in l..r && offset.y in t..b -> 4
                            else -> -1
                        }
                    },
                    onDragEnd = {
                        if (activeHandle != -1) {
                            onCropCommit(startRect)
                        }
                        activeHandle = -1
                    },
                    onDrag = { change, dragAmount ->
                        if (activeHandle == -1) return@detectDragGestures
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val dx = dragAmount.x / w
                        val dy = dragAmount.y / h
                        val minSpan = 0.15f

                        var l = cropRect.left
                        var t = cropRect.top
                        var r = cropRect.right
                        var b = cropRect.bottom

                        when (activeHandle) {
                            0 -> {
                                l = (l + dx).coerceIn(0f, r - minSpan)
                                t = (t + dy).coerceIn(0f, b - minSpan)
                                aspectRatio?.let { ratio ->
                                    val relRatio = ratio / imageAspectRatio
                                    val desiredH = ((r - l) / relRatio).coerceIn(minSpan, b)
                                    t = (b - desiredH).coerceIn(0f, b - minSpan)
                                }
                            }
                            1 -> {
                                r = (r + dx).coerceIn(l + minSpan, 1f)
                                t = (t + dy).coerceIn(0f, b - minSpan)
                                aspectRatio?.let { ratio ->
                                    val relRatio = ratio / imageAspectRatio
                                    val desiredH = ((r - l) / relRatio).coerceIn(minSpan, b)
                                    t = (b - desiredH).coerceIn(0f, b - minSpan)
                                }
                            }
                            2 -> {
                                l = (l + dx).coerceIn(0f, r - minSpan)
                                b = (b + dy).coerceIn(t + minSpan, 1f)
                                aspectRatio?.let { ratio ->
                                    val relRatio = ratio / imageAspectRatio
                                    val desiredH = ((r - l) / relRatio).coerceIn(minSpan, 1f - t)
                                    b = (t + desiredH).coerceIn(t + minSpan, 1f)
                                }
                            }
                            3 -> {
                                r = (r + dx).coerceIn(l + minSpan, 1f)
                                b = (b + dy).coerceIn(t + minSpan, 1f)
                                aspectRatio?.let { ratio ->
                                    val relRatio = ratio / imageAspectRatio
                                    val desiredH = ((r - l) / relRatio).coerceIn(minSpan, 1f - t)
                                    b = (t + desiredH).coerceIn(t + minSpan, 1f)
                                }
                            }
                            4 -> {
                                val cw = r - l
                                val ch = b - t
                                l = (l + dx).coerceIn(0f, 1f - cw)
                                t = (t + dy).coerceIn(0f, 1f - ch)
                                r = l + cw
                                b = t + ch
                            }
                        }
                        onCropChange(NormalizedCropRect(l, t, r, b))
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val l = cropRect.left * w
        val t = cropRect.top * h
        val r = cropRect.right * w
        val b = cropRect.bottom * h
        val cw = r - l
        val ch = b - t

        // Dim outside crop bounds
        val shade = Color.Black.copy(alpha = 0.55f)
        drawRect(shade, Offset.Zero, Size(w, t))
        drawRect(shade, Offset(0f, b), Size(w, h - b))
        drawRect(shade, Offset(0f, t), Size(l, ch))
        drawRect(shade, Offset(r, t), Size(w - r, ch))

        // Crop border & rule-of-thirds grid
        drawRect(Color.White, Offset(l, t), Size(cw, ch), style = Stroke(width = 2.dp.toPx()))
        for (i in 1..2) {
            val gx = l + cw * (i / 3f)
            val gy = t + ch * (i / 3f)
            drawLine(Color.White.copy(alpha = 0.4f), Offset(gx, t), Offset(gx, b), strokeWidth = 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.4f), Offset(l, gy), Offset(r, gy), strokeWidth = 1.dp.toPx())
        }

        // Corner handles
        val handleRadius = 7.dp.toPx()
        listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach { pt ->
            drawCircle(primary, radius = handleRadius, center = pt)
            drawCircle(Color.White, radius = handleRadius, center = pt, style = Stroke(2.dp.toPx()))
        }
    }
}
