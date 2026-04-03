package sanctuary.app.feature.dump.domain.transcription

/**
 * Platform-agnostic interface for on-device speech-to-text.
 * Implementations detect language availability and throw appropriate errors.
 */
interface OnDeviceTranscriber {
    /**
     * Transcribe audio file using on-device STT.
     *
     * @param filePath Path to audio file (typically .m4a)
     * @param locale Locale code ("en" or "hi")
     * @return Transcribed text, or throws exception on failure
     * @throws IllegalArgumentException If locale is not supported on this device
     * @throws Exception For other transcription failures
     */
    suspend fun transcribe(filePath: String, locale: String): String
}
