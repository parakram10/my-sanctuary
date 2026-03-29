package sanctuary.app.feature.dump.data.audio

interface AudioFileProvider {
    fun recordingsDirectory(): String
    fun newRecordingFilePath(): String
}
