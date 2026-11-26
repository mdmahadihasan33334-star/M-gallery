package com.example

import com.example.data.security.InMemoryVaultKeyProvider
import com.example.data.security.VaultCryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class VaultCryptoManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val crypto = VaultCryptoManager(InMemoryVaultKeyProvider())

    @Test
    fun `encryptBytes and decryptBytes roundtrip`() {
        val secret = "M Gallery Vault Manifest".toByteArray()
        val encrypted = crypto.encryptBytes(secret)
        assertFalse(secret.contentEquals(encrypted))
        val decrypted = crypto.decryptBytes(encrypted)
        assertArrayEquals(secret, decrypted)
    }

    @Test
    fun `chunked stream encryption supports full decrypt and arbitrary seek reads`() {
        // Larger than 2 chunks (2 * 256KB + 1234 bytes)
        val plainSize = VaultCryptoManager.CHUNK_SIZE * 2 + 1234
        val original = ByteArray(plainSize) { idx -> (idx % 251).toByte() }
        val encFile = tempFolder.newFile("vault_video.mgv")

        val (_, writtenSize) = crypto.encryptStreamToFile(ByteArrayInputStream(original), encFile)
        assertTrue(writtenSize == plainSize.toLong())

        // Full decrypt
        val fullOut = ByteArrayOutputStream()
        crypto.decryptFileToStream(encFile, fullOut)
        assertArrayEquals(original, fullOut.toByteArray())

        // Seek across chunk boundary
        crypto.openChunkedReader(encFile).use { reader ->
            val seekOffset = (VaultCryptoManager.CHUNK_SIZE - 100).toLong()
            val slice = ByteArray(500)
            val readCount = reader.readAt(seekOffset, slice, 0, slice.size)
            assertTrue(readCount == 500)
            val expectedSlice = original.copyOfRange(seekOffset.toInt(), seekOffset.toInt() + 500)
            assertArrayEquals(expectedSlice, slice)
        }
    }

    @Test
    fun `PIN hashing and verification rejects wrong PIN and accepts matching PIN`() {
        val salt = crypto.generateSalt()
        val hash = crypto.hashSecret("482910", salt)
        assertTrue(crypto.verifySecret("482910", salt, hash))
        assertFalse(crypto.verifySecret("123456", salt, hash))
    }

    @Test
    fun `recovery key follows formatted 24 char structure`() {
        val key = crypto.generateRecoveryKey()
        val parts = key.split("-")
        assertTrue(parts.size == 6)
        assertTrue(parts.all { it.length == 4 })
    }
}
