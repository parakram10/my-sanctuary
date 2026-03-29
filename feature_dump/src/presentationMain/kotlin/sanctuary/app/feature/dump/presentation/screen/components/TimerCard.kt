package sanctuary.app.feature.dump.presentation.screen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sanctuary.app.core.ui.theme.SanctuaryDimens
import sanctuary.app.core.ui.theme.SanctuaryPalette

@Composable
fun TimerCard(
    timerText: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = SanctuaryPalette.Neutral,
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "•",
            color = Color(0xFFE74C3C), // Red dot
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
            ),
        )
        Text(
            text = " $timerText",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                color = SanctuaryPalette.OnNeutral,
            ),
        )
    }
}
