package sanctuary.app.feature.dump.domain.model

/**
 * A queued request to generate an AI insight from a recording.
 *
 * When insight generation fails (e.g., due to network error, rate limit),
 * the request is queued for automatic retry. The queue processor periodically
 * attempts to retry pending requests, up to a maximum of 3 attempts.
 *
 * Once a request is completed successfully (or abandoned after max retries),
 * it should be removed from the queue.
 *
 * @property id Unique identifier for this request
 * @property recordingId The recording this request should generate an insight from
 * @property transcription The transcribed text of the recording
 * @property createdAt Timestamp when request was created (epoch ms)
 * @property retryCount Current number of retry attempts (0-3)
 * @property maxRetries Maximum number of retry attempts allowed (typically 3)
 * @property status Current lifecycle stage of this request
 * @property errorMessage Error message from the last failed attempt, if any
 */
data class InsightGenerationRequest(
    val id: String,
    val recordingId: String,
    val transcription: String,
    val createdAt: Long,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val status: RequestStatus = RequestStatus.PENDING,
    val errorMessage: String? = null,
)

/**
 * Lifecycle states for queued insight generation requests.
 *
 * - PENDING: Request created and waiting for processing
 * - PROCESSING: Currently being processed by the background queue processor
 * - FAILED: Processing failed; will be retried if retryCount < maxRetries
 * - COMPLETED: Successfully processed; should be removed from queue
 */
enum class RequestStatus {
    PENDING, PROCESSING, FAILED, COMPLETED
}
