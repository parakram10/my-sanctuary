package sanctuary.app.feature.dump.presentation.screen.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import sanctuary.app.core.ui.theme.SanctuaryPalette

private val IdleAmplitudes = listOf(
    0.2f, 0.4f, 0.5f, 0.6f, 0.8f, 0.9f, 0.7f, 0.5f, 0.3f, 0.2f,
    0.3f, 0.5f, 0.7f, 0.9f, 0.8f, 0.6f, 0.5f, 0.4f, 0.3f, 0.2f,
)

@Composable
fun WaveformVisualizer(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = SanctuaryPalette.OnNeutralMuted.copy(alpha = 0.5f),
) {
    val displayAmplitudes = if (amplitudes.isEmpty()) IdleAmplitudes else amplitudes

    Canvas(modifier = modifier) {
        val barCount = displayAmplitudes.size
        val totalWidth = size.width
        val totalHeight = size.height
        val barWidthPx = (totalWidth / barCount) * 0.5f
        val gapPx = (totalWidth / barCount) * 0.5f
        val minBarHeightPx = 8f
        val maxBarHeightPx = totalHeight * 0.85f
        val cornerRadius = CornerRadius(barWidthPx / 2f)

        displayAmplitudes.forEachIndexed { index, amplitude ->
            val barHeightPx = minBarHeightPx + (amplitude * (maxBarHeightPx - minBarHeightPx))
            val x = index * (barWidthPx + gapPx) + gapPx / 2f
            val y = (totalHeight - barHeightPx) / 2f

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidthPx, barHeightPx),
                cornerRadius = cornerRadius,
            )
        }
    }
}
