package sanctuary.app.feature.dump.data.processing

import kotlinx.coroutines.coroutineScope
import sanctuary.app.feature.dump.domain.model.ProcessingErrorCode
import sanctuary.app.feature.dump.domain.model.ProcessingStatus
import sanctuary.app.feature.dump.domain.model.Recording
import sanctuary.app.feature.dump.domain.port.InsightPort
import sanctuary.app.feature.dump.domain.repository.RecordingRepository
import sanctuary.app.feature.dump.domain.transcription.OnDeviceTranscriber

/**
 * Implementation of RecordingProcessingEngine with FSM + single-flight + checkpoint.
 *
 * Orchestrates the full pipeline:
 * 1. Fetch recording from DB
 * 2. Validate status (skip if completed/non-retryable failed)
 * 3. Transcribe with checkpoint (skip if already done)
 * 4. Generate insight
 * 5. Mark COMPLETED
 * 6. On error: classify and handle (retry or mark FAILED)
 *
 * Note on single-flight:
 * - Multiple calls to process(same_id) may execute concurrently if Job/Mutex unavailable
 * - Recording status in DB provides checkpoint to prevent duplicate work
 * - Transient duplicate processing is acceptable; DB state is source of truth
 */
internal class RecordingProcessingEngineImpl(
    private val recordingRepository: RecordingRepository,
    private val insightPort: InsightPort,
    private val transcriber: OnDeviceTranscriber,
) : RecordingProcessingEngine {

    /**
     * Process a single recording through the pipeline.
     *
     * Fetches recording, validates status, transcribes (with checkpoint),
     * generates insight, and updates DB status.
     *
     * Error handling: classifies errors as transient or permanent,
     * and updates status accordingly (PENDING for auto-retry, FAILED for WM retry or permanent).
     */
    override suspend fun process(recordingId: String) {
        coroutineScope {
            try {
                executeProcessing(recordingId)
            } catch (e: Exception) {
                // Unexpected error during processing; log and propagate
                // (caller should handle)
                throw e
            }
        }
    }

    /**
     * Execute the full FSM pipeline for a recording.
     *
     * Flow:
     * 1. Fetch recording
     * 2. Validate status (skip if COMPLETED or non-retryable FAILED)
     * 3. Transcribe (checkpoint: skip if transcript exists)
     * 4. Generate insight
     * 5. Mark COMPLETED
     * 6. On error: classify and handle (retry or mark FAILED)
     */
    private suspend fun executeProcessing(recordingId: String) {
        // STEP 1: Fetch recording from DB
        val recording = recordingRepository.getRecording(recordingId)
            ?: throw IllegalArgumentException("Recording $recordingId not found")

        // STEP 2: Validate status — skip if already completed
        if (recording.processingStatus == ProcessingStatus.COMPLETED) {
            // Already successfully processed; nothing to do
            return
        }

        // STEP 3: Validate status — skip if non-retryable failed
        if (recording.processingStatus == ProcessingStatus.FAILED) {
            val errorCode = recording.errorCode
            if (errorCode != null && !errorCode.isEligibleForBackgroundRetry) {
                // Non-retryable error; don't attempt again
                return
            }
            // Otherwise: transient error, proceed with retry
        }

        // STEP 4: Transition to TRANSCRIBING
        recordingRepository.updateProcessingStatus(
            id = recordingId,
            status = ProcessingStatus.TRANSCRIBING
        )

        try {
            // STEP 5: Transcribe (with checkpoint logic)
            val transcription = performTranscription(recording)
                ?: throw IllegalStateException("Transcription returned null")

            // STEP 6: Update DB with transcript
            recordingRepository.updateTranscription(recordingId, transcription)

            // STEP 7: Transition to GENERATING_INSIGHT
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.GENERATING_INSIGHT
            )

            // STEP 8: Generate insight from transcript
            performInsightGeneration(
                recording = recording.copy(transcription = transcription),
                transcription = transcription
            )

            // STEP 9: Transition to COMPLETED (success!)
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.COMPLETED
            )

        } catch (e: Exception) {
            // STEP 10: Handle error with retry classification
            handleError(recording, e, attempt = recording.backgroundWmAttempts)
        }
    }

    /**
     * Perform on-device transcription with checkpoint.
     *
     * Checkpoint: if recording already has a transcript, skip transcription.
     * Otherwise, call transcriber, catch errors, and return result or null.
     */
    private suspend fun performTranscription(recording: Recording): String? {
        // CHECKPOINT: If transcript already exists, skip transcription
        if (!recording.transcription.isNullOrBlank()) {
            // Optimization: cached transcript exists
            // Use it instead of re-transcribing
            return recording.transcription
        }

        // Not yet transcribed; perform transcription
        return try {
            transcriber.transcribe(
                filePath = recording.filePath,
                locale = recording.recordingLocale
            )
        } catch (e: Exception) {
            // Transcription failed; let caller handle via handleError()
            throw e
        }
    }

    /**
     * Generate insight from transcription.
     *
     * Calls InsightPort to generate insight, handles errors, updates DB.
     */
    private suspend fun performInsightGeneration(
        recording: Recording,
        transcription: String
    ) {
        // Generate insight from transcription
        insightPort.generateInsight(
            recordingId = recording.id,
            transcription = transcription
        )

        // InsightPort.generateInsight() returns Insight
        // It is the responsibility of InsightRepositoryImpl to save it to DB
        // For v2 pipeline, the engine only orchestrates the call
    }

    /**
     * Handle processing errors with transient vs permanent classification.
     *
     * - Transient: auto-retry once, then queue for WorkManager
     * - Permanent: mark FAILED, do not retry
     */
    private suspend fun handleError(
        recording: Recording,
        error: Exception,
        attempt: Int
    ) {
        // STEP 1: Classify error into ProcessingErrorCode
        val errorCode = classifyError(error)

        // STEP 2: Decide retry strategy based on error type
        when {
            // Permanent errors → always FAILED (no retry)
            !errorCode.isEligibleForBackgroundRetry -> {
                recordingRepository.updateProcessingStatus(
                    id = recording.id,
                    status = ProcessingStatus.FAILED,
                    errorCode = errorCode,
                    errorMessage = error.message
                )
            }

            // Transient errors → auto-retry once, then defer to WM
            attempt < 1 -> {
                // First attempt; mark PENDING for auto-retry
                recordingRepository.updateProcessingStatus(
                    id = recording.id,
                    status = ProcessingStatus.PENDING,
                    errorCode = null,  // Clear error code (will retry)
                    errorMessage = null
                )
            }

            else -> {
                // Already retried; mark FAILED, defer to WorkManager
                recordingRepository.updateProcessingStatus(
                    id = recording.id,
                    status = ProcessingStatus.FAILED,
                    errorCode = errorCode,
                    errorMessage = error.message
                )
            }
        }
    }

    /**
     * Classify exception into ProcessingErrorCode.
     *
     * Maps error messages to error codes to determine retry eligibility.
     */
    private fun classifyError(error: Exception): ProcessingErrorCode {
        val message = error.message?.lowercase() ?: ""
        val cause = error.cause?.message?.lowercase() ?: ""
        val fullMessage = "$message $cause"

        return when {
            // Transient errors (retry-eligible)
            fullMessage.contains("timeout") -> ProcessingErrorCode.TIMEOUT
            fullMessage.contains("connection refused") -> ProcessingErrorCode.NETWORK
            fullMessage.contains("connection reset") -> ProcessingErrorCode.NETWORK
            fullMessage.contains("connection lost") -> ProcessingErrorCode.NETWORK
            fullMessage.contains("network") -> ProcessingErrorCode.NETWORK
            fullMessage.contains("unavailable") -> ProcessingErrorCode.NETWORK

            // Permanent errors (no retry)
            fullMessage.contains("language") && fullMessage.contains("not supported") ->
                ProcessingErrorCode.ON_DEVICE_LANGUAGE_NOT_SUPPORTED
            fullMessage.contains("corrupt") -> ProcessingErrorCode.CORRUPT_FILE
            fullMessage.contains("file not found") -> ProcessingErrorCode.CORRUPT_FILE
            fullMessage.contains("permission denied") -> ProcessingErrorCode.CORRUPT_FILE
            fullMessage.contains("bad request") -> ProcessingErrorCode.BAD_REQUEST
            fullMessage.contains("invalid") -> ProcessingErrorCode.BAD_REQUEST
            fullMessage.contains("rate limit") -> ProcessingErrorCode.RATE_LIMIT
            fullMessage.contains("quota") -> ProcessingErrorCode.RATE_LIMIT

            // Default: treat as transient (safer to retry)
            else -> ProcessingErrorCode.UNKNOWN_TRANSIENT
        }
    }
}
