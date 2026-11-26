package com.example.domain.smart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.example.domain.model.DuplicateGroup
import com.example.domain.model.DuplicateKind
import com.example.domain.model.MediaItem
import com.example.domain.model.MediaType
import com.example.domain.model.SmartAnalysisState
import com.example.domain.model.StorageBreakdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class MediaAnalyzer(private val context: Context) {

    fun computeStorageBreakdown(
        items: List<MediaItem>,
        vaultBytes: Long,
        trashBytes: Long
    ): StorageBreakdown {
        var photoCount = 0
        var photoBytes = 0L
        var videoCount = 0
        var videoBytes = 0L
        val bucketSizes = HashMap<String, Long>()

        for (item in items) {
            if (item.mediaType == MediaType.PHOTO) {
                photoCount++
                photoBytes += item.sizeBytes
            } else {
                videoCount++
                videoBytes += item.sizeBytes
            }
            val bucket = item.bucketName.ifBlank { "Other" }
            bucketSizes[bucket] = (bucketSizes[bucket] ?: 0L) + item.sizeBytes
        }

        val topBuckets = bucketSizes.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }

        return StorageBreakdown(
            photosCount = photoCount,
            photosBytes = photoBytes,
            videosCount = videoCount,
            videosBytes = videoBytes,
            vaultBytes = vaultBytes,
            trashBytes = trashBytes,
            topBuckets = topBuckets
        )
    }

    suspend fun analyzeLibrary(
        items: List<MediaItem>,
        vaultBytes: Long,
        trashBytes: Long,
        largeFileThresholdBytes: Long = 10L * 1024L * 1024L,
        onProgress: (Float, String) -> Unit
    ): SmartAnalysisState = withContext(Dispatchers.Default) {
        val breakdown = computeStorageBreakdown(items, vaultBytes, trashBytes)
        val largeFiles = items
            .filter { it.sizeBytes >= largeFileThresholdBytes }
            .sortedByDescending { it.sizeBytes }
            .ifEmpty { items.sortedByDescending { it.sizeBytes }.take(15) }

        if (items.isEmpty()) {
            return@withContext SmartAnalysisState(
                isScanning = false,
                progress = 1f,
                statusText = "No media items to analyze",
                breakdown = breakdown,
                largeFiles = emptyList(),
                hasCompletedScan = true
            )
        }

        // Phase 1: Exact duplicate detection via file-size grouping + streaming SHA-256
        onProgress(0.15f, "Comparing file sizes & SHA-256 signatures...")
        val sameSizeGroups = items.filter { it.sizeBytes > 0L }.groupBy { it.sizeBytes }.values.filter { it.size > 1 }
        val exactGroups = mutableListOf<DuplicateGroup>()
        val exactMatchedKeys = HashSet<String>()

        var processed = 0
        val totalHashCandidates = sameSizeGroups.sumOf { it.size }.coerceAtLeast(1)
        for (group in sameSizeGroups) {
            coroutineContext.ensureActive()
            val byHash = HashMap<String, MutableList<MediaItem>>()
            for (item in group) {
                coroutineContext.ensureActive()
                val hash = computeSha256(Uri.parse(item.contentUri))
                if (hash != null) {
                    byHash.getOrPut(hash) { mutableListOf() }.add(item)
                }
                processed++
                onProgress(0.15f + 0.35f * (processed.toFloat() / totalHashCandidates), "Hashing candidate ${processed}/$totalHashCandidates")
            }
            for ((hash, matched) in byHash) {
                if (matched.size > 1) {
                    exactGroups.add(
                        DuplicateGroup(
                            groupId = "exact_$hash",
                            kind = DuplicateKind.EXACT_HASH,
                            items = matched.sortedBy { it.effectiveTimestampMs }
                        )
                    )
                    matched.forEach { exactMatchedKeys.add(it.mediaKey) }
                }
            }
        }

        // Phase 2: Visually similar photos via 9x8 perceptual dHash (excluding exact duplicates)
        val photoCandidates = items
            .filter { it.mediaType == MediaType.PHOTO && it.mediaKey !in exactMatchedKeys }
            .take(350) // Bound background work gracefully on huge libraries

        val dHashes = ArrayList<Pair<MediaItem, Long>>(photoCandidates.size)
        for ((idx, photo) in photoCandidates.withIndex()) {
            coroutineContext.ensureActive()
            computeDHash(Uri.parse(photo.contentUri))?.let { hash ->
                dHashes.add(photo to hash)
            }
            if (idx % 8 == 0 || idx == photoCandidates.lastIndex) {
                val frac = (idx + 1).toFloat() / photoCandidates.size.coerceAtLeast(1)
                onProgress(0.50f + 0.45f * frac, "Comparing visual structure (${idx + 1}/${photoCandidates.size})...")
            }
        }

        val used = BooleanArray(dHashes.size)
        val similarGroups = mutableListOf<DuplicateGroup>()
        for (i in dHashes.indices) {
            if (used[i]) continue
            val (anchor, anchorHash) = dHashes[i]
            val cluster = mutableListOf(anchor)
            for (j in i + 1 until dHashes.size) {
                if (used[j]) continue
                val dist = java.lang.Long.bitCount(anchorHash xor dHashes[j].second)
                if (dist <= 5) {
                    used[j] = true
                    cluster.add(dHashes[j].first)
                }
            }
            if (cluster.size > 1) {
                used[i] = true
                similarGroups.add(
                    DuplicateGroup(
                        groupId = "sim_${anchor.mediaKey}",
                        kind = DuplicateKind.VISUALLY_SIMILAR,
                        items = cluster.sortedBy { it.effectiveTimestampMs }
                    )
                )
            }
        }

        onProgress(1f, "Analysis complete")
        SmartAnalysisState(
            isScanning = false,
            progress = 1f,
            statusText = "Scan complete",
            exactDuplicates = exactGroups,
            visuallySimilar = similarGroups,
            largeFiles = largeFiles,
            breakdown = breakdown,
            hasCompletedScan = true
        )
    }

    private fun computeSha256(uri: Uri): String? {
        return runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(16_384)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                while (true) {
                    val read = stream.read(buf)
                    if (read <= 0) break
                    md.update(buf, 0, read)
                }
            } ?: return null
            md.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    /**
     * Computes a 64-bit Difference Hash (dHash) on a 9x8 grayscale thumbnail.
     */
    private fun computeDHash(uri: Uri): Long? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > 128 && bounds.outHeight / sample > 128) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            val tiny = Bitmap.createScaledBitmap(decoded, 9, 8, true)
            if (tiny !== decoded) decoded.recycle()

            var hash = 0L
            var bit = 0
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val left = luminance(tiny.getPixel(x, y))
                    val right = luminance(tiny.getPixel(x + 1, y))
                    if (left > right) {
                        hash = hash or (1L shl bit)
                    }
                    bit++
                }
            }
            tiny.recycle()
            hash
        }.getOrNull()
    }

    private fun luminance(pixel: Int): Int {
        return (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
    }
}
