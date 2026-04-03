package sanctuary.app.feature.dump.domain.model

enum class ProcessingStatus {
    PENDING,
    TRANSCRIBING,
    GENERATING_INSIGHT,
    COMPLETED,
    FAILED
}
