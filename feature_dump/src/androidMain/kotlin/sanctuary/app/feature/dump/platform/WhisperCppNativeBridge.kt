package sanctuary.app.feature.dump.platform

import android.os.Build
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

internal class WhisperCppContext private constructor(private var contextPointer: Long) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    suspend fun transcribe(
        audioSamples: FloatArray,
        language: String,
        threadCount: Int
    ): String = withContext(dispatcher) {
        require(contextPointer != 0L) { "Whisper context has already been released" }

        val status = WhisperCppNativeLib.fullTranscribe(
            contextPtr = contextPointer,
            numThreads = threadCount,
            language = language,
            audioData = audioSamples
        )

        if (status != 0) {
            throw IllegalStateException("whisper.cpp failed with native status code $status")
        }

        buildString {
            val segmentCount = WhisperCppNativeLib.getTextSegmentCount(contextPointer)
            for (index in 0 until segmentCount) {
                append(WhisperCppNativeLib.getTextSegment(contextPointer, index))
            }
        }.trim()
    }

    suspend fun release() {
        withContext(dispatcher) {
            if (contextPointer != 0L) {
                WhisperCppNativeLib.freeContext(contextPointer)
                contextPointer = 0L
            }
        }

        dispatcher.close()
    }

    companion object {
        fun fromModelPath(modelPath: String): WhisperCppContext {
            val pointer = WhisperCppNativeLib.initContext(modelPath)
            if (pointer == 0L) {
                throw IllegalStateException("Unable to initialize whisper.cpp model from $modelPath")
            }
            return WhisperCppContext(pointer)
        }
    }
}

private class WhisperCppNativeLib {
    companion object {
        init {
            System.loadLibrary("sanctuary_whisper")
        }

        external fun initContext(modelPath: String): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(
            contextPtr: Long,
            numThreads: Int,
            language: String,
            audioData: FloatArray
        ): Int

        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
    }
}

internal object WhisperCpuConfig {
    val preferredThreadCount: Int
        get() = when {
            Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a" -> {
                (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 6)
            }

            else -> Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        }
}
