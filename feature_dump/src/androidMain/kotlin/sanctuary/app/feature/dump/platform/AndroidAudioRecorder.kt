package sanctuary.app.feature.dump.platform

import kotlinx.coroutines.flow.Flow
import sanctuary.app.feature.dump.domain.audio.AudioRecorder

internal class AndroidAudioRecorder(
    private val recordingManager: AndroidMediaRecordingManager,
) : AudioRecorder {

    override fun startRecording(outputFilePath: String) {
        recordingManager.start(outputFilePath)
    }

    override fun stopRecording() {
        recordingManager.stop()
    }

    override fun cancelRecording() {
        recordingManager.cancel()
    }

    override fun isRecording(): Boolean = recordingManager.isRecording()

    override fun amplitudeFlow(): Flow<Float> = recordingManager.amplitudeFlow()
}
