package sanctuary.app.feature.dump.domain.audio

interface AudioPlayer {
    fun play(filePath: String): Boolean
    fun stop()
    fun isPlaying(): Boolean
}
