package com.example.domain.model

enum class MediaType {
    PHOTO,
    VIDEO
}

enum class MediaSortOrder(val label: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME_ASC("Name (A–Z)"),
    SIZE_DESC("Largest first"),
    TYPE("Media type")
}

enum class GalleryViewMode {
    GRID,
    LIST
}

enum class HomeMediaTab(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos")
}

data class MediaItem(
    val mediaKey: String,
    val id: Long,
    val contentUri: String,
    val displayName: String,
    val mimeType: String,
    val mediaType: MediaType,
    val dateTakenMs: Long,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucketId: String,
    val bucketName: String,
    val relativePath: String,
    val orientation: Int = 0,
    val isFavorite: Boolean = false,
    val isImportedPickerItem: Boolean = false
) {
    val effectiveTimestampMs: Long
        get() = when {
            dateTakenMs > 0L -> dateTakenMs
            dateModifiedSec > 0L -> dateModifiedSec * 1000L
            dateAddedSec > 0L -> dateAddedSec * 1000L
            else -> 0L
        }

    val isScreenshot: Boolean
        get() = bucketName.contains("screenshot", ignoreCase = true) ||
            relativePath.contains("screenshot", ignoreCase = true) ||
            displayName.startsWith("Screenshot", ignoreCase = true)
}

data class MediaExifInfo(
    val resolution: String,
    val megapixels: String?,
    val fileSizeFormatted: String,
    val mimeType: String,
    val folderPath: String,
    val captureDateFormatted: String,
    val cameraMakeModel: String?,
    val focalLength: String?,
    val aperture: String?,
    val iso: String?,
    val shutterSpeed: String?,
    val locationLatLong: String?,
    val durationFormatted: String?
)

enum class AlbumKind {
    SMART_FAVORITES,
    SMART_RECENT_ADDED,
    SMART_RECENT_VIEWED,
    USER_ALBUM,
    SYSTEM_ALBUM
}

data class Album(
    val id: String,
    val title: String,
    val coverUri: String?,
    val itemCount: Int,
    val kind: AlbumKind,
    val totalSizeBytes: Long = 0L
)

data class TimelineSection(
    val label: String,
    val items: List<MediaItem>
)
