package sanctuary.app.feature.dump.domain.audio

interface AudioFileProvider {
    fun recordingsDirectory(): String
    fun newRecordingFilePath(): String
    fun deleteFile(path: String)
}
