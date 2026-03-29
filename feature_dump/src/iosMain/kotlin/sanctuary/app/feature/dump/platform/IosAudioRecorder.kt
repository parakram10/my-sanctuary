package sanctuary.app.feature.dump.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import platform.AVFAudio.AVAudioQualityHigh
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSURL
import sanctuary.app.feature.dump.domain.audio.AudioRecorder

internal class IosAudioRecorder : AudioRecorder {

    private var recorder: AVAudioRecorder? = null
    private var recording = false

    @OptIn(ExperimentalForeignApi::class)
    override fun startRecording(outputFilePath: String) {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryRecord, error = null)
        session.setActive(true, error = null)

        val url = NSURL.fileURLWithPath(outputFilePath)
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to kAudioFormatMPEG4AAC,
            AVSampleRateKey to 44100.0,
            AVNumberOfChannelsKey to 1,
            AVEncoderAudioQualityKey to AVAudioQualityHigh,
        )

        recorder = AVAudioRecorder(url, settings, null).apply {
            meteringEnabled = true
            record()
        }
        recording = true
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun stopRecording() {
        recording = false
        recorder?.stop()
        recorder = null
        AVAudioSession.sharedInstance().setActive(false, error = null)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun cancelRecording() {
        recording = false
        recorder?.stop()
        recorder?.deleteRecording()
        recorder = null
        AVAudioSession.sharedInstance().setActive(false, error = null)
    }

    override fun isRecording(): Boolean = recording

    override fun amplitudeFlow(): Flow<Float> = flow {
        while (recording) {
            recorder?.updateMeters()
            val power = recorder?.averagePowerForChannel(0u) ?: -160f
            // averagePowerForChannel returns dBFS (-160 to 0); normalize to 0..1
            val normalized = ((power + 160f) / 160f).coerceIn(0f, 1f)
            emit(normalized)
            delay(100)
        }
    }
}
