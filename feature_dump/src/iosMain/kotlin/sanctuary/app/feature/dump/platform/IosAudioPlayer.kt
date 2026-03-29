package sanctuary.app.feature.dump.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSURL
import sanctuary.app.feature.dump.domain.audio.AudioPlayer

internal class IosAudioPlayer : AudioPlayer {

    private var player: AVAudioPlayer? = null

    @OptIn(ExperimentalForeignApi::class)
    override fun play(filePath: String): Boolean {
        stop()

        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, error = null)

        val url = NSURL.fileURLWithPath(filePath)
        val localPlayer = AVAudioPlayer(contentsOfURL = url, error = null) ?: return false
        localPlayer.prepareToPlay()

        val started = localPlayer.play()
        if (started) {
            player = localPlayer
        } else {
            session.setActive(false, error = null)
        }
        return started
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun stop() {
        player?.stop()
        player = null
        AVAudioSession.sharedInstance().setActive(false, error = null)
    }

    override fun isPlaying(): Boolean = player?.playing == true
}
