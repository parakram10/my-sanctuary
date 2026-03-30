package sanctuary.app.feature.dump.platform

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL
import android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS
import android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE
import android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
import android.speech.SpeechRecognizer
import android.speech.SpeechRecognizer.createSpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class AndroidSpeechRecognitionManager(private val context: Context) {

    private val recognizerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recognizerMutex = Mutex()
    private val recognizerToken = AtomicLong(0L)
    private val transcriptHolder = AtomicReference<String?>(null)
    private var speechRecognizer: SpeechRecognizer? = null
    @Volatile
    private var sessionActive = false

    fun startSession() {
        transcriptHolder.set(null)
        sessionActive = true
        val token = recognizerToken.incrementAndGet()
        recognizerScope.launch {
            startSpeechRecognition(token)
        }
    }

    fun stopSession(cancelListening: Boolean, clearTranscript: Boolean) {
        sessionActive = false
        if (clearTranscript) {
            transcriptHolder.set(null)
        }

        val token = recognizerToken.incrementAndGet()
        recognizerScope.launch {
            stopSpeechRecognition(token, cancelListening, clearTranscript)
        }
    }

    fun getTranscript(): String? = transcriptHolder.get()

    private suspend fun startSpeechRecognition(token: Long) {
        recognizerMutex.withLock {
            if (token != recognizerToken.get() || !sessionActive) {
                return@withLock
            }

            speechRecognizer?.let { existing ->
                runCatching { existing.destroy() }
                speechRecognizer = null
            }
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                return@withLock
            }

            val recognizer = runCatching { createSpeechRecognizer(context) }.getOrNull() ?: return@withLock
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(EXTRA_LANGUAGE_MODEL, LANGUAGE_MODEL_FREE_FORM)
                putExtra(EXTRA_PARTIAL_RESULTS, true)
                putExtra(EXTRA_PREFER_OFFLINE, true)
            }
            recognizer.setRecognitionListener(RecognitionListenerImpl())
            runCatching {
                recognizer.startListening(intent)
                speechRecognizer = recognizer
            }.onFailure {
                runCatching { recognizer.destroy() }
            }
        }
    }

    private suspend fun stopSpeechRecognition(
        token: Long,
        cancelListening: Boolean,
        clearTranscript: Boolean,
    ) {
        recognizerMutex.withLock {
            if (token != recognizerToken.get()) {
                return@withLock
            }

            speechRecognizer?.let { recognizer ->
                if (cancelListening) {
                    runCatching { recognizer.cancel() }
                } else {
                    runCatching { recognizer.stopListening() }
                }
                runCatching { recognizer.destroy() }
                speechRecognizer = null
            }
            if (clearTranscript) {
                transcriptHolder.set(null)
            }
        }
    }

    private inner class RecognitionListenerImpl : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val results = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!results.isNullOrEmpty()) {
                transcriptHolder.set(results[0])
            }
        }

        override fun onResults(results: Bundle?) {
            val resultsList = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!resultsList.isNullOrEmpty()) {
                transcriptHolder.set(resultsList[0])
            }
        }
    }
}
