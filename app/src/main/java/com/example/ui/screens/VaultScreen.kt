package com.example.ui.screens

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.repository.MediaRepository
import com.example.domain.model.MediaItem
import com.example.domain.model.VaultItem
import com.example.ui.vault.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    vaultViewModel: VaultViewModel,
    allGalleryMedia: List<MediaItem>,
    biometricEnabledInSettings: Boolean,
    hapticsEnabled: Boolean,
    onOpenVaultVideo: (VaultItem) -> Unit,
    onRefreshGalleryAfterOriginalDelete: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by vaultViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var showImportSheet by remember { mutableStateOf(false) }
    var showVaultSettingsDialog by remember { mutableStateOf(false) }
    var showRecoveryResetDialog by remember { mutableStateOf(false) }
    var confirmDeleteTarget by remember { mutableStateOf<VaultItem?>(null) }
    var confirmExportTarget by remember { mutableStateOf<VaultItem?>(null) }

    val deleteOriginalsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            onRefreshGalleryAfterOriginalDelete()
            onShowSnackbar("Removed original item(s) from public gallery")
        }
    }

    LaunchedEffect(Unit) {
        vaultViewModel.events.collect { msg -> onShowSnackbar(msg) }
    }

    LaunchedEffect(Unit) {
        vaultViewModel.deleteOriginalsIntent.collect { pendingIntent ->
            deleteOriginalsLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
        }
    }

    val canUseBiometric = remember(context, biometricEnabledInSettings) {
        if (!biometricEnabledInSettings || activity == null) false
        else {
            val mgr = BiometricManager.from(context)
            mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    val launchBiometric = {
        if (activity != null) {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        vaultViewModel.unlockWithBiometrics()
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock M Gallery Vault")
                .setSubtitle("Verify your identity to access encrypted media")
                .setNegativeButtonText("Use PIN")
                .build()
            prompt.authenticate(info)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            !state.isConfigured -> {
                VaultSetupView(
                    isBusy = state.isBusy,
                    errorMessage = state.errorMessage,
                    onSetupPin = { pin -> vaultViewModel.setupPin(pin) }
                )
            }

            !state.isUnlocked -> {
                VaultLockView(
                    canUseBiometric = canUseBiometric,
                    hapticsEnabled = hapticsEnabled,
                    errorMessage = state.errorMessage,
                    onSubmitPin = { pin -> vaultViewModel.unlockWithPin(pin) },
                    onBiometricClick = launchBiometric,
                    onForgotPin = { showRecoveryResetDialog = true }
                )
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopAppBar(
                            title = {
                                if (state.selectedIds.isNotEmpty()) {
                                    Text("${state.selectedIds.size} selected")
                                } else {
                                    Column {
                                        Text("Private Vault", style = MaterialTheme.typography.headlineSmall)
                                        Text(
                                            "AES-256-GCM Encrypted • ${state.items.size} items",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                if (state.selectedIds.isNotEmpty()) {
                                    IconButton(onClick = { vaultViewModel.clearSelection() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear selection")
                                    }
                                }
                            },
                            actions = {
                                if (state.selectedIds.isNotEmpty()) {
                                    IconButton(onClick = { vaultViewModel.deleteSelectedVaultItems() }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                                    }
                                } else {
                                    IconButton(
                                        onClick = { showVaultSettingsDialog = true },
                                        modifier = Modifier.testTag("vault_security_settings_button")
                                    ) {
                                        Icon(Icons.Default.Key, contentDescription = "Vault PIN & Recovery")
                                    }
                                    IconButton(
                                        onClick = { vaultViewModel.lockVault() },
                                        modifier = Modifier.testTag("vault_lock_now_button")
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = "Lock Vault")
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )

                        if (state.items.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Your Private Vault is empty", style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Imported photos and videos are encrypted with Android Keystore AES-256-GCM. Original files are never deleted without your confirmation.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                Button(
                                    onClick = { showImportSheet = true },
                                    modifier = Modifier.testTag("vault_import_empty_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import Media")
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(state.items, key = { it.id }) { vaultItem ->
                                    val thumbBytes = state.decryptedThumbnails[vaultItem.id]
                                    val thumbBmp = remember(thumbBytes) {
                                        thumbBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                                    }
                                    val isSelected = vaultItem.id in state.selectedIds

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
                                                    if (state.selectedIds.isNotEmpty()) {
                                                        vaultViewModel.toggleSelect(vaultItem.id)
                                                    } else if (vaultItem.isVideo) {
                                                        onOpenVaultVideo(vaultItem)
                                                    } else {
                                                        vaultViewModel.openVaultPhoto(vaultItem)
                                                    }
                                                },
                                                onLongClick = { vaultViewModel.toggleSelect(vaultItem.id) }
                                            )
                                    ) {
                                        if (thumbBmp != null) {
                                            Image(
                                                bitmap = thumbBmp.asImageBitmap(),
                                                contentDescription = vaultItem.originalName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        if (vaultItem.isVideo) {
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.65f),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                    Spacer(Modifier.width(2.dp))
                                                    Text(
                                                        MediaRepository.formatDuration(vaultItem.durationMs),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                        if (state.selectedIds.isNotEmpty()) {
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

                    FloatingActionButton(
                        onClick = { showImportSheet = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp)
                            .testTag("vault_import_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Import to Private Vault")
                    }

                    if (state.isBusy) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    // One-time Recovery Key Backup Dialog
    state.generatedRecoveryKey?.let { key ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Save Your Recovery Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Your Private Vault is encrypted with hardware-backed keys. If you forget your PIN AND lose this recovery key, your encrypted vault media cannot be recovered.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { vaultViewModel.dismissRecoveryKeyDialog() },
                    modifier = Modifier.testTag("vault_recovery_saved_button")
                ) { Text("I've Saved It") }
            }
        )
    }

    // Post-Import Prompt to optionally remove originals
    if (state.pendingOriginalsAfterImport.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vaultViewModel.dismissOriginalsPrompt() },
            title = { Text("Encrypted Copy Verified") },
            text = {
                Text("Successfully encrypted ${state.pendingOriginalsAfterImport.size} item(s) inside your Private Vault. Would you like to remove the original unencrypted copy from your public gallery?")
            },
            confirmButton = {
                Button(onClick = { vaultViewModel.confirmDeleteOriginalsAfterImport() }) {
                    Text("Remove Originals")
                }
            },
            dismissButton = {
                TextButton(onClick = { vaultViewModel.dismissOriginalsPrompt() }) {
                    Text("Keep Originals")
                }
            }
        )
    }

    // Decrypted In-Memory Full-Screen Photo Viewer
    val activeBytes = state.activePhotoBytes
    val activeItem = state.activePhotoItem
    if (activeBytes != null && activeItem != null) {
        val bmp = remember(activeBytes) { BitmapFactory.decodeByteArray(activeBytes, 0, activeBytes.size) }
        Dialog(
            onDismissRequest = { vaultViewModel.closeVaultPhoto() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = activeItem.originalName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vaultViewModel.closeVaultPhoto() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(activeItem.originalName, color = Color.White, modifier = Modifier.weight(1f))
                    IconButton(onClick = { confirmExportTarget = activeItem }) {
                        Icon(Icons.Default.Download, contentDescription = "Export to gallery", tint = Color.White)
                    }
                    IconButton(onClick = { confirmDeleteTarget = activeItem }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete from vault", tint = Color(0xFFF87171))
                    }
                }
            }
        }
    }

    // Import Media Sheet
    if (showImportSheet) {
        var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
        ModalBottomSheet(onDismissRequest = { showImportSheet = false }) {
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
                    Text("Encrypt into Vault (${picked.size})", style = MaterialTheme.typography.titleLarge)
                    Button(
                        onClick = {
                            val selected = allGalleryMedia.filter { it.mediaKey in picked }
                            vaultViewModel.importMediaItems(selected)
                            showImportSheet = false
                        },
                        enabled = picked.isNotEmpty()
                    ) { Text("Encrypt") }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (allGalleryMedia.isEmpty()) {
                    Text(
                        "No gallery items indexed yet. Grant media access or import items via the Photo Picker in the Photos tab first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.height(340.dp)
                    ) {
                        items(allGalleryMedia, key = { it.mediaKey }) { item ->
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
    }

    confirmExportTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmExportTarget = null },
            title = { Text("Export to Public Gallery?") },
            text = { Text("This writes an unencrypted copy of \"${item.originalName}\" to Pictures/M Gallery so other apps can view it.") },
            confirmButton = {
                Button(onClick = {
                    vaultViewModel.exportVaultItem(item)
                    confirmExportTarget = null
                }) { Text("Export Copy") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExportTarget = null }) { Text("Cancel") }
            }
        )
    }

    confirmDeleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDeleteTarget = null },
            title = { Text("Delete from Private Vault?") },
            text = { Text("\"${item.originalName}\" will be securely erased from the encrypted vault. This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    vaultViewModel.closeVaultPhoto()
                    vaultViewModel.deleteVaultItem(item)
                    confirmDeleteTarget = null
                }) { Text("Delete Permanently") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (showVaultSettingsDialog) {
        var currentPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showVaultSettingsDialog = false },
            title = { Text("Vault PIN & Recovery") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) currentPin = it },
                        label = { Text("Current PIN") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPin = it },
                        label = { Text("New PIN (4–6 digits)") },
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = {
                            showVaultSettingsDialog = false
                            vaultViewModel.regenerateRecoveryKey()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Regenerate Recovery Key") }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vaultViewModel.changePin(currentPin, newPin)
                        showVaultSettingsDialog = false
                    },
                    enabled = currentPin.length >= 4 && newPin.length >= 4
                ) { Text("Update PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showVaultSettingsDialog = false }) { Text("Close") }
            }
        )
    }

    if (showRecoveryResetDialog) {
        var recoveryInput by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRecoveryResetDialog = false },
            title = { Text("Reset PIN with Recovery Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter the 24-character recovery key generated when you set up your Vault.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = recoveryInput,
                        onValueChange = { recoveryInput = it },
                        label = { Text("Recovery Key") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPin = it },
                        label = { Text("New PIN (4–6 digits)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vaultViewModel.recoverWithKey(recoveryInput, newPin)
                        showRecoveryResetDialog = false
                    },
                    enabled = recoveryInput.isNotBlank() && newPin.length >= 4
                ) { Text("Reset & Unlock") }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun VaultSetupView(
    isBusy: Boolean,
    errorMessage: String?,
    onSetupPin: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(68.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.LockOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Set Up Private Vault", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Protect sensitive photos & videos with Android Keystore AES-256-GCM encryption and a local PIN.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
            label = { Text("Create 4–6 digit PIN") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vault_setup_pin_input")
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it },
            label = { Text("Confirm PIN") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vault_setup_confirm_pin_input")
        )

        (localError ?: errorMessage)?.let { err ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = {
                when {
                    pin.length !in 4..6 -> localError = "PIN must be 4 to 6 digits"
                    pin != confirmPin -> localError = "PINs do not match"
                    else -> {
                        localError = null
                        onSetupPin(pin)
                    }
                }
            },
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vault_setup_submit_button")
        ) {
            Text("Create Encrypted Vault")
        }
    }
}

@Composable
private fun VaultLockView(
    canUseBiometric: Boolean,
    hapticsEnabled: Boolean,
    errorMessage: String?,
    onSubmitPin: (String) -> Unit,
    onBiometricClick: () -> Unit,
    onForgotPin: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Private Vault Locked", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Enter your PIN or authenticate with biometrics",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(22.dp))

        // PIN Dots
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) { idx ->
                val filled = idx < enteredPin.length
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = errorMessage ?: " ",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Keypad
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("BIO", "0", "DEL")
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    row.forEach { key ->
                        when (key) {
                            "BIO" -> {
                                Surface(
                                    onClick = { if (canUseBiometric) onBiometricClick() },
                                    enabled = canUseBiometric,
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (canUseBiometric) {
                                            Icon(Icons.Default.Fingerprint, contentDescription = "Biometric unlock", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                            "DEL" -> {
                                Surface(
                                    onClick = {
                                        if (enteredPin.isNotEmpty()) {
                                            enteredPin = enteredPin.dropLast(1)
                                        }
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
                                    }
                                }
                            }
                            else -> {
                                Surface(
                                    onClick = {
                                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (enteredPin.length < 6) {
                                            enteredPin += key
                                        }
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .size(68.dp)
                                        .testTag("vault_pin_key_$key")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(key, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onForgotPin) {
                Text("Forgot PIN?")
            }
            Button(
                onClick = { onSubmitPin(enteredPin) },
                enabled = enteredPin.length >= 4,
                modifier = Modifier.testTag("vault_unlock_button")
            ) {
                Text("Unlock")
            }
        }
    }
}
