package sanctuary.app.feature.dump.data.preferences

import platform.Foundation.NSUserDefaults
import platform.Foundation.standardUserDefaults

actual class PermissionsPreferences {
    private val userDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults

    actual fun isMicrophonePermissionGranted(): Boolean =
        userDefaults.boolForKey(KEY_MICROPHONE_PERMISSION)

    actual fun setMicrophonePermissionGranted(granted: Boolean) {
        userDefaults.setBool(granted, KEY_MICROPHONE_PERMISSION)
        userDefaults.synchronize()
    }

    companion object {
        private const val KEY_MICROPHONE_PERMISSION = "microphone_permission"
    }
}
