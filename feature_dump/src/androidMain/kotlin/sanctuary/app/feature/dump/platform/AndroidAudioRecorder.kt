package sanctuary.app.feature.dump.platform

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import sanctuary.app.feature.dump.domain.audio.AudioRecorder
import java.io.File

internal class AndroidAudioRecorder(private val context: Context) : AudioRecorder {

    private var recorder: MediaRecorder? = null
    private var recording = false
    private var currentOutputFilePath: String? = null

    override fun startRecording(outputFilePath: String) {
        val localRecorder = createMediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(outputFilePath)
        }
        currentOutputFilePath = outputFilePath

        runCatching {
            localRecorder.prepare()
            localRecorder.start()
        }.onSuccess {
            recorder = localRecorder
            recording = true
        }.onFailure {
            // Ensure resources are freed if starting the recorder fails
            runCatching { localRecorder.release() }
            recording = false
            currentOutputFilePath = null
        }
    }

    override fun stopRecording() {
        recording = false
        recorder?.runCatching {
            stop()
            release()
        }
        recorder = null
        currentOutputFilePath = null
    }

    override fun cancelRecording() {
        recording = false
        recorder?.runCatching {
            stop()
            release()
        }
        recorder = null
        currentOutputFilePath?.let { File(it).delete() }
        currentOutputFilePath = null
    }

    override fun isRecording(): Boolean = recording

    override fun amplitudeFlow(): Flow<Float> = flow {
        while (recording) {
            val amplitude = recorder?.maxAmplitude ?: 0
            emit((amplitude / 32767f).coerceIn(0f, 1f))
            delay(100)
        }
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
}
