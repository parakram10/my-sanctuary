package sanctuary.app.feature.dump.domain.scheduling

/**
 * Platform-agnostic interface for scheduling background recording processing.
 *
 * Implementations:
 * - Android: WorkManager-based scheduler
 * - iOS: BGProcessingTask or foreground flush
 */
interface BackgroundWorkScheduler {
    /**
     * Schedule a recording for background processing.
     *
     * Implementation details (WM, periodic job, etc.) are platform-specific.
     * Caller doesn't care about timing; it's the platform's job to pick it.
     *
     * @param recordingId The recording to process
     */
    suspend fun scheduleRecordingProcessing(recordingId: String)

    /**
     * One-time setup: configure periodic job, constraints, etc.
     *
     * Called once during app initialization.
     */
    suspend fun setup()
}
