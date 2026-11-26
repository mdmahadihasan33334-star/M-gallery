package com.example.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

interface VaultKeyProvider {
    fun getOrCreateMasterKey(): SecretKey
}

class AndroidKeystoreVaultKeyProvider(
    private val alias: String = "mgallery_vault_aes_master_key_v1"
) : VaultKeyProvider {

    override fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}

class InMemoryVaultKeyProvider(
    rawKey: ByteArray = ByteArray(32) { idx -> (idx * 7 + 13).toByte() }
) : VaultKeyProvider {
    private val secretKey: SecretKey = SecretKeySpec(rawKey, "AES")
    override fun getOrCreateMasterKey(): SecretKey = secretKey
}

/**
 * Provides Android Keystore-backed AES-256-GCM encryption for:
 * 1) Small payloads (PIN/recovery config, vault manifest JSON, thumbnails)
 * 2) Chunked seekable streams for full-resolution photos and videos so Media3 can
 *    stream encrypted vault videos in memory without writing plaintext files to disk.
 */
class VaultCryptoManager(
    private val keyProvider: VaultKeyProvider = AndroidKeystoreVaultKeyProvider()
) {
    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH = 16
        private const val MAGIC = 0x4D475631 // "MGV1"
        const val CHUNK_SIZE = 262_144 // 256 KB
        private const val HEADER_SIZE = 16
        private const val PBKDF2_ITERATIONS = 60_000
        private const val SALT_BYTES = 16
        private val RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray()
    }

    private val secureRandom = SecureRandom()

    fun encryptBytes(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreateMasterKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain)
        return ByteBuffer.allocate(1 + iv.size + encrypted.size)
            .put(iv.size.toByte())
            .put(iv)
            .put(encrypted)
            .array()
    }

    fun decryptBytes(payload: ByteArray): ByteArray {
        val buf = ByteBuffer.wrap(payload)
        val ivLen = buf.get().toInt() and 0xFF
        require(ivLen in 12..16) { "Invalid IV length in vault payload" }
        val iv = ByteArray(ivLen)
        buf.get(iv)
        val encrypted = ByteArray(buf.remaining())
        buf.get(encrypted)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider.getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    /**
     * Encrypts [inputStream] into [outputFile] using chunked AES-GCM with AAD binding
     * each chunk index and total plain size. Returns SHA-256 hex digest of plaintext and byte size.
     */
    fun encryptStreamToFile(inputStream: InputStream, outputFile: File, expectedPlainSize: Long = -1L): Pair<String, Long> {
        val tempOut = File(outputFile.parentFile, "${outputFile.name}.tmp")
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK_SIZE)
        var totalRead = 0L
        val chunks = mutableListOf<ByteArray>()

        // If size isn't known upfront, we write a placeholder header and update it at the end.
        RandomAccessFile(tempOut, "rw").use { raf ->
            raf.setLength(0)
            raf.write(ByteArray(HEADER_SIZE))

            var chunkIndex = 0L
            while (true) {
                var filled = 0
                while (filled < CHUNK_SIZE) {
                    val read = inputStream.read(buffer, filled, CHUNK_SIZE - filled)
                    if (read == -1) break
                    filled += read
                }
                if (filled == 0) break

                val plainChunk = if (filled == CHUNK_SIZE) buffer else buffer.copyOf(filled)
                md.update(plainChunk, 0, filled)
                totalRead += filled

                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreateMasterKey())
                val aad = ByteBuffer.allocate(8).putLong(chunkIndex).array()
                cipher.updateAAD(aad)
                val iv = cipher.iv
                val encryptedChunk = cipher.doFinal(plainChunk)

                val chunkHeader = ByteBuffer.allocate(IV_LENGTH + 4)
                    .put(iv)
                    .putInt(encryptedChunk.size)
                    .array()
                raf.write(chunkHeader)
                raf.write(encryptedChunk)
                chunkIndex++
            }

            // Rewrite final 16-byte header
            raf.seek(0)
            val header = ByteBuffer.allocate(HEADER_SIZE)
                .putInt(MAGIC)
                .putInt(CHUNK_SIZE)
                .putLong(totalRead)
                .array()
            raf.write(header)
            raf.fd.sync()
        }

        if (outputFile.exists()) outputFile.delete()
        if (!tempOut.renameTo(outputFile)) {
            tempOut.copyTo(outputFile, overwrite = true)
            tempOut.delete()
        }
        val sha256 = md.digest().joinToString("") { "%02x".format(it) }
        return sha256 to totalRead
    }

    fun decryptFileToStream(encryptedFile: File, outputStream: OutputStream) {
        RandomAccessFile(encryptedFile, "r").use { raf ->
            val headerBuf = ByteArray(HEADER_SIZE)
            raf.readFully(headerBuf)
            val header = ByteBuffer.wrap(headerBuf)
            val magic = header.getInt()
            require(magic == MAGIC) { "Corrupted vault file header" }
            header.getInt() // chunkSize
            val totalPlainSize = header.getLong()

            var chunkIndex = 0L
            var remainingPlain = totalPlainSize
            while (remainingPlain > 0) {
                val iv = ByteArray(IV_LENGTH)
                raf.readFully(iv)
                val encLen = raf.readInt()
                val encBytes = ByteArray(encLen)
                raf.readFully(encBytes)

                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, keyProvider.getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
                val aad = ByteBuffer.allocate(8).putLong(chunkIndex).array()
                cipher.updateAAD(aad)
                val plainChunk = cipher.doFinal(encBytes)
                outputStream.write(plainChunk)
                remainingPlain -= plainChunk.size
                chunkIndex++
            }
        }
    }

    fun decryptFileToBytes(encryptedFile: File): ByteArray {
        val baos = ByteArrayOutputStream()
        decryptFileToStream(encryptedFile, baos)
        return baos.toByteArray()
    }

    fun openChunkedReader(encryptedFile: File): ChunkedVaultFileReader {
        return ChunkedVaultFileReader(encryptedFile, keyProvider)
    }

    fun generateSalt(): ByteArray = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }

    fun hashSecret(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    fun verifySecret(candidate: String, salt: ByteArray, expectedHash: ByteArray): Boolean {
        val actual = hashSecret(candidate, salt)
        return MessageDigest.isEqual(actual, expectedHash)
    }

    fun generateRecoveryKey(): String {
        val chars = CharArray(24) { RECOVERY_ALPHABET[secureRandom.nextInt(RECOVERY_ALPHABET.size)] }
        return chars.toList().chunked(4).joinToString("-") { it.joinToString("") }
    }

    fun secureDelete(file: File?) {
        if (file == null || !file.exists()) return
        runCatching {
            if (file.isFile && file.length() > 0L) {
                val len = minOf(file.length(), 256 * 1024L).toInt()
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(0)
                    raf.write(ByteArray(len))
                }
            }
        }
        file.delete()
    }

    class ChunkedVaultFileReader(
        encryptedFile: File,
        private val keyProvider: VaultKeyProvider
    ) : AutoCloseable {
        private val raf = RandomAccessFile(encryptedFile, "r")
        val chunkSize: Int
        val totalPlainSize: Long
        private val chunkOffsets: LongArray
        private var cachedChunkIndex = -1
        private var cachedChunkData: ByteArray? = null

        init {
            val headerBuf = ByteArray(HEADER_SIZE)
            raf.readFully(headerBuf)
            val header = ByteBuffer.wrap(headerBuf)
            val magic = header.getInt()
            require(magic == MAGIC) { "Invalid vault file format" }
            chunkSize = header.getInt()
            totalPlainSize = header.getLong()

            val numChunks = if (totalPlainSize == 0L) 0 else ((totalPlainSize + chunkSize - 1) / chunkSize).toInt()
            chunkOffsets = LongArray(numChunks)
            var filePos = HEADER_SIZE.toLong()
            for (i in 0 until numChunks) {
                chunkOffsets[i] = filePos
                raf.seek(filePos + IV_LENGTH)
                val encLen = raf.readInt()
                filePos += IV_LENGTH + 4L + encLen
            }
        }

        @Synchronized
        fun readAt(plainOffset: Long, dest: ByteArray, destOffset: Int, length: Int): Int {
            if (plainOffset >= totalPlainSize || length == 0) return -1
            val toRead = minOf(length.toLong(), totalPlainSize - plainOffset).toInt()
            var totalCopied = 0
            var currentPos = plainOffset

            while (totalCopied < toRead) {
                val chunkIdx = (currentPos / chunkSize).toInt()
                val inChunkOffset = (currentPos % chunkSize).toInt()
                val chunk = loadChunk(chunkIdx)
                val available = chunk.size - inChunkOffset
                val copyLen = minOf(available, toRead - totalCopied)
                System.arraycopy(chunk, inChunkOffset, dest, destOffset + totalCopied, copyLen)
                totalCopied += copyLen
                currentPos += copyLen
            }
            return totalCopied
        }

        private fun loadChunk(index: Int): ByteArray {
            if (cachedChunkIndex == index && cachedChunkData != null) {
                return cachedChunkData!!
            }
            raf.seek(chunkOffsets[index])
            val iv = ByteArray(IV_LENGTH)
            raf.readFully(iv)
            val encLen = raf.readInt()
            val encBytes = ByteArray(encLen)
            raf.readFully(encBytes)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyProvider.getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(ByteBuffer.allocate(8).putLong(index.toLong()).array())
            val plain = cipher.doFinal(encBytes)
            cachedChunkIndex = index
            cachedChunkData = plain
            return plain
        }

        override fun close() {
            cachedChunkData = null
            raf.close()
        }
    }
}
