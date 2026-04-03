package sanctuary.app.feature.dump.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import sanctuary.app.feature.dump.domain.transcription.OnDeviceTranscriber
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android implementation of on-device STT using SpeechRecognizer.
 * Supports English ("en") and Hindi ("hi").
 * Transcribes by playing audio file while listening via SpeechRecognizer.
 *
 * ARCHITECTURE:
 * - Validates audio file and permissions upfront to fail fast
 * - Starts both SpeechRecognizer and MediaPlayer in parallel
 * - Uses timeout to prevent indefinite hangs
 * - Ensures cleanup in all paths (success, error, timeout, cancellation)
 */
class AndroidOnDeviceTranscriber : OnDeviceTranscriber, KoinComponent {
    private val context: Context by inject()

    companion object {
        // Max transcription time: 120 seconds
        // SpeechRecognizer has built-in ~30sec silence timeout, so 120sec covers:
        // - normal audio + recognition time
        // - SpeechRecognizer's internal timeout mechanisms
        // This prevents indefinite hangs while being reasonable for real audio
        private const val TRANSCRIPTION_TIMEOUT_MS = 120_000L

        // Supported audio formats
        private val SUPPORTED_FORMATS = setOf(".m4a", ".mp3", ".wav", ".flac", ".ogg", ".aac")

        // Max audio file size: 100MB (reasonable for audio)
        private const val MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024L
    }

    override suspend fun transcribe(filePath: String, locale: String): String {
        // Input validation: prevent null/empty strings
        require(!filePath.isNullOrEmpty()) { "filePath must not be empty" }
        require(!locale.isNullOrEmpty()) { "locale must not be empty" }

        // Validate supported locales with helpful error message
        val languageTag = when (locale) {
            "en" -> "en-US"
            "hi" -> "hi-IN"
            else -> throw IllegalArgumentException(
                "Unsupported locale: '$locale'. Supported: en, hi"
            )
        }

        // Validate file exists and is readable
        val audioFile = File(filePath)
        if (!audioFile.exists()) {
            throw IllegalArgumentException("Audio file not found: $filePath")
        }
        if (!audioFile.canRead()) {
            throw IllegalArgumentException("Audio file not readable: $filePath")
        }

        // Validate audio file size (< 100 MB for reasonable memory usage)
        if (audioFile.length() > MAX_FILE_SIZE_BYTES) {
            throw IllegalArgumentException(
                "Audio file too large: ${audioFile.length()} bytes (max: $MAX_FILE_SIZE_BYTES)"
            )
        }

        // Validate audio format
        val fileExtension = audioFile.extension.lowercase()
        if (!SUPPORTED_FORMATS.contains(".$fileExtension")) {
            throw IllegalArgumentException(
                "Unsupported audio format: .$fileExtension. Supported: $SUPPORTED_FORMATS"
            )
        }

        // Check required runtime permissions
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("RECORD_AUDIO permission not granted")
        }

