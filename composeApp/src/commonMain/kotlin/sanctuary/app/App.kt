package sanctuary.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.koin.compose.KoinMultiplatformApplication
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinConfiguration
import sanctuary.app.core.ui.theme.SanctuaryTheme
import sanctuary.app.di.allAppModules
import sanctuary.app.feature.dump.presentation.screen.MentalDumpHomePlaceholder
import sanctuary.app.feature.history.presentation.screen.HistoryHomePlaceholder
import sanctuary.app.feature.summary.presentation.screen.SummaryHomePlaceholder

@Composable
@OptIn(KoinExperimentalAPI::class)
fun App(
    onRequestPermission: ((onPermissionResult: (Boolean) -> Unit) -> Unit)? = null,
) {
    KoinMultiplatformApplication(
        config = koinConfiguration {
            modules(allAppModules())
        },
    ) {
        SanctuaryTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                SanctuaryRootShell(onRequestPermission = onRequestPermission)
            }
        }
    }
}

@Composable
private fun SanctuaryRootShell(
    onRequestPermission: ((onPermissionResult: (Boolean) -> Unit) -> Unit)? = null,
) {
    var showPermissionDialog by remember { mutableStateOf(false) }
    var pendingPermissionCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MentalDumpHomePlaceholder(
            onRequestPermission = { onResult ->
                pendingPermissionCallback = onResult
                showPermissionDialog = true
            },
        )
        SummaryHomePlaceholder()
        HistoryHomePlaceholder()
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                showPermissionDialog = false
                pendingPermissionCallback?.invoke(false)
                pendingPermissionCallback = null
            },
            title = { Text("Microphone Permission") },
            text = { Text("Sanctuary needs microphone access to record your mental dump.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        val callback = pendingPermissionCallback
                        pendingPermissionCallback = null
                        if (callback != null && onRequestPermission != null) {
                            onRequestPermission { granted -> callback(granted) }
                        } else {
                            callback?.invoke(false)
                        }
                    },
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        pendingPermissionCallback?.invoke(false)
                        pendingPermissionCallback = null
                    },
                ) {
                    Text("Not now")
                }
            },
        )
    }
}
