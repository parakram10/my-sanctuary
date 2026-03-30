package sanctuary.app.feature.dump.platform

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

internal class AndroidMediaRecordingManager(private val context: Context) {

    private var recorder: MediaRecorder? = null
    @Volatile
    private var recording = false
    private var currentOutputFilePath: String? = null

    fun start(outputFilePath: String): Boolean {
        if (recording) {
            return false
        }

        val localRecorder = createMediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(outputFilePath)
        }
        currentOutputFilePath = outputFilePath

        return runCatching {
            localRecorder.prepare()
            localRecorder.start()
            recorder = localRecorder
            recording = true
            true
        }.getOrElse {
            runCatching { localRecorder.release() }
            currentOutputFilePath = null
            recording = false
            false
        }
    }

    fun stop() {
        recording = false
        stopAndReleaseRecorder()
        currentOutputFilePath = null
    }

    fun cancel() {
        recording = false
        stopAndReleaseRecorder()
        currentOutputFilePath?.let { File(it).delete() }
        currentOutputFilePath = null
    }

    fun isRecording(): Boolean = recording

    fun amplitudeFlow(): Flow<Float> = flow {
        while (recording) {
            val amplitude = recorder?.maxAmplitude ?: 0
            emit((amplitude / 32767f).coerceIn(0f, 1f))
            delay(100)
        }
    }

    private fun stopAndReleaseRecorder() {
        val localRecorder = recorder ?: return
        runCatching { localRecorder.stop() }
        runCatching { localRecorder.release() }
        recorder = null
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
}