        // Check if SpeechRecognizer is available
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            throw IllegalStateException("Speech recognition not available on this device")
        }

        // Wrap in timeout to prevent indefinite hangs
        return withTimeoutOrNull(TRANSCRIPTION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                var mediaPlayer: MediaPlayer? = null
                // Thread-safe flag to prevent multiple completion attempts
                val isCompleted = AtomicBoolean(false)
                // Guard against double-release of MediaPlayer
                val isMediaPlayerReleased = AtomicBoolean(false)
                // Lock for thread-safe mediaPlayer initialization
                val mediaPlayerLock = Any()

                val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                // Cleanup function called in all completion paths to prevent resource leaks
                val cleanup = {
                    // Unregister listeners before cleanup to prevent callbacks during shutdown
                    mediaPlayer?.setOnCompletionListener(null)
                    mediaPlayer?.setOnErrorListener(null)
                    mediaPlayer?.setOnPreparedListener(null)

                    // Guard against double-release: only release once
                    if (mediaPlayer != null && isMediaPlayerReleased.compareAndSet(false, true)) {
                        runCatching { mediaPlayer?.stop() }
                        runCatching { mediaPlayer?.release() }
                        mediaPlayer = null
                    }

                    // Cancel and destroy speech recognizer
                    runCatching { speechRecognizer.cancel() }
                    runCatching { speechRecognizer.destroy() }
                }

                val recognitionListener = object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {
                        // Start playing audio file once recognition is ready.
                        // Synchronized to prevent race conditions with multiple onReadyForSpeech calls
                        synchronized(mediaPlayerLock) {
                            // Only initialize MediaPlayer once
                            if (mediaPlayer != null) return

                            try {
                                mediaPlayer = createAndPrepareMediaPlayer(filePath) { success ->
                                    if (!success && isCompleted.compareAndSet(false, true)) {
                                        continuation.resumeWithException(
                                            IllegalStateException("Failed to prepare audio file")
                                        )
                                        cleanup()
                                    }
                                }
                            } catch (e: Exception) {
                                if (isCompleted.compareAndSet(false, true)) {
                                    continuation.resumeWithException(
                                        IllegalArgumentException("Failed to initialize audio: ${e.message}")
                                    )
                                    cleanup()
                                }
                            }
                        }
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onResults(results: android.os.Bundle?) {
                        // Atomically check and set completion flag. If already completed, skip.
                        if (!isCompleted.compareAndSet(false, true)) {
                            return
                        }

                        cleanup()
                        if (results != null) {
                            val matches = results.getStringArrayList(
                                android.speech.SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            if (!matches.isNullOrEmpty()) {
                                val transcript = matches[0].trim()
                                if (transcript.isNotEmpty()) {
                                    continuation.resume(transcript)
                                } else {
                                    continuation.resumeWithException(
                                        IllegalStateException("Empty transcript received")
                                    )
                                }
                            } else {
                                continuation.resumeWithException(
                                    IllegalStateException("No speech detected in audio")
                                )
                            }
                        } else {
                            continuation.resumeWithException(
                                IllegalStateException("No recognition results returned")
                            )
                        }
                    }

                    override fun onPartialResults(partialResults: android.os.Bundle?) {}
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

                    override fun onError(error: Int) {
                        // Atomically check and set completion flag. If already completed, skip.
                        if (!isCompleted.compareAndSet(false, true)) {
                            return
                        }

                        cleanup()
                        val exception = when (error) {
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                IllegalStateException("Transient network error: retry eligible")
                            SpeechRecognizer.ERROR_AUDIO ->
                                IllegalStateException("Audio hardware error")
                            SpeechRecognizer.ERROR_NO_MATCH ->
                                IllegalStateException("No speech detected in audio")
                            SpeechRecognizer.ERROR_CLIENT ->
                                SecurityException("Permission or client error")
                            SpeechRecognizer.ERROR_SERVER ->
                                IllegalStateException("Server error during recognition")
                            else -> IllegalStateException("Speech recognition error code: $error")
                        }
                        continuation.resumeWithException(exception)
                    }
                }

                speechRecognizer.setRecognitionListener(recognitionListener)

                // On cancellation, ensure all resources are cleaned up
                continuation.invokeOnCancellation {
                    if (isCompleted.compareAndSet(false, true)) {
                        cleanup()
                    }
                }

                // Start listening immediately, then audio will start on onReadyForSpeech()
                try {
                    speechRecognizer.startListening(intent)
                } catch (e: Exception) {
                    if (isCompleted.compareAndSet(false, true)) {
                        continuation.resumeWithException(
                            IllegalStateException("Failed to start speech recognition: ${e.message}")
                        )
                        cleanup()
                    }
                }
            }
        } ?: throw IllegalStateException(
            "Transcription timeout after ${TRANSCRIPTION_TIMEOUT_MS}ms. " +
                    "Possible causes: no speech detected, audio too long, or audio playback failed."
        )
    }

    // Helper function to create MediaPlayer with async preparation and error handling.
    // Prevents blocking the listener thread and ensures error listeners are set up.
    // @throws Exception if data source setup fails (e.g., invalid URI or file path)
    private fun createAndPrepareMediaPlayer(
        filePath: String,
        onPrepared: (success: Boolean) -> Unit
    ): MediaPlayer {
        val mp = MediaPlayer()
        var callbackFired = false
        val lock = Any()

        try {
            // Set data source based on whether path is URI or file path
            if (filePath.startsWith("content://")) {
                // Validate content URI format
                try {
                    val uri = Uri.parse(filePath)
                    // Basic validation: content URIs must have a scheme and authority
                    if (uri.scheme != "content" || uri.authority == null) {
                        throw IllegalArgumentException("Invalid content URI: $filePath")
                    }
                    mp.setDataSource(context, uri)
                } catch (e: IllegalArgumentException) {
                    throw e
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid content URI: $filePath - ${e.message}")
                }
            } else {
                // Validate file path before setDataSource
                val file = File(filePath)
                if (!file.exists()) {
                    throw IllegalArgumentException("Audio file not found: $filePath")
                }
                if (!file.canRead()) {
                    throw IllegalArgumentException("Audio file not readable: $filePath")
                }
                mp.setDataSource(filePath)
            }

            // Prepare async to avoid blocking listener thread
            mp.setOnPreparedListener { preparedMp ->
                synchronized(lock) {
                    if (callbackFired) return@setOnPreparedListener
                    callbackFired = true
                }
                try {
                    preparedMp.start()
                    onPrepared(true)
                } catch (e: Exception) {
                    onPrepared(false)
                }
            }

            // Handle preparation errors immediately
            mp.setOnErrorListener { errorMp, what, extra ->
                synchronized(lock) {
                    if (callbackFired) return@setOnErrorListener true
                    callbackFired = true
                }
                // Fail immediately instead of waiting for timeout
                onPrepared(false)
                true // Consume error to prevent further callbacks
            }

            // Do NOT release in completion listener. Release is handled by cleanup()
            // to prevent double-release when audio finishes before transcription.
            mp.setOnCompletionListener { _ ->
                // Audio finished playing. Transcription should complete soon.
            }

            // Async preparation avoids ANR on large files
            mp.prepareAsync()
            return mp
        } catch (e: Exception) {
            // Cleanup on error to prevent resource leak
            runCatching { mp.release() }
            throw e
        }
    }
}
