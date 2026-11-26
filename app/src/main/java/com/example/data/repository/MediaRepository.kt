package com.example.data.repository

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.example.data.local.db.GalleryDao
import com.example.data.local.db.ImportedMediaEntity
import com.example.domain.model.MediaExifInfo
import com.example.domain.model.MediaItem
import com.example.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MediaRepository(
    private val context: Context,
    private val dao: GalleryDao
) {
    private val contentResolver: ContentResolver = context.contentResolver

    fun observeMediaStoreChanges(): Flow<Unit> = callbackFlow {
        trySend(Unit)
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        runCatching {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        }
        awaitClose {
            runCatching { contentResolver.unregisterContentObserver(observer) }
        }
    }

    fun observeAllAccessibleMedia(refreshSignal: Flow<Int>): Flow<List<MediaItem>> {
        return combine(
            observeMediaStoreChanges(),
            dao.observeFavoriteKeys(),
            dao.observeImportedMedia(),
            refreshSignal
        ) { _, favoriteKeys, importedEntities, _ ->
            val favSet = favoriteKeys.toHashSet()
            val storeItems = queryDeviceMediaStore(favSet)
            val existingUris = storeItems.mapTo(HashSet()) { it.contentUri }
            val importedItems = importedEntities
                .filter { it.contentUri !in existingUris }
                .map { entity ->
                    MediaItem(
                        mediaKey = entity.mediaKey,
                        id = entity.mediaKey.hashCode().toLong(),
                        contentUri = entity.contentUri,
                        displayName = entity.displayName,
                        mimeType = entity.mimeType,
                        mediaType = if (entity.isVideo) MediaType.VIDEO else MediaType.PHOTO,
                        dateTakenMs = entity.dateTakenMs,
                        dateAddedSec = entity.addedAtMs / 1000L,
                        dateModifiedSec = entity.addedAtMs / 1000L,
                        sizeBytes = entity.sizeBytes,
                        width = entity.width,
                        height = entity.height,
                        durationMs = entity.durationMs,
                        bucketId = "imported_picker",
                        bucketName = "Imported",
                        relativePath = "M Gallery/Imported",
                        isFavorite = entity.mediaKey in favSet,
                        isImportedPickerItem = true
                    )
                }
            (storeItems + importedItems).distinctBy { it.mediaKey }
        }.flowOn(Dispatchers.IO)
    }

    private fun queryDeviceMediaStore(favoriteKeys: Set<String>): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        result.addAll(queryImages(favoriteKeys))
        result.addAll(queryVideos(favoriteKeys))
        return result
    }

    private fun queryImages(favoriteKeys: Set<String>): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.MIME_TYPE)
            add(MediaStore.Images.Media.DATE_TAKEN)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.DATE_MODIFIED)
            add(MediaStore.Images.Media.SIZE)
            add(MediaStore.Images.Media.WIDTH)
            add(MediaStore.Images.Media.HEIGHT)
            add(MediaStore.Images.Media.BUCKET_ID)
            add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            add(MediaStore.Images.Media.ORIENTATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        runCatching {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val takenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val addedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val modCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val orientCol = cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()
                    val key = "img_$id"
                    val displayName = if (nameCol >= 0) cursor.getString(nameCol) ?: "Photo_$id" else "Photo_$id"
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "image/jpeg" else "image/jpeg"
                    val dateTaken = if (takenCol >= 0) cursor.getLong(takenCol) else 0L
                    val dateAdded = if (addedCol >= 0) cursor.getLong(addedCol) else 0L
                    val dateMod = if (modCol >= 0) cursor.getLong(modCol) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                    val bucketId = if (bucketIdCol >= 0) cursor.getString(bucketIdCol) ?: "0" else "0"
                    val bucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) ?: "Camera" else "Camera"
                    val orientation = if (orientCol >= 0) cursor.getInt(orientCol) else 0
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol) ?: "" else ""

                    items.add(
                        MediaItem(
                            mediaKey = key,
                            id = id,
                            contentUri = uri,
                            displayName = displayName,
                            mimeType = mime,
                            mediaType = MediaType.PHOTO,
                            dateTakenMs = dateTaken,
                            dateAddedSec = dateAdded,
                            dateModifiedSec = dateMod,
                            sizeBytes = size,
                            width = width,
                            height = height,
                            durationMs = 0L,
                            bucketId = "sys_$bucketId",
                            bucketName = bucketName,
                            relativePath = relPath,
                            orientation = orientation,
                            isFavorite = key in favoriteKeys
                        )
                    )
                }
            }
        }
        return items
    }

    private fun queryVideos(favoriteKeys: Set<String>): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.DATE_TAKEN)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.BUCKET_ID)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        runCatching {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val takenCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
                val addedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val modCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()
                    val key = "vid_$id"
                    val displayName = if (nameCol >= 0) cursor.getString(nameCol) ?: "Video_$id" else "Video_$id"
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4"
                    val dateTaken = if (takenCol >= 0) cursor.getLong(takenCol) else 0L
                    val dateAdded = if (addedCol >= 0) cursor.getLong(addedCol) else 0L
                    val dateMod = if (modCol >= 0) cursor.getLong(modCol) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                    val duration = if (durCol >= 0) cursor.getLong(durCol) else 0L
                    val bucketId = if (bucketIdCol >= 0) cursor.getString(bucketIdCol) ?: "0" else "0"
                    val bucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) ?: "Videos" else "Videos"
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol) ?: "" else ""

                    items.add(
                        MediaItem(
                            mediaKey = key,
                            id = id,
                            contentUri = uri,
                            displayName = displayName,
                            mimeType = mime,
                            mediaType = MediaType.VIDEO,
                            dateTakenMs = dateTaken,
                            dateAddedSec = dateAdded,
                            dateModifiedSec = dateMod,
                            sizeBytes = size,
                            width = width,
                            height = height,
                            durationMs = duration,
                            bucketId = "sys_$bucketId",
                            bucketName = bucketName,
                            relativePath = relPath,
                            isFavorite = key in favoriteKeys
                        )
                    )
                }
            }
        }
        return items
    }

    /**
     * Imports URIs returned by the Android Photo Picker (works with zero storage permissions).
     * Copies into app-managed gallery directory if persistable permission isn't available so
     * items survive app restarts cleanly.
     */
    suspend fun importPickerUris(uris: List<Uri>): List<MediaItem> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<MediaItem>()
        val importedDir = File(context.filesDir, "imported_media").apply { mkdirs() }

        for (uri in uris) {
            runCatching {
                val mime = contentResolver.getType(uri) ?: "image/jpeg"
                val isVideo = mime.startsWith("video/")
                var name = "Media_${System.currentTimeMillis()}"
                var size = 0L

                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val nIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sIdx = c.getColumnIndex(OpenableColumns.SIZE)
                        if (nIdx >= 0) name = c.getString(nIdx) ?: name
                        if (sIdx >= 0) size = c.getLong(sIdx)
                    }
                }

                // Persist into local imported directory so it never loses URI permission across restarts
                val ext = name.substringAfterLast('.', if (isVideo) "mp4" else "jpg")
                val localFile = File(importedDir, "${UUID.randomUUID()}.$ext")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(localFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (size <= 0L) size = localFile.length()

                var width = 0
                var height = 0
                var durationMs = 0L
                if (isVideo) {
                    val retriever = MediaMetadataRetriever()
                    runCatching {
                        retriever.setDataSource(localFile.absolutePath)
                        width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                        height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    }
                    retriever.release()
                } else {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(localFile.absolutePath, opts)
                    width = opts.outWidth
                    height = opts.outHeight
                }

                val providerUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    localFile
                ).toString()

                val now = System.currentTimeMillis()
                val key = "imp_${localFile.nameWithoutExtension}"
                val entity = ImportedMediaEntity(
                    mediaKey = key,
                    contentUri = providerUri,
                    displayName = name,
                    mimeType = mime,
                    isVideo = isVideo,
                    sizeBytes = size,
                    width = width,
                    height = height,
                    durationMs = durationMs,
                    dateTakenMs = now,
                    addedAtMs = now
                )
                dao.upsertImportedMedia(entity)

                imported.add(
                    MediaItem(
                        mediaKey = key,
                        id = key.hashCode().toLong(),
                        contentUri = providerUri,
                        displayName = name,
                        mimeType = mime,
                        mediaType = if (isVideo) MediaType.VIDEO else MediaType.PHOTO,
                        dateTakenMs = now,
                        dateAddedSec = now / 1000L,
                        dateModifiedSec = now / 1000L,
                        sizeBytes = size,
                        width = width,
                        height = height,
                        durationMs = durationMs,
                        bucketId = "imported_picker",
                        bucketName = "Imported",
                        relativePath = "M Gallery/Imported",
                        isImportedPickerItem = true
                    )
                )
            }
        }
        imported
    }

    suspend fun removeImportedMediaRecord(mediaKey: String, uriString: String) = withContext(Dispatchers.IO) {
        dao.deleteImportedMedia(mediaKey)
        val fileName = Uri.parse(uriString).lastPathSegment
        if (!fileName.isNullOrBlank()) {
            val file = File(File(context.filesDir, "imported_media"), fileName)
            if (file.exists()) file.delete()
        }
    }

    suspend fun loadExifInfo(item: MediaItem): MediaExifInfo = withContext(Dispatchers.IO) {
        val formatter = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
        val dateText = if (item.effectiveTimestampMs > 0L) {
            formatter.format(Date(item.effectiveTimestampMs))
        } else {
            "Unknown date"
        }

        var width = item.width
        var height = item.height
        var makeModel: String? = null
        var focal: String? = null
        var aperture: String? = null
        var iso: String? = null
        var shutter: String? = null
        var latLongText: String? = null

        if (item.mediaType == MediaType.PHOTO) {
            runCatching {
                contentResolver.openInputStream(Uri.parse(item.contentUri))?.use { stream ->
                    val exif = ExifInterface(stream)
                    if (width <= 0) width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                    if (height <= 0) height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                    val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()
                    val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()
                    makeModel = listOfNotNull(make, model).distinct().joinToString(" ").ifBlank { null }
                    focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, -1.0)
                        .takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f mm", it) }
                    aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" }
                    iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { "ISO $it" }
                    shutter = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, -1.0)
                        .takeIf { it > 0 }?.let { sec ->
                            if (sec < 1.0) "1/${(1.0 / sec).toInt()} s" else "${sec}s"
                        }
                    exif.latLong?.let { coords ->
                        latLongText = String.format(Locale.US, "%.5f, %.5f", coords[0], coords[1])
                    }
                }
            }
        }

        val resText = if (width > 0 && height > 0) "$width × $height" else "Unknown"
        val mpText = if (width > 0 && height > 0 && item.mediaType == MediaType.PHOTO) {
            val mp = (width.toLong() * height.toLong()) / 1_000_000.0
            String.format(Locale.US, "%.1f MP", mp)
        } else null

        val durText = if (item.mediaType == MediaType.VIDEO && item.durationMs > 0L) {
            formatDuration(item.durationMs)
        } else null

        MediaExifInfo(
            resolution = resText,
            megapixels = mpText,
            fileSizeFormatted = formatBytes(item.sizeBytes),
            mimeType = item.mimeType,
            folderPath = item.relativePath.ifBlank { item.bucketName },
            captureDateFormatted = dateText,
            cameraMakeModel = makeModel,
            focalLength = focal,
            aperture = aperture,
            iso = iso,
            shutterSpeed = shutter,
            locationLatLong = latLongText,
            durationFormatted = durText
        )
    }

    fun buildShareIntent(items: List<MediaItem>): Intent? {
        if (items.isEmpty()) return null
        val uris = ArrayList(items.map { Uri.parse(it.contentUri) })
        return if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = items.first().mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            val allImages = items.all { it.mediaType == MediaType.PHOTO }
            val allVideos = items.all { it.mediaType == MediaType.VIDEO }
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = when {
                    allImages -> "image/*"
                    allVideos -> "video/*"
                    else -> "*/*"
                }
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun createDeletePendingIntent(uris: List<Uri>): PendingIntent? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(contentResolver, uris)
        } else {
            null
        }
    }

    suspend fun deleteMediaDirectlyLegacy(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (uri in uris) {
            runCatching {
                if (contentResolver.delete(uri, null, null) > 0) deleted++
            }
        }
        deleted
    }

    suspend fun saveBytesToGallery(
        bytes: ByteArray,
        displayName: String,
        mimeType: String,
        isVideo: Boolean
    ): Uri? = withContext(Dispatchers.IO) {
        val collection = if (isVideo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (isVideo) "Movies/M Gallery" else "Pictures/M Gallery"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val inserted = runCatching { contentResolver.insert(collection, values) }.getOrNull()
        if (inserted != null) {
            runCatching {
                contentResolver.openOutputStream(inserted)?.use { out ->
                    out.write(bytes)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    contentResolver.update(inserted, done, null, null)
                }
                return@withContext inserted
            }.onFailure {
                runCatching { contentResolver.delete(inserted, null, null) }
            }
        }

        // Fallback into app-managed imported folder if external MediaStore insert was denied
        val fallbackDir = File(context.filesDir, "imported_media").apply { mkdirs() }
        val file = File(fallbackDir, displayName)
        FileOutputStream(file).use { it.write(bytes) }
        val providerUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val now = System.currentTimeMillis()
        dao.upsertImportedMedia(
            ImportedMediaEntity(
                mediaKey = "imp_${file.nameWithoutExtension}",
                contentUri = providerUri.toString(),
                displayName = displayName,
                mimeType = mimeType,
                isVideo = isVideo,
                sizeBytes = bytes.size.toLong(),
                width = 0,
                height = 0,
                durationMs = 0L,
                dateTakenMs = now,
                addedAtMs = now
            )
        )
        providerUri
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
                else -> "$bytes B"
            }
        }

        fun formatDuration(ms: Long): String {
            if (ms <= 0L) return "0:00"
            val totalSec = ms / 1000L
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return if (h > 0) {
                String.format(Locale.US, "%d:%02d:%02d", h, m, s)
            } else {
                String.format(Locale.US, "%d:%02d", m, s)
            }
        }
    }
}
