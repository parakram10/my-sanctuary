package sanctuary.app.feature.dump.domain.preferences

import android.content.Context
import android.content.SharedPreferences

actual class PermissionsPreferences(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "sanctuary_dump_prefs",
        Context.MODE_PRIVATE
    )

    actual fun isMicrophonePermissionGranted(): Boolean =
        prefs.getBoolean(KEY_MICROPHONE_PERMISSION, false)

    actual fun setMicrophonePermissionGranted(granted: Boolean) {
        prefs.edit().putBoolean(KEY_MICROPHONE_PERMISSION, granted).apply()
    }

    companion object {
        private const val KEY_MICROPHONE_PERMISSION = "microphone_permission"
    }
}
