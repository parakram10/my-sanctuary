package sanctuary.app.core.database.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import sanctuary.app.core.database.SanctuaryDatabase

/**
 * iOS implementation of encrypted database driver creation.
 * Uses NativeSqliteDriver with SQLCipher for encryption.
 *
 * Note: Full SQLCipher encryption integration requires:
 * 1. CocoaPods installation: pod 'sqlcipher-ios'
 * 2. Native interop with sqlite3_key_v2() C function
 * 3. Proper framework linking in Xcode build
 *
 * Current implementation:
 * - Uses NativeSqliteDriver with standard SQLite
 * - TODO: Implement native sqlite3_key_v2() call for iOS encryption
 */
actual fun createDriver(encryptionPassphrase: String): SqlDriver {
    return try {
        // Create native driver
        val driver = NativeSqliteDriver(
            schema = SanctuaryDatabase.Schema,
            name = "sanctuary.db",
        )

        // TODO: Native SQLCipher integration for iOS
        // Requires proper CocoaPods setup with sqlcipher-ios pod
        // and native sqlite3_key_v2() call after database open

        driver
    } catch (e: Exception) {
        throw RuntimeException("Failed to create encrypted database driver for iOS", e)
    }
}
