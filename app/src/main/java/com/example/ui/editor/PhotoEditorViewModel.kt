package com.example.ui.editor

import android.app.PendingIntent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.domain.editor.AspectRatioPreset
import com.example.domain.editor.FilterPreset
import com.example.domain.editor.NormalizedCropRect
import com.example.domain.editor.PhotoEditState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PhotoEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val sourceBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val editState: PhotoEditState = PhotoEditState(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isComparingOriginal: Boolean = false,
    val errorMessage: String? = null
)

sealed interface EditorSaveResult {
    data class SavedCopy(val message: String) : EditorSaveResult
    data object OverwrittenOriginal : EditorSaveResult
    data class NeedsWriteConsent(val pendingIntent: PendingIntent) : EditorSaveResult
    data class Error(val message: String) : EditorSaveResult
}

class PhotoEditorViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val processor = container.photoEditorProcessor
    private var targetUri: Uri? = null
    private var targetName: String = "Photo"

    private val undoStack = ArrayDeque<PhotoEditState>()
    private val redoStack = ArrayDeque<PhotoEditState>()

    private val _uiState = MutableStateFlow(PhotoEditorUiState())
    val uiState: StateFlow<PhotoEditorUiState> = _uiState.asStateFlow()

    private val _saveResults = MutableSharedFlow<EditorSaveResult>()
    val saveResults: SharedFlow<EditorSaveResult> = _saveResults.asSharedFlow()

    fun loadImage(uriString: String, displayName: String) {
        val uri = Uri.parse(uriString)
        if (targetUri == uri && _uiState.value.sourceBitmap != null) return
        targetUri = uri
        targetName = displayName
        undoStack.clear()
        redoStack.clear()

        viewModelScope.launch {
            _uiState.value = PhotoEditorUiState(isLoading = true)
            val decoded = processor.decodeForEditing(uri, maxDimension = 1440)
            if (decoded == null) {
                _uiState.value = PhotoEditorUiState(
                    isLoading = false,
                    errorMessage = "Could not open image for editing."
                )
            } else {
                _uiState.value = PhotoEditorUiState(
                    isLoading = false,
                    sourceBitmap = decoded,
                    previewBitmap = decoded,
                    editState = PhotoEditState()
                )
            }
        }
    }

    fun applyEdit(commitToHistory: Boolean = true, transform: (PhotoEditState) -> PhotoEditState) {
        val current = _uiState.value.editState
        val next = transform(current)
        if (next == current) return
        if (commitToHistory) {
            undoStack.addLast(current)
            if (undoStack.size > 30) undoStack.removeFirst()
            redoStack.clear()
        }
        updateAndRender(next)
    }

    fun updateSliderPreview(transform: (PhotoEditState) -> PhotoEditState) {
        val next = transform(_uiState.value.editState)
        updateAndRender(next)
    }

    fun pushUndoCheckpoint(previousState: PhotoEditState) {
        if (previousState != _uiState.value.editState) {
            undoStack.addLast(previousState)
            if (undoStack.size > 30) undoStack.removeFirst()
            redoStack.clear()
            _uiState.update {
                it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty())
            }
        }
    }

    fun rotate90(clockwise: Boolean = true) {
        applyEdit { state ->
            val delta = if (clockwise) 1 else 3
            state.copy(rotationQuarterTurns = (state.rotationQuarterTurns + delta) % 4)
        }
    }

    fun toggleFlipHorizontal() = applyEdit { it.copy(flipHorizontal = !it.flipHorizontal) }
    fun toggleFlipVertical() = applyEdit { it.copy(flipVertical = !it.flipVertical) }

    fun setFilter(preset: FilterPreset) = applyEdit { it.copy(filterPreset = preset) }

    fun setAspectRatio(preset: AspectRatioPreset) {
        applyEdit { state ->
            val ratio = preset.ratio
            val newRect = if (ratio == null) {
                state.cropRect
            } else {
                fitAspectRect(ratio)
            }
            state.copy(aspectPreset = preset, cropRect = newRect)
        }
    }

    fun updateCropRect(rect: NormalizedCropRect) {
        applyEdit { it.copy(cropRect = rect) }
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_uiState.value.editState)
        updateAndRender(prev)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_uiState.value.editState)
        updateAndRender(next)
    }

    fun resetAll() {
        val current = _uiState.value.editState
        if (current.isDefault) return
        undoStack.addLast(current)
        redoStack.clear()
        updateAndRender(PhotoEditState())
    }

    fun setComparingOriginal(comparing: Boolean) {
        _uiState.update { it.copy(isComparingOriginal = comparing) }
    }

    fun saveAsNewCopy() {
        val uri = targetUri ?: return
        val state = _uiState.value.editState
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val saved = processor.saveAsNewCopy(uri, targetName, state)
            _uiState.update { it.copy(isSaving = false) }
            if (saved != null) {
                _saveResults.emit(EditorSaveResult.SavedCopy("Saved edited copy to Pictures/M Gallery"))
            } else {
                _saveResults.emit(EditorSaveResult.Error("Failed to save edited copy"))
            }
        }
    }

    fun requestReplaceOriginal() {
        val uri = targetUri ?: return
        val consent = processor.needsWriteConsentForOverwrite(uri)
        if (consent != null) {
            viewModelScope.launch {
                _saveResults.emit(EditorSaveResult.NeedsWriteConsent(consent))
            }
        } else {
            executeOverwriteOriginal()
        }
    }

    fun executeOverwriteOriginal() {
        val uri = targetUri ?: return
        val state = _uiState.value.editState
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = processor.overwriteOriginal(uri, state)
            _uiState.update { it.copy(isSaving = false) }
            result.onSuccess {
                _saveResults.emit(EditorSaveResult.OverwrittenOriginal)
            }.onFailure { err ->
                _saveResults.emit(EditorSaveResult.Error(err.message ?: "Could not overwrite original"))
            }
        }
    }

    private fun updateAndRender(newState: PhotoEditState) {
        val src = _uiState.value.sourceBitmap ?: return
        _uiState.update {
            it.copy(
                editState = newState,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        viewModelScope.launch {
            val rendered = withContext(Dispatchers.Default) {
                // In preview we render geometric + color transforms without cropping so the crop handles remain interactive
                processor.renderEditedBitmap(src, newState, applyCrop = false)
            }
            _uiState.update { it.copy(previewBitmap = rendered) }
        }
    }

    private fun fitAspectRect(targetRatio: Float): NormalizedCropRect {
        val bmp = _uiState.value.previewBitmap ?: _uiState.value.sourceBitmap
        val imgRatio = if (bmp != null && bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else 1f
        val relRatio = targetRatio / imgRatio
        return if (relRatio >= 1f) {
            val h = (1f / relRatio).coerceIn(0.15f, 1f)
            val top = (1f - h) / 2f
            NormalizedCropRect(0f, top, 1f, top + h)
        } else {
            val w = relRatio.coerceIn(0.15f, 1f)
            val left = (1f - w) / 2f
            NormalizedCropRect(left, 0f, left + w, 1f)
        }
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.value.sourceBitmap?.recycle()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PhotoEditorViewModel(container) as T
                }
            }
    }
}
