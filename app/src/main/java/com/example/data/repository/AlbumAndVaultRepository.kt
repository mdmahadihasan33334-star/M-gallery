package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.local.db.AlbumMediaCrossRef
import com.example.data.local.db.FavoriteEntity
import com.example.data.local.db.GalleryDao
import com.example.data.local.db.RecentlyViewedEntity
import com.example.data.local.db.TrashEntity
import com.example.data.local.db.UserAlbumEntity
import com.example.data.security.VaultCryptoManager
import com.example.domain.model.MediaItem
import com.example.domain.model.MediaType
import com.example.domain.model.TrashItem
import com.example.domain.model.VaultItem
import com.example.domain.model.VaultManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Base64
import java.util.UUID

@Serializable
private data class VaultSecurityConfig(
    val pinSaltBase64: String,
    val pinHashBase64: String,
    val recoverySaltBase64: String,
    val recoveryHashBase64: String,
    val createdAtMs: Long
)

class AlbumAndVaultRepository(
    private val context: Context,
    private val dao: GalleryDao,
    private val mediaRepository: MediaRepository,
    val cryptoManager: VaultCryptoManager
) {
    private val json = Json { ignoreUnknownKeys = true }

    val vaultDir: File = File(context.filesDir, "vault").apply { mkdirs() }
    private val trashDir: File = File(context.filesDir, "recycle_bin").apply { mkdirs() }
    private val securityConfigFile = File(vaultDir, "vault_security.bin")
    private val manifestFile = File(vaultDir, "vault_manifest.bin")

    private val _vaultItems = MutableStateFlow<List<VaultItem>>(emptyList())
    val vaultItems: StateFlow<List<VaultItem>> = _vaultItems.asStateFlow()

    // --- Favorites & Recently Viewed ---

    val favoriteKeys: Flow<List<String>> = dao.observeFavoriteKeys()

    val recentlyViewedKeys: Flow<List<String>> = dao.observeRecentlyViewed().map { list ->
        list.map { it.mediaKey }
    }

    suspend fun setFavorite(mediaKey: String, favorite: Boolean) {
        if (favorite) {
            dao.addFavorite(FavoriteEntity(mediaKey = mediaKey, addedAtMs = System.currentTimeMillis()))
        } else {
            dao.removeFavorite(mediaKey)
        }
    }

    suspend fun setFavorites(mediaKeys: List<String>, favorite: Boolean) {
        if (mediaKeys.isEmpty()) return
        if (favorite) {
            val now = System.currentTimeMillis()
            dao.addFavorites(mediaKeys.map { FavoriteEntity(mediaKey = it, addedAtMs = now) })
        } else {
            dao.removeFavorites(mediaKeys)
        }
    }

    suspend fun clearAllFavorites() {
        dao.clearAllFavorites()
    }

    suspend fun toggleFavorite(item: MediaItem) {
        setFavorite(item.mediaKey, !item.isFavorite)
    }

    suspend fun recordViewed(mediaKey: String) {
        dao.recordViewed(RecentlyViewedEntity(mediaKey = mediaKey, viewedAtMs = System.currentTimeMillis()))
    }

    // --- User Albums ---

    val userAlbums: Flow<List<UserAlbumEntity>> = dao.observeUserAlbums()
    val albumCrossRefs: Flow<List<AlbumMediaCrossRef>> = dao.observeAllAlbumCrossRefs()

    suspend fun createAlbum(name: String, initialMedia: List<MediaItem> = emptyList()): UserAlbumEntity {
        val albumId = "user_${UUID.randomUUID()}"
        val cover = initialMedia.firstOrNull()?.contentUri
        val entity = UserAlbumEntity(
            albumId = albumId,
            name = name.trim().ifBlank { "Untitled Album" },
            coverUri = cover,
            createdAtMs = System.currentTimeMillis()
        )
        dao.upsertUserAlbum(entity)
        if (initialMedia.isNotEmpty()) {
            addMediaToAlbum(albumId, initialMedia)
        }
        return entity
    }

    suspend fun renameAlbum(albumId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty()) {
            dao.renameUserAlbum(albumId, trimmed)
        }
    }

    suspend fun updateAlbumCover(albumId: String, coverUri: String?) {
        dao.updateUserAlbumCover(albumId, coverUri)
    }

    suspend fun deleteAlbum(albumId: String) {
        dao.deleteUserAlbum(albumId)
    }

    suspend fun addMediaToAlbum(albumId: String, items: List<MediaItem>) {
        val now = System.currentTimeMillis()
        dao.addMediaListToAlbum(
            items.map {
                AlbumMediaCrossRef(albumId = albumId, mediaKey = it.mediaKey, addedAtMs = now)
            }
        )
    }

    suspend fun removeMediaFromAlbum(albumId: String, mediaKey: String) {
        dao.removeMediaFromAlbum(albumId, mediaKey)
    }

    // --- Recycle Bin ---

    val trashItems: Flow<List<TrashItem>> = dao.observeTrashItems().map { entities ->
        entities.map { entity ->
            TrashItem(
                id = entity.id,
                originalUri = entity.originalUri,
                displayName = entity.displayName,
                mimeType = entity.mimeType,
                mediaType = if (entity.isVideo) MediaType.VIDEO else MediaType.PHOTO,
                trashedFilePath = entity.trashedFilePath,
                trashedThumbPath = entity.trashedThumbPath,
                sizeBytes = entity.sizeBytes,
                width = entity.width,
                height = entity.height,
                durationMs = entity.durationMs,
                trashedAtMs = entity.trashedAtMs,
                expiresAtMs = entity.expiresAtMs
            )
        }
    }

    /**
     * Stages a copy of [item] in the app-managed Recycle Bin before deletion from MediaStore.
     * For app-owned imported items, it removes the imported record directly once staged.
     */
    suspend fun stageToRecycleBin(item: MediaItem, retentionDays: Int): TrashEntity? = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val ext = item.displayName.substringAfterLast('.', if (item.mediaType == MediaType.VIDEO) "mp4" else "jpg")
        val destFile = File(trashDir, "$id.$ext")
        val thumbFile = File(trashDir, "${id}_thumb.jpg")

        val copied = runCatching {
            context.contentResolver.openInputStream(Uri.parse(item.contentUri))?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            destFile.exists() && destFile.length() > 0L
        }.getOrDefault(false)

        if (!copied) {
            destFile.delete()
            return@withContext null
        }

        // Generate thumbnail for fast Recycle Bin browsing
        val thumbSaved = runCatching {
            val bmp = createThumbnailBitmap(destFile, item.mediaType == MediaType.VIDEO, 360)
            if (bmp != null) {
                FileOutputStream(thumbFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                bmp.recycle()
                true
            } else false
        }.getOrDefault(false)

        val now = System.currentTimeMillis()
        val expiresAt = now + retentionDays.toLong() * 86_400_000L
        val entity = TrashEntity(
            id = id,
            originalUri = item.contentUri,
            displayName = item.displayName,
            mimeType = item.mimeType,
            isVideo = item.mediaType == MediaType.VIDEO,
            trashedFilePath = destFile.absolutePath,
            trashedThumbPath = if (thumbSaved) thumbFile.absolutePath else null,
            sizeBytes = destFile.length(),
            width = item.width,
            height = item.height,
            durationMs = item.durationMs,
            trashedAtMs = now,
            expiresAtMs = expiresAt
        )
        dao.insertTrashItem(entity)
        if (item.isImportedPickerItem) {
            mediaRepository.removeImportedMediaRecord(item.mediaKey, item.contentUri)
            dao.removeMediaFromAllAlbums(item.mediaKey)
            dao.removeFavorite(item.mediaKey)
            dao.removeRecentlyViewed(item.mediaKey)
        }
        entity
    }

    suspend fun rollbackPendingTrash(ids: List<String>) = withContext(Dispatchers.IO) {
        // Called if user cancels the system MediaStore delete consent dialog
        val current = dao.getExpiredTrashItems(Long.MAX_VALUE).filter { it.id in ids }
        for (item in current) {
            File(item.trashedFilePath).delete()
            item.trashedThumbPath?.let { File(it).delete() }
            dao.deleteTrashItem(item.id)
        }
    }

    suspend fun restoreFromRecycleBin(item: TrashItem): Boolean = withContext(Dispatchers.IO) {
        val file = File(item.trashedFilePath)
        if (!file.exists()) {
            dao.deleteTrashItem(item.id)
            return@withContext false
        }
        val bytes = file.readBytes()
        val restoredUri = mediaRepository.saveBytesToGallery(
            bytes = bytes,
            displayName = item.displayName,
            mimeType = item.mimeType,
            isVideo = item.mediaType == MediaType.VIDEO
        )
        if (restoredUri != null) {
            file.delete()
            item.trashedThumbPath?.let { File(it).delete() }
            dao.deleteTrashItem(item.id)
            true
        } else {
            false
        }
    }

    suspend fun permanentlyDeleteTrashItem(item: TrashItem) = withContext(Dispatchers.IO) {
        File(item.trashedFilePath).delete()
        item.trashedThumbPath?.let { File(it).delete() }
        dao.deleteTrashItem(item.id)
    }

    suspend fun emptyRecycleBin() = withContext(Dispatchers.IO) {
        trashDir.listFiles()?.forEach { it.delete() }
        dao.clearTrashTable()
    }

    suspend fun purgeExpiredTrash() = withContext(Dispatchers.IO) {
        val expired = dao.getExpiredTrashItems(System.currentTimeMillis())
        for (item in expired) {
            File(item.trashedFilePath).delete()
            item.trashedThumbPath?.let { File(it).delete() }
            dao.deleteTrashItem(item.id)
        }
    }

    fun getTrashTotalBytes(): Long {
        return trashDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    // --- Private Vault Security & Storage ---

    fun isVaultConfigured(): Boolean = securityConfigFile.exists()

    suspend fun setupVaultPin(pin: String): String = withContext(Dispatchers.IO) {
        val pinSalt = cryptoManager.generateSalt()
        val pinHash = cryptoManager.hashSecret(pin, pinSalt)
        val recoveryKey = cryptoManager.generateRecoveryKey()
        val recSalt = cryptoManager.generateSalt()
        val recHash = cryptoManager.hashSecret(normalizeRecoveryKey(recoveryKey), recSalt)

        val config = VaultSecurityConfig(
            pinSaltBase64 = Base64.getEncoder().encodeToString(pinSalt),
            pinHashBase64 = Base64.getEncoder().encodeToString(pinHash),
            recoverySaltBase64 = Base64.getEncoder().encodeToString(recSalt),
            recoveryHashBase64 = Base64.getEncoder().encodeToString(recHash),
            createdAtMs = System.currentTimeMillis()
        )
        val encrypted = cryptoManager.encryptBytes(json.encodeToString(config).toByteArray())
        securityConfigFile.writeBytes(encrypted)
        if (!manifestFile.exists()) {
            saveManifest(VaultManifest(emptyList()))
        }
        loadManifestInternal()
        recoveryKey
    }

    suspend fun verifyVaultPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        val config = loadSecurityConfig() ?: return@withContext false
        val salt = Base64.getDecoder().decode(config.pinSaltBase64)
        val expected = Base64.getDecoder().decode(config.pinHashBase64)
        val ok = cryptoManager.verifySecret(pin, salt, expected)
        if (ok) {
            loadManifestInternal()
        }
        ok
    }

    suspend fun resetPinWithRecoveryKey(recoveryKey: String, newPin: String): Boolean = withContext(Dispatchers.IO) {
        val config = loadSecurityConfig() ?: return@withContext false
        val recSalt = Base64.getDecoder().decode(config.recoverySaltBase64)
        val expectedRec = Base64.getDecoder().decode(config.recoveryHashBase64)
        if (!cryptoManager.verifySecret(normalizeRecoveryKey(recoveryKey), recSalt, expectedRec)) {
            return@withContext false
        }
        val newSalt = cryptoManager.generateSalt()
        val newHash = cryptoManager.hashSecret(newPin, newSalt)
        val updated = config.copy(
            pinSaltBase64 = Base64.getEncoder().encodeToString(newSalt),
            pinHashBase64 = Base64.getEncoder().encodeToString(newHash)
        )
        securityConfigFile.writeBytes(cryptoManager.encryptBytes(json.encodeToString(updated).toByteArray()))
        loadManifestInternal()
        true
    }

    suspend fun changeVaultPin(currentPin: String, newPin: String): Boolean = withContext(Dispatchers.IO) {
        val config = loadSecurityConfig() ?: return@withContext false
        val salt = Base64.getDecoder().decode(config.pinSaltBase64)
        val expected = Base64.getDecoder().decode(config.pinHashBase64)
        if (!cryptoManager.verifySecret(currentPin, salt, expected)) return@withContext false

        val newSalt = cryptoManager.generateSalt()
        val newHash = cryptoManager.hashSecret(newPin, newSalt)
        val updated = config.copy(
            pinSaltBase64 = Base64.getEncoder().encodeToString(newSalt),
            pinHashBase64 = Base64.getEncoder().encodeToString(newHash)
        )
        securityConfigFile.writeBytes(cryptoManager.encryptBytes(json.encodeToString(updated).toByteArray()))
        true
    }

    suspend fun regenerateRecoveryKey(): String? = withContext(Dispatchers.IO) {
        val config = loadSecurityConfig() ?: return@withContext null
        val recoveryKey = cryptoManager.generateRecoveryKey()
        val recSalt = cryptoManager.generateSalt()
        val recHash = cryptoManager.hashSecret(normalizeRecoveryKey(recoveryKey), recSalt)
        val updated = config.copy(
            recoverySaltBase64 = Base64.getEncoder().encodeToString(recSalt),
            recoveryHashBase64 = Base64.getEncoder().encodeToString(recHash)
        )
        securityConfigFile.writeBytes(cryptoManager.encryptBytes(json.encodeToString(updated).toByteArray()))
        recoveryKey
    }

    suspend fun reloadVaultOnBiometricUnlock() = withContext(Dispatchers.IO) {
        loadManifestInternal()
    }

    fun clearUnlockedSessionMemory() {
        _vaultItems.value = emptyList()
    }

    suspend fun importToVault(item: MediaItem): VaultItem? = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val dataFileName = "v_${id}.mgv"
        val thumbFileName = "vt_${id}.mgt"
        val dataFile = File(vaultDir, dataFileName)
        val thumbFile = File(vaultDir, thumbFileName)

        val uri = Uri.parse(item.contentUri)
        val (sha256, plainSize) = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                cryptoManager.encryptStreamToFile(input, dataFile)
            } ?: return@withContext null
        }.getOrElse {
            cryptoManager.secureDelete(dataFile)
            return@withContext null
        }

        if (!dataFile.exists() || dataFile.length() <= 16L || plainSize <= 0L) {
            cryptoManager.secureDelete(dataFile)
            return@withContext null
        }

        // Generate & encrypt thumbnail into memory -> encrypted bytes
        runCatching {
            val bmp = createThumbnailBitmapFromUri(uri, item.mediaType == MediaType.VIDEO, 400)
            if (bmp != null) {
                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 84, baos)
                bmp.recycle()
                thumbFile.writeBytes(cryptoManager.encryptBytes(baos.toByteArray()))
            }
        }

        val vaultItem = VaultItem(
            id = id,
            originalName = item.displayName,
            mimeType = item.mimeType,
            isVideo = item.mediaType == MediaType.VIDEO,
            encryptedFileName = dataFileName,
            encryptedThumbName = thumbFileName,
            sizeBytes = plainSize,
            width = item.width,
            height = item.height,
            durationMs = item.durationMs,
            dateAddedMs = System.currentTimeMillis(),
            dateOriginalTakenMs = item.effectiveTimestampMs,
            sha256Hash = sha256
        )

        val current = loadManifestInternal().toMutableList()
        current.add(0, vaultItem)
        saveManifest(VaultManifest(current))
        _vaultItems.value = current
        vaultItem
    }

    suspend fun decryptVaultThumbnail(item: VaultItem): ByteArray? = withContext(Dispatchers.IO) {
        val thumbFile = File(vaultDir, item.encryptedThumbName)
        if (!thumbFile.exists()) return@withContext null
        runCatching { cryptoManager.decryptBytes(thumbFile.readBytes()) }.getOrNull()
    }

    suspend fun decryptVaultPhotoBytes(item: VaultItem): ByteArray? = withContext(Dispatchers.IO) {
        val dataFile = File(vaultDir, item.encryptedFileName)
        if (!dataFile.exists()) return@withContext null
        runCatching { cryptoManager.decryptFileToBytes(dataFile) }.getOrNull()
    }

    suspend fun exportFromVault(item: VaultItem): Uri? = withContext(Dispatchers.IO) {
        val dataFile = File(vaultDir, item.encryptedFileName)
        if (!dataFile.exists()) return@withContext null
        val plainBytes = runCatching { cryptoManager.decryptFileToBytes(dataFile) }.getOrNull()
            ?: return@withContext null
        mediaRepository.saveBytesToGallery(
            bytes = plainBytes,
            displayName = item.originalName,
            mimeType = item.mimeType,
            isVideo = item.isVideo
        )
    }

    suspend fun removeFromVault(item: VaultItem) = withContext(Dispatchers.IO) {
        cryptoManager.secureDelete(File(vaultDir, item.encryptedFileName))
        cryptoManager.secureDelete(File(vaultDir, item.encryptedThumbName))
        val updated = loadManifestInternal().filterNot { it.id == item.id }
        saveManifest(VaultManifest(updated))
        _vaultItems.value = updated
    }

    fun getVaultTotalBytes(): Long {
        return vaultDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun loadSecurityConfig(): VaultSecurityConfig? {
        if (!securityConfigFile.exists()) return null
        return runCatching {
            val decrypted = cryptoManager.decryptBytes(securityConfigFile.readBytes())
            json.decodeFromString<VaultSecurityConfig>(String(decrypted))
        }.getOrNull()
    }

    private fun loadManifestInternal(): List<VaultItem> {
        if (!manifestFile.exists()) {
            _vaultItems.value = emptyList()
            return emptyList()
        }
        val items = runCatching {
            val decrypted = cryptoManager.decryptBytes(manifestFile.readBytes())
            json.decodeFromString<VaultManifest>(String(decrypted)).items
        }.getOrDefault(emptyList())
        _vaultItems.value = items
        return items
    }

    private fun saveManifest(manifest: VaultManifest) {
        val encoded = json.encodeToString(manifest).toByteArray()
        manifestFile.writeBytes(cryptoManager.encryptBytes(encoded))
    }

    private fun normalizeRecoveryKey(input: String): String {
        return input.uppercase().replace("-", "").replace(" ", "").trim()
    }

    private fun createThumbnailBitmap(file: File, isVideo: Boolean, maxDim: Int): Bitmap? {
        if (isVideo) {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                retriever.frameAtTime?.let { scaleBitmapDown(it, maxDim) }
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim, maxDim)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)?.let { scaleBitmapDown(it, maxDim) }
    }

    private fun createThumbnailBitmapFromUri(uri: Uri, isVideo: Boolean, maxDim: Int): Bitmap? {
        if (isVideo) {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, uri)
                retriever.frameAtTime?.let { scaleBitmapDown(it, maxDim) }
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim, maxDim)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        return scaleBitmapDown(bmp, maxDim)
    }

    private fun scaleBitmapDown(bmp: Bitmap, maxDim: Int): Bitmap {
        val maxSide = maxOf(bmp.width, bmp.height)
        if (maxSide <= maxDim || maxSide == 0) return bmp
        val ratio = maxDim.toFloat() / maxSide
        val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        if (scaled != bmp) bmp.recycle()
        return scaled
    }

    private fun computeInSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (height > reqH || width > reqW) {
            val halfH = height / 2
            val halfW = width / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
