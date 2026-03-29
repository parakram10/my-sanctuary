package sanctuary.app.feature.dump.domain.preferences

expect class PermissionsPreferences {
    fun isMicrophonePermissionGranted(): Boolean
    fun setMicrophonePermissionGranted(granted: Boolean)
}
