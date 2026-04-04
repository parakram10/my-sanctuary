package sanctuary.app.feature.dump.platform

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import sanctuary.app.feature.dump.data.processing.RecordingProcessingEngine
import sanctuary.app.feature.dump.domain.repository.RecordingRepository

internal class RecordingProcessingWorker(
    context: Context,
    params: WorkerParameters,
    private val recordingRepository: RecordingRepository,
    private val processingEngine: RecordingProcessingEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // STEP 1: Query eligible recordings (PENDING or FAILED with transient error)
            val eligible = recordingRepository.queryEligibleForBackgroundRetry()

            if (eligible.isEmpty()) {
                return Result.success()  // Nothing to do
            }

            // STEP 2: Process each eligible recording
            for (recording in eligible) {
                try {
                    // Increment WM attempt counter
                    recordingRepository.incrementBackgroundWmAttempts(recording.id)

                    // Call engine (same as foreground)
                    processingEngine.process(recording.id)

                    // Engine updated DB status; WM just observes
                } catch (e: Exception) {
                    // Log but continue with next recording
                }
            }

            // STEP 3: All processed; report success to WM
            Result.success()

        } catch (e: Exception) {
            // Fatal error (e.g., can't query DB); let WM retry
            Result.retry()
        }
    }
}
