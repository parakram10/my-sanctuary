package sanctuary.app.feature.dump.data.audio

import kotlinx.coroutines.flow.Flow

interface AudioRecorder {
    fun startRecording(outputFilePath: String)
    fun stopRecording()
    fun cancelRecording()
    fun isRecording(): Boolean
    fun amplitudeFlow(): Flow<Float>
}
