package com.example.data.security

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.IOException

/**
 * Custom Media3 DataSource that decrypts AES-256-GCM chunked vault video files
 * on the fly in memory while supporting arbitrary seek positions.
 */
@UnstableApi
class EncryptedVaultDataSource(
    private val vaultDir: File,
    private val cryptoManager: VaultCryptoManager
) : BaseDataSource(/* isNetwork = */ false) {

    private var reader: VaultCryptoManager.ChunkedVaultFileReader? = null
    private var currentUri: Uri? = null
    private var readPosition = 0L
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        currentUri = dataSpec.uri
        val fileName = dataSpec.uri.lastPathSegment
            ?: throw IOException("Missing encrypted vault file name")
        val targetFile = File(vaultDir, fileName).canonicalFile
        val canonicalVaultDir = vaultDir.canonicalFile
        if (!targetFile.path.startsWith(canonicalVaultDir.path + File.separator)) {
            throw SecurityException("Invalid vault file path")
        }
        if (!targetFile.exists()) {
            throw IOException("Encrypted vault file not found")
        }

        val chunkReader = cryptoManager.openChunkedReader(targetFile)
        reader = chunkReader
        readPosition = dataSpec.position
        val total = chunkReader.totalPlainSize
        if (readPosition > total) {
            throw IOException("Seek position out of range")
        }
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            minOf(dataSpec.length, total - readPosition)
        } else {
            total - readPosition
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val activeReader = reader ?: return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        val read = activeReader.readAt(readPosition, buffer, offset, toRead)
        if (read <= 0) return C.RESULT_END_OF_INPUT
        readPosition += read
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        currentUri = null
        try {
            reader?.close()
        } finally {
            reader = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory(
        private val vaultDir: File,
        private val cryptoManager: VaultCryptoManager
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return EncryptedVaultDataSource(vaultDir, cryptoManager)
        }
    }
}
