package sanctuary.app.feature.dump.platform

import kotlinx.coroutines.flow.Flow
import sanctuary.app.feature.dump.domain.audio.AudioRecorder

internal class AndroidAudioRecorder(
    private val recordingManager: AndroidMediaRecordingManager,
    private val speechRecognitionManager: AndroidSpeechRecognitionManager,
) : AudioRecorder {

    override fun startRecording(outputFilePath: String) {
        val started = recordingManager.start(outputFilePath)
        if (started) {
            speechRecognitionManager.startSession()
        }
    }

    override fun stopRecording() {
        recordingManager.stop()
        speechRecognitionManager.stopSession(
            cancelListening = false,
            clearTranscript = false,
        )
    }

    override fun cancelRecording() {
        recordingManager.cancel()
        speechRecognitionManager.stopSession(
            cancelListening = true,
            clearTranscript = true,
        )
    }

    override fun isRecording(): Boolean = recordingManager.isRecording()

    override fun amplitudeFlow(): Flow<Float> = recordingManager.amplitudeFlow()
}
