package sanctuary.app.feature.dump.data.datasource

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sanctuary.app.feature.dump.platform.AndroidSpeechRecognitionManager
import java.util.concurrent.TimeoutException

internal class AndroidTranscriptionDataSource(
    private val speechRecognitionManager: AndroidSpeechRecognitionManager
) {
    suspend fun getTranscript(): Result<String> = withContext(Dispatchers.IO) {
        val deadline = SystemClock.elapsedRealtime() + 3000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val t = speechRecognitionManager.getTranscript()
            if (t != null) return@withContext Result.success(t)
            delay(100)
        }
        Result.failure(TimeoutException("Speech recognition timed out"))
    }
}
