package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.repository.MediaRepository
import com.example.data.security.EncryptedVaultDataSource
import com.example.data.security.VaultCryptoManager
import kotlinx.coroutines.delay
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUriString: String,
    videoTitle: String,
    isEncryptedVaultVideo: Boolean,
    vaultDir: File,
    cryptoManager: VaultCryptoManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentPosMs by rememberSaveable { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var isMuted by rememberSaveable { mutableStateOf(false) }
    var volumeLevel by rememberSaveable { mutableFloatStateOf(1f) }
    var playbackSpeed by rememberSaveable { mutableFloatStateOf(1f) }
    var resizeModeIndex by rememberSaveable { mutableIntStateOf(0) }
    var isLandscapeForced by rememberSaveable { mutableStateOf(false) }
    var showHud by remember { mutableStateOf(true) }

    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Fill",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch"
    )

    // Restore orientation on exit
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val exoPlayer = remember(videoUriString, isEncryptedVaultVideo) {
        val dataSourceFactory = if (isEncryptedVaultVideo) {
            EncryptedVaultDataSource.Factory(vaultDir, cryptoManager)
        } else {
            DefaultDataSource.Factory(context)
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(videoUriString)))
                seekTo(currentPosMs)
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    playbackError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playbackError = error.localizedMessage ?: "Unsupported or damaged video file."
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            currentPosMs = exoPlayer.currentPosition
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Periodic progress polling
    LaunchedEffect(exoPlayer, isPlaying, isScrubbing) {
        while (true) {
            if (!isScrubbing) {
                currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration
                if (dur > 0L) durationMs = dur
            }
            delay(250L)
        }
    }

    // Auto-hide HUD while playing
    LaunchedEffect(showHud, isPlaying) {
        if (showHud && isPlaying) {
            delay(3500L)
            showHud = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showHud = !showHud }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.resizeMode = resizeModes[resizeModeIndex].first
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && playbackError == null) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Error Fallback
        playbackError?.let { err ->
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Playback Error", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(err, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            playbackError = null
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }) { Text("Retry") }
                        if (!isEncryptedVaultVideo) {
                            OutlinedButton(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(videoUriString), "video/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(intent) }
                            }) { Text("External Player") }
                        }
                    }
                }
            }
        }

        // Custom Media3 HUD
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent))
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("player_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = videoTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                    }) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Resize mode", tint = Color.White)
                    }
                    IconButton(onClick = {
                        isLandscapeForced = !isLandscapeForced
                        activity?.requestedOrientation = if (isLandscapeForced) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }) {
                        Icon(Icons.Default.ScreenRotation, contentDescription = "Toggle landscape", tint = Color.White)
                    }
                }

                // Center Play / Seek 10s Controls
                if (playbackError == null) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                currentPosMs = target
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10 seconds", tint = Color.White, modifier = Modifier.size(30.dp))
                        }

                        Surface(
                            onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("player_play_pause_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(durationMs.coerceAtLeast(0L))
                                exoPlayer.seekTo(target)
                                currentPosMs = target
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }

                // Bottom Scrub & Audio Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    val activePos = if (isScrubbing) scrubPositionMs else currentPosMs
                    val maxSlider = durationMs.coerceAtLeast(1L).toFloat()

                    Slider(
                        value = activePos.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
                        onValueChange = { value ->
                            isScrubbing = true
                            scrubPositionMs = value.toLong()
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(scrubPositionMs)
                            currentPosMs = scrubPositionMs
                            isScrubbing = false
                        },
                        valueRange = 0f..maxSlider,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_seek_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${MediaRepository.formatDuration(activePos)} / ${MediaRepository.formatDuration(durationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else volumeLevel
                            }) {
                                Icon(
                                    imageVector = if (isMuted || volumeLevel == 0f)
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Mute toggle",
                                    tint = Color.White
                                )
                            }
                            Slider(
                                value = if (isMuted) 0f else volumeLevel,
                                onValueChange = { v ->
                                    volumeLevel = v
                                    isMuted = v == 0f
                                    exoPlayer.volume = v
                                },
                                modifier = Modifier.width(86.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                onClick = {
                                    val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
                                    val nextIdx = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
                                    playbackSpeed = speeds[nextIdx]
                                    exoPlayer.setPlaybackSpeed(playbackSpeed)
                                },
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "${playbackSpeed}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
