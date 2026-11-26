package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.repository.MediaRepository
import com.example.domain.model.AccentPreset
import com.example.domain.model.AppSettings
import com.example.domain.model.MediaSortOrder
import com.example.domain.model.StorageBreakdown
import com.example.domain.model.ThemeMode
import com.example.domain.model.ThumbnailQuality
import com.example.ui.MediaAccessLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    accessLevel: MediaAccessLevel,
    storageBreakdown: StorageBreakdown,
    trashCount: Int,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentPresetChange: (AccentPreset) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onThumbnailQualityChange: (ThumbnailQuality) -> Unit,
    onAnimationsChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onDefaultSortChange: (MediaSortOrder) -> Unit,
    onVaultAutoLockChange: (Int) -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onTrashRetentionChange: (Int) -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenSmartTools: () -> Unit,
    onRequestMediaPermission: () -> Unit,
    onOpenAppSystemSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings", style = MaterialTheme.typography.headlineMedium) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance
            item("appearance") {
                SettingsSectionCard(title = "Appearance", icon = Icons.Default.Palette) {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("Accent Color", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AccentPreset.entries.forEach { preset ->
                            val selected = settings.accentPreset == preset
                            if (preset == AccentPreset.DYNAMIC) {
                                FilterChip(
                                    selected = selected,
                                    onClick = { onAccentPresetChange(preset) },
                                    label = { Text(preset.label) }
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(preset.hex))
                                        .border(
                                            width = if (selected) 2.5.dp else 0.dp,
                                            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { onAccentPresetChange(preset) }
                                        .testTag("settings_accent_${preset.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(Icons.Default.Check, contentDescription = preset.label, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("Grid Density", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3, 4, 5).forEach { cols ->
                            FilterChip(
                                selected = settings.gridColumns == cols,
                                onClick = { onGridColumnsChange(cols) },
                                label = { Text("$cols cols") },
                                modifier = Modifier.testTag("settings_grid_$cols")
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("Thumbnail Quality", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThumbnailQuality.entries.forEach { q ->
                            FilterChip(
                                selected = settings.thumbnailQuality == q,
                                onClick = { onThumbnailQualityChange(q) },
                                label = { Text(q.label) }
                            )
                        }
                    }

                    SettingsSwitchRow(
                        title = "Smooth Animations",
                        subtitle = "Spring scale and crossfade transitions",
                        checked = settings.animationsEnabled,
                        onCheckedChange = onAnimationsChange
                    )
                    SettingsSwitchRow(
                        title = "Haptic Feedback",
                        subtitle = "Subtle tactile ticks on selection and keypad",
                        checked = settings.hapticsEnabled,
                        onCheckedChange = onHapticsChange
                    )
                }
            }

            // Library, Sorting & Recycle Bin
            item("library") {
                SettingsSectionCard(title = "Library & Recycle Bin", icon = Icons.Default.DeleteSweep) {
                    Text("Default Sort Order", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MediaSortOrder.entries.forEach { sort ->
                            FilterChip(
                                selected = settings.defaultSort == sort,
                                onClick = { onDefaultSortChange(sort) },
                                label = { Text(sort.label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("Recycle Bin Retention", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 15, 30, 60).forEach { days ->
                            FilterChip(
                                selected = settings.trashRetentionDays == days,
                                onClick = { onTrashRetentionChange(days) },
                                label = { Text("${days}d") }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onOpenRecycleBin, modifier = Modifier.weight(1f)) {
                            Text("Recycle Bin ($trashCount)")
                        }
                        OutlinedButton(onClick = onOpenSmartTools, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Insights")
                        }
                    }
                }
            }

            // Vault Security
            item("vault_security") {
                SettingsSectionCard(title = "Private Vault Security", icon = Icons.Default.Lock) {
                    SettingsSwitchRow(
                        title = "Biometric Unlock",
                        subtitle = "Allow fingerprint or face unlock alongside your PIN",
                        checked = settings.biometricEnabled,
                        onCheckedChange = onBiometricChange
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Auto-Lock When Backgrounded", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            0 to "Immediate",
                            30 to "30 sec",
                            60 to "1 min",
                            300 to "5 min",
                            -1 to "Manual"
                        ).forEach { (sec, label) ->
                            FilterChip(
                                selected = settings.vaultAutoLockSeconds == sec,
                                onClick = { onVaultAutoLockChange(sec) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // Storage & Permissions
            item("permissions") {
                SettingsSectionCard(title = "Permissions & Storage", icon = Icons.Default.PhotoLibrary) {
                    Text(
                        text = "Media Access: ${
                            when (accessLevel) {
                                MediaAccessLevel.FULL -> "Full Photo & Video Access"
                                MediaAccessLevel.PARTIAL -> "Selected Items Only"
                                MediaAccessLevel.DENIED -> "Not Granted (Photo Picker Available)"
                            }
                        }",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Indexed Storage: ${MediaRepository.formatBytes(storageBreakdown.totalManagedBytes)} (${storageBreakdown.photosCount} photos, ${storageBreakdown.videosCount} videos)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onRequestMediaPermission, modifier = Modifier.weight(1f)) {
                            Text("Request Access")
                        }
                        OutlinedButton(onClick = onOpenAppSystemSettings, modifier = Modifier.weight(1f)) {
                            Text("System Settings")
                        }
                    }
                }
            }

            // About & Privacy
            item("about") {
                SettingsSectionCard(title = "About & Privacy", icon = Icons.Default.Shield) {
                    Text("M Gallery • Version 1.0", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.about_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
