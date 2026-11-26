package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VaultItem(
    val id: String,
    val originalName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val encryptedFileName: String,
    val encryptedThumbName: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val dateAddedMs: Long,
    val dateOriginalTakenMs: Long,
    val sha256Hash: String
) {
    val mediaType: MediaType
        get() = if (isVideo) MediaType.VIDEO else MediaType.PHOTO
}

@Serializable
data class VaultManifest(
    val items: List<VaultItem> = emptyList()
)

data class TrashItem(
    val id: String,
    val originalUri: String?,
    val displayName: String,
    val mimeType: String,
    val mediaType: MediaType,
    val trashedFilePath: String,
    val trashedThumbPath: String?,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val trashedAtMs: Long,
    val expiresAtMs: Long
) {
    val daysRemaining: Int
        get() {
            val diff = expiresAtMs - System.currentTimeMillis()
            return if (diff <= 0L) 0 else ((diff / 86_400_000L) + 1L).toInt()
        }
}

enum class DuplicateKind {
    EXACT_HASH,
    VISUALLY_SIMILAR
}

data class DuplicateGroup(
    val groupId: String,
    val kind: DuplicateKind,
    val items: List<MediaItem>
) {
    val reclaimableBytes: Long
        get() = if (items.size <= 1) 0L else items.drop(1).sumOf { it.sizeBytes }
}

data class StorageBreakdown(
    val photosCount: Int = 0,
    val photosBytes: Long = 0L,
    val videosCount: Int = 0,
    val videosBytes: Long = 0L,
    val vaultBytes: Long = 0L,
    val trashBytes: Long = 0L,
    val topBuckets: List<Pair<String, Long>> = emptyList()
) {
    val totalManagedBytes: Long
        get() = photosBytes + videosBytes + vaultBytes + trashBytes
}

data class SmartAnalysisState(
    val isScanning: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val exactDuplicates: List<DuplicateGroup> = emptyList(),
    val visuallySimilar: List<DuplicateGroup> = emptyList(),
    val largeFiles: List<MediaItem> = emptyList(),
    val breakdown: StorageBreakdown = StorageBreakdown(),
    val hasCompletedScan: Boolean = false
)
