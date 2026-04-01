package sanctuary.app.feature.dump.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class InsightContent(
    val title: String,
    val summary: String,
    val fullSummary: String,
    val emotions: List<String>,
    val pathForward: String,
    val recordingType: String,
    val sentiment: Sentiment,
)

@Serializable
enum class Sentiment {
    POSITIVE, NEGATIVE, NEUTRAL
}

data class Insight(
    val id: String,
    val recordingId: String,
    val content: InsightContent,
    val createdAt: Long,
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
    val status: InsightStatus = InsightStatus.SAVED,
)

enum class InsightStatus {
    PENDING, GENERATING, SAVED, ARCHIVED
}
