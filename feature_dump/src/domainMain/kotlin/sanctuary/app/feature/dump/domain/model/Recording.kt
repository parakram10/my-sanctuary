package sanctuary.app.feature.dump.domain.model

data class Recording(
    val id: String,
    val userId: String?,
    val filePath: String,
    val durationMs: Long,
    val createdAt: Long,
    val title: String?,
    val transcription: String?,
    val isArchived: Boolean = false,
    val processingStatus: ProcessingStatus = ProcessingStatus.PENDING,
    val errorCode: ProcessingErrorCode? = null,
    val backgroundWmAttempts: Int = 0,
    val recordingLocale: String = "en",
)
