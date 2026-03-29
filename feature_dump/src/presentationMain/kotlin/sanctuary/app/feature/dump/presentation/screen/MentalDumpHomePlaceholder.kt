package sanctuary.app.feature.dump.presentation.screen

import androidx.compose.runtime.Composable

@Composable
fun MentalDumpHomePlaceholder(
    onNavigateBack: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
) {
    DumpRecordingScreen(
        onNavigateBack = onNavigateBack,
        onRequestPermission = onRequestPermission,
    )
}
