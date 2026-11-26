package com.example.ui.vault

import android.app.PendingIntent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.domain.model.MediaItem
import com.example.domain.model.VaultItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VaultUiState(
    val isConfigured: Boolean = false,
    val isUnlocked: Boolean = false,
    val items: List<VaultItem> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val generatedRecoveryKey: String? = null,
    val pendingOriginalsAfterImport: List<MediaItem> = emptyList(),
    val decryptedThumbnails: Map<String, ByteArray> = emptyMap(),
    val activePhotoBytes: ByteArray? = null,
    val activePhotoItem: VaultItem? = null
)

class VaultViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val repo = container.albumAndVaultRepository
    private val mediaRepo = container.mediaRepository

    private val _uiState = MutableStateFlow(
        VaultUiState(isConfigured = repo.isVaultConfigured())
    )
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _deleteOriginalsIntent = MutableSharedFlow<PendingIntent>()
    val deleteOriginalsIntent: SharedFlow<PendingIntent> = _deleteOriginalsIntent.asSharedFlow()

    private var backgroundTimestampMs: Long = 0L

    init {
        viewModelScope.launch {
            repo.vaultItems.collect { items ->
                _uiState.update { it.copy(items = items) }
                if (_uiState.value.isUnlocked) {
                    preloadThumbnails(items)
                }
            }
        }
    }

    fun setupPin(pin: String) {
        if (pin.length !in 4..6) {
            _uiState.update { it.copy(errorMessage = "PIN must be 4 to 6 digits") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val recoveryKey = repo.setupVaultPin(pin)
            _uiState.update {
                it.copy(
                    isConfigured = true,
                    isUnlocked = true,
                    isBusy = false,
                    generatedRecoveryKey = recoveryKey
                )
            }
        }
    }

    fun dismissRecoveryKeyDialog() {
        _uiState.update { it.copy(generatedRecoveryKey = null) }
    }

    fun unlockWithPin(pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val ok = repo.verifyVaultPin(pin)
            if (ok) {
                _uiState.update { it.copy(isUnlocked = true, isBusy = false, errorMessage = null) }
            } else {
                _uiState.update { it.copy(isUnlocked = false, isBusy = false, errorMessage = "Incorrect PIN") }
            }
        }
    }

    fun unlockWithBiometrics() {
        viewModelScope.launch {
            repo.reloadVaultOnBiometricUnlock()
            _uiState.update { it.copy(isUnlocked = true, errorMessage = null) }
        }
    }

    fun recoverWithKey(recoveryKey: String, newPin: String) {
        if (newPin.length !in 4..6) {
            _uiState.update { it.copy(errorMessage = "New PIN must be 4 to 6 digits") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val ok = repo.resetPinWithRecoveryKey(recoveryKey, newPin)
            if (ok) {
                _uiState.update { it.copy(isUnlocked = true, isBusy = false, errorMessage = null) }
                _events.emit("PIN reset and Vault unlocked")
            } else {
                _uiState.update { it.copy(isBusy = false, errorMessage = "Recovery key did not match") }
            }
        }
    }

    fun changePin(currentPin: String, newPin: String) {
        if (newPin.length !in 4..6) {
            viewModelScope.launch { _events.emit("New PIN must be 4 to 6 digits") }
            return
        }
        viewModelScope.launch {
            val ok = repo.changeVaultPin(currentPin, newPin)
            _events.emit(if (ok) "Vault PIN updated" else "Current PIN is incorrect")
        }
    }

    fun regenerateRecoveryKey() {
        viewModelScope.launch {
            val newKey = repo.regenerateRecoveryKey()
            if (newKey != null) {
                _uiState.update { it.copy(generatedRecoveryKey = newKey) }
            }
        }
    }

    fun lockVault() {
        repo.clearUnlockedSessionMemory()
        _uiState.update {
            it.copy(
                isUnlocked = false,
                selectedIds = emptySet(),
                decryptedThumbnails = emptyMap(),
                activePhotoBytes = null,
                activePhotoItem = null,
                errorMessage = null
            )
        }
    }

    fun onAppBackgrounded() {
        backgroundTimestampMs = System.currentTimeMillis()
    }

    fun onAppForegrounded(autoLockSeconds: Int) {
        if (!_uiState.value.isUnlocked) return
        if (autoLockSeconds < 0) return // Manual lock only
        if (autoLockSeconds == 0) {
            lockVault()
            return
        }
        if (backgroundTimestampMs > 0L) {
            val elapsedSec = (System.currentTimeMillis() - backgroundTimestampMs) / 1000L
            if (elapsedSec >= autoLockSeconds) {
                lockVault()
            }
        }
        backgroundTimestampMs = 0L
    }

    fun importMediaItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val succeededOriginals = mutableListOf<MediaItem>()
            for (item in items) {
                val imported = repo.importToVault(item)
                if (imported != null) {
                    succeededOriginals.add(item)
                }
            }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    pendingOriginalsAfterImport = succeededOriginals
                )
            }
            if (succeededOriginals.isNotEmpty()) {
                _events.emit("Encrypted ${succeededOriginals.size} item(s) in Private Vault")
            } else {
                _events.emit("Could not import selected media into Vault")
            }
        }
    }

    fun dismissOriginalsPrompt() {
        _uiState.update { it.copy(pendingOriginalsAfterImport = emptyList()) }
    }

    fun confirmDeleteOriginalsAfterImport() {
        val originals = _uiState.value.pendingOriginalsAfterImport
        _uiState.update { it.copy(pendingOriginalsAfterImport = emptyList()) }
        if (originals.isEmpty()) return

        viewModelScope.launch {
            val importedPickerItems = originals.filter { it.isImportedPickerItem }
            importedPickerItems.forEach {
                mediaRepo.removeImportedMediaRecord(it.mediaKey, it.contentUri)
            }
            val storeUris = originals.filterNot { it.isImportedPickerItem }.map { Uri.parse(it.contentUri) }
            if (storeUris.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    mediaRepo.createDeletePendingIntent(storeUris)?.let { pi ->
                        _deleteOriginalsIntent.emit(pi)
                    }
                } else {
                    mediaRepo.deleteMediaDirectlyLegacy(storeUris)
                    _events.emit("Original media removed from public gallery")
                }
            }
        }
    }

    fun openVaultPhoto(item: VaultItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val bytes = repo.decryptVaultPhotoBytes(item)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    activePhotoBytes = bytes,
                    activePhotoItem = if (bytes != null) item else null
                )
            }
            if (bytes == null) _events.emit("Failed to decrypt photo")
        }
    }

    fun closeVaultPhoto() {
        _uiState.update { it.copy(activePhotoBytes = null, activePhotoItem = null) }
    }

    fun exportVaultItem(item: VaultItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val uri = repo.exportFromVault(item)
            _uiState.update { it.copy(isBusy = false) }
            _events.emit(
                if (uri != null) "Exported \"${item.originalName}\" back to M Gallery"
                else "Export failed"
            )
        }
    }

    fun deleteVaultItem(item: VaultItem) {
        viewModelScope.launch {
            repo.removeFromVault(item)
            _uiState.update { state ->
                state.copy(
                    decryptedThumbnails = state.decryptedThumbnails - item.id,
                    selectedIds = state.selectedIds - item.id
                )
            }
            _events.emit("Deleted \"${item.originalName}\" from Private Vault")
        }
    }

    fun toggleSelect(id: String) {
        _uiState.update { state ->
            val set = state.selectedIds.toMutableSet()
            if (!set.add(id)) set.remove(id)
            state.copy(selectedIds = set)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun deleteSelectedVaultItems() {
        val selected = _uiState.value.items.filter { it.id in _uiState.value.selectedIds }
        viewModelScope.launch {
            selected.forEach { repo.removeFromVault(it) }
            _uiState.update { it.copy(selectedIds = emptySet()) }
            _events.emit("Deleted ${selected.size} vault item(s)")
        }
    }

    private fun preloadThumbnails(items: List<VaultItem>) {
        viewModelScope.launch {
            val currentMap = _uiState.value.decryptedThumbnails.toMutableMap()
            for (item in items) {
                if (item.id !in currentMap) {
                    val bytes = repo.decryptVaultThumbnail(item)
                    if (bytes != null) {
                        currentMap[item.id] = bytes
                        _uiState.update { it.copy(decryptedThumbnails = currentMap.toMap()) }
                    }
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return VaultViewModel(container) as T
                }
            }
    }
}
