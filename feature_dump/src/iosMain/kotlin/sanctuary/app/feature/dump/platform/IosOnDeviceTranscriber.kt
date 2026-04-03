package sanctuary.app.feature.dump.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFoundation.*
import platform.Foundation.*
import platform.Speech.*
import sanctuary.app.feature.dump.domain.transcription.OnDeviceTranscriber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS implementation of on-device STT using SFSpeechRecognizer.
 * Supports English ("en") and Hindi ("hi") when available on-device.
 *
 * ARCHITECTURE:
 * - Validates audio file and permissions upfront to fail fast
 * - Uses SFSpeechURLRecognitionRequest for file-based recognition
 * - Applies timeout to prevent indefinite hangs
 * - Ensures cleanup in all paths (success, error, timeout, cancellation)
 */
class IosOnDeviceTranscriber : OnDeviceTranscriber {

    companion object {
        // Max transcription time: 60 seconds
        // Most voice recordings are < 60 seconds; SFSpeechRecognizer has built-in timeouts as safety net
        private const val TRANSCRIPTION_TIMEOUT_MS = 60_000L

        // Supported audio formats on iOS
        private val SUPPORTED_FORMATS = setOf(".m4a", ".mp3", ".wav", ".aac", ".caf")
    }

    override suspend fun transcribe(filePath: String, locale: String): String {
        // Input validation: prevent null/empty strings
        require(!filePath.isNullOrEmpty()) { "filePath must not be empty" }
        require(!locale.isNullOrEmpty()) { "locale must not be empty" }

        // Validate supported locales with helpful error message
        val localeIdentifier = when (locale) {
            "en" -> "en-US"
            "hi" -> "hi-IN"
            else -> throw IllegalArgumentException(
                "Unsupported locale: '$locale'. Supported: en, hi"
            )
        }

        // Validate audio format
        val fileExtension = filePath.substringAfterLast(".").lowercase()
        if (!SUPPORTED_FORMATS.contains(".$fileExtension")) {
            throw IllegalArgumentException(
                "Unsupported audio format: .$fileExtension. Supported: $SUPPORTED_FORMATS"
            )
        }

        // Check if speech recognition is available for the locale
        val recognizer = SFSpeechRecognizer(locale = NSLocale(localeIdentifier = localeIdentifier))
            ?: throw IllegalStateException("Speech recognition not available for locale: $localeIdentifier")

        if (!recognizer.isAvailable()) {
            throw IllegalStateException("Speech recognition not available on this device")
        }

        // Wrap in timeout to prevent indefinite hangs
        return withTimeoutOrNull(TRANSCRIPTION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                var recognitionTask: SFSpeechRecognitionTask? = null
                // Thread-safe flag to prevent multiple completion attempts
                val isCompleted = AtomicBoolean(false)

                // Cleanup function: idempotent, safe to call multiple times
                val cleanup = {
                    runCatching { recognitionTask?.cancel() }
                    recognitionTask = null
                }

                try {
                    val audioFileUrl = NSURL(fileURLWithPath = filePath)

                    if (audioFileUrl == null) {
                        if (isCompleted.compareAndSet(false, true)) {
                            continuation.resumeWithException(
                                IllegalArgumentException("Failed to create audio URL from path: $filePath")
                            )
                        }
                        return@suspendCancellableCoroutine
                    }

                    val request = SFSpeechURLRecognitionRequest(URL = audioFileUrl)

                    if (request == null) {
                        if (isCompleted.compareAndSet(false, true)) {
                            continuation.resumeWithException(
                                IllegalStateException("Failed to create recognition request for audio file")
                            )
                        }
                        return@suspendCancellableCoroutine
                    }

                    // Configure request for optimal reliability
                    // Don't report partial results; only wait for final transcription
                    request.shouldReportPartialResults = false

                    recognitionTask = recognizer.recognitionTaskWithRequest(request) { result, error ->
                        // Early exit if already completed to prevent race conditions
                        if (!isCompleted.compareAndSet(false, true)) {
                            return@recognitionTaskWithRequest
                        }

                        cleanup()

                        // Handle error case first
                        if (error != null) {
                            val exception = mapSFSpeechRecognitionError(error)
                            continuation.resumeWithException(exception)
                            return@recognitionTaskWithRequest
                        }

                        // Handle successful result: only process when isFinal is true
                        if (result != null && result.isFinal) {
                            val transcript = result.bestTranscription.formattedString.trim()
                            if (transcript.isNotEmpty()) {
                                continuation.resume(transcript)
                            } else {
                                continuation.resumeWithException(
                                    IllegalStateException("Empty transcript: no speech detected in audio file")
                                )
                            }
                        } else if (result == null && error == null) {
                            // Result is null but no error: unexpected state
                            continuation.resumeWithException(
                                IllegalStateException("Recognition returned null result without error")
                            )
                        }
                        // If result is not final yet, keep waiting (callback will be called again)
                    }

                    continuation.invokeOnCancellation {
                        // Ensure cleanup runs even if already completed (timeout cleanup)
                        // This prevents resource leaks if timeout fires after partial completion
                        cleanup()
                    }
                } catch (e: Exception) {
                    if (isCompleted.compareAndSet(false, true)) {
                        continuation.resumeWithException(
                            IllegalStateException("Failed to set up transcription: ${e.message}")
                        )
                    }
                }
            }
        } ?: throw IllegalStateException("Transcription timeout after ${TRANSCRIPTION_TIMEOUT_MS}ms")
    }

    // Map SFSpeechRecognizer errors to appropriate exception types
    // Distinguishes transient (retry-eligible) from permanent errors
    private fun mapSFSpeechRecognitionError(error: NSError): Exception {
        val description = error.localizedDescription
        return when {
            // Network errors are transient and eligible for retry
            description.contains("network", ignoreCase = true) ||
                description.contains("timeout", ignoreCase = true) ||
                description.contains("connection", ignoreCase = true) ->
                IllegalStateException("Transient network error: retry eligible")
            // Permission/authorization errors are permanent
            description.contains("permission", ignoreCase = true) ||
                description.contains("authorization", ignoreCase = true) ||
                description.contains("not authorized", ignoreCase = true) ->
                SecurityException("Speech recognition permission denied")
            // Audio issues
            description.contains("audio", ignoreCase = true) ->
                IllegalStateException("Audio processing error: $description")
            // Other errors
            else -> IllegalStateException("Transcription failed: $description")
        }
    }
}
