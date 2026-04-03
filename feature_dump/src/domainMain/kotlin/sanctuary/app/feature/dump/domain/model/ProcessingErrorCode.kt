package sanctuary.app.feature.dump.domain.model

/**
 * Machine-readable error codes for recording processing failures.
 * Determines whether a failure is eligible for WorkManager auto-retry.
 */
enum class ProcessingErrorCode(
    val isEligibleForBackgroundRetry: Boolean
) {
    // Transient errors — eligible for WM retry
    NETWORK(isEligibleForBackgroundRetry = true),
    TIMEOUT(isEligibleForBackgroundRetry = true),
    UNKNOWN_TRANSIENT(isEligibleForBackgroundRetry = true),

    // Permanent errors — NOT eligible for WM retry
    ON_DEVICE_LANGUAGE_NOT_SUPPORTED(isEligibleForBackgroundRetry = false),
    CORRUPT_FILE(isEligibleForBackgroundRetry = false),
    BAD_REQUEST(isEligibleForBackgroundRetry = false),
    RATE_LIMIT(isEligibleForBackgroundRetry = false),
    UNKNOWN_PERMANENT(isEligibleForBackgroundRetry = false),
}
