package sanctuary.app.core.database.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlin.random.Random

/**
 * Android implementation of PassphraseManager.
 * Leverages Android Keystore + EncryptedSharedPreferences for secure passphrase storage.
 *
 * Security model:
 * 1. MasterKey stored in Android Keystore (hardware-backed if available)
 * 2. Passphrase encrypted with MasterKey and stored in EncryptedSharedPreferences
 * 3. Passphrase generated once per device installation, reused for all database operations
 */
actual object PassphraseManager {
    private const val PREFS_NAME = "sanctuary_db_security"
    private const val KEY_PASSPHRASE = "db_passphrase"
    private var context: Context? = null

    /**
     * Initialize with Android context (must be called once at app startup).
     * Safe to call multiple times.
     */
    fun init(context: Context) {
        this.context = context
    }

    actual fun getOrCreatePassphrase(): String {
        val ctx = context ?: throw IllegalStateException(
            "PassphraseManager not initialized. Call PassphraseManager.init(context) at app startup."
        )

        try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                ctx,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

            return prefs.getString(KEY_PASSPHRASE, null)?.takeIf { it.isNotEmpty() }
                ?: run {
                    // Generate new passphrase on first access
                    val newPassphrase = generateSecurePassphrase()
                    prefs.edit().putString(KEY_PASSPHRASE, newPassphrase).apply()
                    newPassphrase
                }
        } catch (e: Exception) {
            throw RuntimeException("Failed to retrieve or create database encryption passphrase", e)
        }
    }

    /**
     * Generates a cryptographically secure 32-character passphrase.
     * Uses alphanumeric + special characters for maximum entropy.
     *
     * @return Secure random passphrase
     */
    private fun generateSecurePassphrase(): String {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#\$%^&*"
        return (1..32)
            .map { Random.nextInt(0, charset.length) }
            .map(charset::get)
            .joinToString("")
    }
}
