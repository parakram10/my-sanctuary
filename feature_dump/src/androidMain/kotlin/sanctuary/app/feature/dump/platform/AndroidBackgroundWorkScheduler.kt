package sanctuary.app.feature.dump.platform

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import sanctuary.app.feature.dump.domain.scheduling.BackgroundWorkScheduler
import java.util.concurrent.TimeUnit

internal class AndroidBackgroundWorkScheduler(
    private val context: Context,
) : BackgroundWorkScheduler {

    override suspend fun scheduleRecordingProcessing(recordingId: String) {
        // Enqueue a unique work request for this recording
        val uniqueWorkName = "process_recording_$recordingId"

        val workRequest = OneTimeWorkRequestBuilder<RecordingProcessingWorker>()
            .addTag("recording_processing")
            .addTag(recordingId)  // For cancellation if needed
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP,  // If already queued, don't add another
            workRequest
        )
    }

    override suspend fun setup() {
        // Configure periodic job (runs even if app is closed)
        val periodicWorkRequest = PeriodicWorkRequestBuilder<RecordingProcessingWorker>(
            15,              // Every 15 minutes
            TimeUnit.MINUTES,
            5,               // Flex window: 10-15 minutes
            TimeUnit.MINUTES
        )
            .addTag("recording_processing_periodic")
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)  // Need network for insights
                    .setRequiresCharging(false)                     // Can run on battery
                    .setRequiresBatteryNotLow(false)                // Not strict on battery
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "recording_processing_periodic",
            ExistingPeriodicWorkPolicy.KEEP,  // Don't reschedule if already running
            periodicWorkRequest
        )
    }
}
