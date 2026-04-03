package sanctuary.app.core.database.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import sanctuary.app.core.database.SanctuaryDatabase

/**
 * Android implementation of encrypted database driver creation.
 * Uses AndroidSqliteDriver with SQLCipher for encryption.
 *
 * Note: Full SQLCipher encryption integration requires:
 * 1. Custom SQLDelight driver wrapper that applies PRAGMA key
 * 2. Or integration with sqlcipher-android through custom SqlDriver implementation
 *
 * Current implementation:
 * - Creates AndroidSqliteDriver with standard SQLite
 * - TODO: Wrap with SQLCipher encryption layer using custom SqlDriver
 */
private var androidContext: Context? = null

/**
 * Initialize with Android application context.
 * Must be called once at app startup before creating encrypted database.
 */
fun setAndroidContext(context: Context) {
    androidContext = context
}

actual fun createDriver(encryptionPassphrase: String): SqlDriver {
    val context = androidContext ?: throw IllegalStateException(
        "Android context not set. Call setAndroidContext(context) before creating encrypted database."
    )

    // Create Android SQLite driver
    // TODO: Wrap with SQLCipher encryption layer using custom SqlDriver
    // that applies PRAGMA key = '...' before schema access
    return AndroidSqliteDriver(
        schema = SanctuaryDatabase.Schema,
        context = context,
        name = "sanctuary.db",
    )
}
