package sanctuary.app.core.database.db

import app.cash.sqldelight.db.SqlDriver
import sanctuary.app.core.database.SanctuaryDatabase

/**
 * Creates a SqlDriver for the SanctuaryDatabase with encryption support.
 * Uses SQLCipher on Android and native SQLCipher support on iOS.
 *
 * Platform implementations:
 * - Android: Uses AndroidSqliteDriver with SQLCipher and EncryptedSharedPreferences
 * - iOS: Uses NativeSqliteDriver with SQLCipher (via Keychain)
 *
 * @param encryptionPassphrase The passphrase used to encrypt/decrypt the database
 * @return SqlDriver configured with encryption
 */
expect fun createDriver(encryptionPassphrase: String): SqlDriver

/**
 * Creates and returns a SanctuaryDatabase instance with encrypted storage.
 *
 * @param encryptionPassphrase The passphrase for database encryption
 * @return Initialized SanctuaryDatabase with encryption enabled
 */
fun createEncryptedDatabase(
    encryptionPassphrase: String,
): SanctuaryDatabase {
    val driver = createDriver(encryptionPassphrase)
    return SanctuaryDatabase(driver)
}
