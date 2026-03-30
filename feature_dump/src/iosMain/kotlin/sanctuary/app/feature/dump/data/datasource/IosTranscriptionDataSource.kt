package sanctuary.app.feature.dump.data.datasource

import kotlinx.coroutines.delay
import sanctuary.app.feature.dump.platform.IosSpeechRecognitionManager

internal class IosTranscriptionDataSource(
    private val speechRecognitionManager: IosSpeechRecognitionManager
) {
    suspend fun getTranscript(): Result<String> {
        // Try to get transcript for approximately 3 seconds (30 * 100ms)
        repeat(30) {
            val t = speechRecognitionManager.getTranscript()
            if (t != null) return Result.success(t)
            delay(100)
        }
        return Result.failure(Exception("Failed to get transcript within 3 seconds"))
    }
}
