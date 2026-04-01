package sanctuary.app.feature.dump.domain.model

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

enum class RequestStatus {
    PENDING, PROCESSING, FAILED, COMPLETED
}
