package com.wealthos.database

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Random SQLCipher key wrapped by a non-exportable AES key. Never regenerates keys for an existing DB. */
class DatabaseKeyStore(private val context: Context) {
    fun loadOrCreate(): ByteArray = synchronized(KEY_LOCK) { loadOrCreateLocked() }
    private fun loadOrCreateLocked(): ByteArray {
        val file = AtomicFile(File(context.noBackupFilesDir, "database-key.v1"))
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
            val key = store.getKey(ALIAS, null) as? SecretKey ?: error("Database key protection is unavailable. Existing data has been preserved.")
            val bytes = file.openRead().use { it.readBytes() }
            require(bytes.size == 61 && bytes[0] == 1.toByte()) { "Invalid encrypted database key. Existing data has been preserved." }
            return Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
                updateAAD(AAD)
                doFinal(bytes.copyOfRange(13, bytes.size))
            }
        }
        require(!context.getDatabasePath("wealthos.db").exists()) { "The database key is missing. Existing data has been preserved." }
        val key = (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key); updateAAD(AAD)
            }
            val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(secret)
            val output = file.startWrite()
            try { output.write(encrypted); file.finishWrite(output) }
            catch (error: Exception) { file.failWrite(output); throw error }
            return secret
        } catch (error: Exception) { secret.fill(0); throw error }
    }
    companion object {
        // Different ViewModel/loader instances must not race the initial AtomicFile creation.
        private val KEY_LOCK = Any()
        private const val ALIAS = "wealthos.database.wrap.v1"
        private val AAD = "WealthOS database key v1".toByteArray(Charsets.UTF_8)
    }
}
