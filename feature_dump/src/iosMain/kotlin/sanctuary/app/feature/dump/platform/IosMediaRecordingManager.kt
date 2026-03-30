package sanctuary.app.feature.dump.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.AVFAudio.AVAudioQualityHigh
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
internal class IosMediaRecordingManager(
    private val audioSessionManager: IosAudioSessionManager
) {

    private val recorderScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recorderMutex = Mutex()
    private var recorder: AVAudioRecorder? = null
    private var recording = false
    private var currentOutputFilePath: String? = null

    fun start(outputFilePath: String) {
        if (recording) {
            return
        }
        recording = true
        currentOutputFilePath = outputFilePath

        recorderScope.launch {
            recorderMutex.withLock {
                if (!recording || currentOutputFilePath != outputFilePath) {
                    return@withLock
                }
                startRecorderLocked(outputFilePath)
            }
        }
    }

    fun stop() {
        recording = false
        currentOutputFilePath = null

        recorderScope.launch {
            recorderMutex.withLock {
                stopRecorderLocked(deleteFile = false)
                audioSessionManager.deactivate()
            }
        }
    }

    fun cancel() {
        recording = false
        currentOutputFilePath = null

        recorderScope.launch {
            recorderMutex.withLock {
                stopRecorderLocked(deleteFile = true)
                audioSessionManager.deactivate()
            }
        }
    }

    fun isRecording(): Boolean = recording

    fun amplitudeFlow(): Flow<Float> = flow {
        while (recording) {
            recorder?.updateMeters()
            val power = recorder?.averagePowerForChannel(0u) ?: -160f
            // averagePowerForChannel returns dBFS (-160 to 0); normalize to 0..1
            val normalized = ((power + 160f) / 160f).coerceIn(0f, 1f)
            emit(normalized)
            delay(100)
        }
    }

    private fun startRecorderLocked(outputFilePath: String) {
        stopRecorderLocked(deleteFile = false)
        if (!audioSessionManager.activateForRecording()) {
            recording = false
            currentOutputFilePath = null
            audioSessionManager.deactivate()
            return
        }

        var localRecorder: AVAudioRecorder? = null
        runCatching {
            val url = NSURL.fileURLWithPath(outputFilePath)
            val settings = mapOf<Any?, Any?>(
                AVFormatIDKey to kAudioFormatMPEG4AAC,
                AVSampleRateKey to 44100.0,
                AVNumberOfChannelsKey to 1,
                AVEncoderAudioQualityKey to AVAudioQualityHigh,
            )

            localRecorder = AVAudioRecorder(url, settings, null).apply {
                meteringEnabled = true
            }
            val started = localRecorder?.record() == true
            if (!started) error("Failed to start AVAudioRecorder")
            recorder = localRecorder
        }.onFailure {
            runCatching { localRecorder?.stop() }
            runCatching { localRecorder?.deleteRecording() }
            recorder = null
            recording = false
            currentOutputFilePath = null
            audioSessionManager.deactivate()
        }
    }

    private fun stopRecorderLocked(deleteFile: Boolean) {
        val localRecorder = recorder ?: return
        runCatching { localRecorder.stop() }
        if (deleteFile) {
            runCatching { localRecorder.deleteRecording() }
        }
        recorder = null
    }
}
