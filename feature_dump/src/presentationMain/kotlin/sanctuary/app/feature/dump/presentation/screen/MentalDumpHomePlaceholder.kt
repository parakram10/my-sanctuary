package sanctuary.app.feature.dump.presentation.screen

import androidx.compose.runtime.Composable

@Composable
fun MentalDumpHomePlaceholder(
    onNavigateBack: () -> Unit = {},
    onViewAllRecordings: () -> Unit = {},
    onRequestPermission: (onPermissionResult: (Boolean) -> Unit) -> Unit = { onResult -> onResult(false) },
) {
    DumpRecordingScreen(
        onNavigateBack = onNavigateBack,
        onViewAllRecordings = onViewAllRecordings,
        onRequestPermission = onRequestPermission,
    )
}
