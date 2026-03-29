package sanctuary.app.feature.dump.data.preferences

expect class PermissionsPreferences {
    fun isMicrophonePermissionGranted(): Boolean
    fun setMicrophonePermissionGranted(granted: Boolean)
}
