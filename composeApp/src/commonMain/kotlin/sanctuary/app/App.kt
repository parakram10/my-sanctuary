package sanctuary.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sanctuary.app.core.ui.theme.SanctuaryTheme
import sanctuary.app.feature.dump.presentation.screen.MentalDumpHomePlaceholder
import sanctuary.app.feature.history.presentation.screen.HistoryHomePlaceholder
import sanctuary.app.feature.summary.presentation.screen.SummaryHomePlaceholder

@Composable
fun App() {
    SanctuaryTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SanctuaryRootShell()
        }
    }
}

@Composable
private fun SanctuaryRootShell() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Sanctuary",
            modifier = Modifier.padding(bottom = 16.dp),
        )
        MentalDumpHomePlaceholder()
        SummaryHomePlaceholder()
        HistoryHomePlaceholder()
    }
}
