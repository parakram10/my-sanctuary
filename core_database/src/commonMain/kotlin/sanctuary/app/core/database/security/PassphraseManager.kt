package sanctuary.app.core.database.security

/**
 * Manages database encryption passphrase with platform-specific secure storage.
 *
 * Security Considerations:
 * - Passphrases are never stored in plain text
 * - Android: Uses Android Keystore for secure storage
 * - iOS: Uses iOS Keychain for secure storage
 * - Passphrases are generated once and reused per device/installation
 */
expect object PassphraseManager {
    /**
     * Retrieves or generates the database encryption passphrase.
     *
     * First call: Generates a new random passphrase and stores securely
     * Subsequent calls: Retrieves the stored passphrase from secure storage
     *
     * @return The database encryption passphrase (32+ characters)
     * @throws IllegalStateException if passphrase cannot be generated or retrieved
     */
    fun getOrCreatePassphrase(): String
}
