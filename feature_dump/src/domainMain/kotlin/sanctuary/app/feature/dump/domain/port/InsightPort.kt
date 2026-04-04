package sanctuary.app.feature.dump.domain.port

import sanctuary.app.feature.dump.domain.model.Insight

/**
 * Domain port for AI-powered insight generation.
 *
 * Defines the boundary between the domain and any external AI provider.
 * Implementations live in the data layer (Claude, Groq, etc.) and are
 * wired via DI — the domain and processing engine never depend on a
 * concrete provider.
 *
 * Throws on failure; the caller is responsible for checking retryability
 * against [sanctuary.app.feature.dump.domain.model.ProcessingErrorCode].
 */
interface InsightPort {
    suspend fun generateInsight(recordingId: String, transcription: String): Insight
}
