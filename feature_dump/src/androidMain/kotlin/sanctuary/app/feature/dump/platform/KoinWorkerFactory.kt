package sanctuary.app.feature.dump.platform

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import sanctuary.app.feature.dump.data.processing.RecordingProcessingEngine
import sanctuary.app.feature.dump.domain.repository.RecordingRepository

/**
 * Koin-aware WorkerFactory that enables dependency injection for WorkManager workers.
 *
 * Registers with WorkManager to create workers with Koin-injected dependencies.
 */
internal class KoinWorkerFactory : WorkerFactory(), KoinComponent {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        return when (workerClassName) {
            RecordingProcessingWorker::class.java.name -> {
                val recordingRepository: RecordingRepository by inject()
                val processingEngine: RecordingProcessingEngine by inject()

                RecordingProcessingWorker(
                    appContext,
                    workerParameters,
                    recordingRepository,
                    processingEngine
                )
            }

            else -> null  // Let WorkManager handle other workers
        }
    }
}
