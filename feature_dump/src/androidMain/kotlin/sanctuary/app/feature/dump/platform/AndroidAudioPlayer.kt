package sanctuary.app.feature.dump.platform

import android.media.MediaPlayer
import sanctuary.app.feature.dump.domain.audio.AudioPlayer
import java.io.File

internal class AndroidAudioPlayer : AudioPlayer {

    private var player: MediaPlayer? = null

    override fun play(filePath: String): Boolean {
        if (!File(filePath).exists()) return false

        stop()
        val localPlayer = MediaPlayer()
        return runCatching {
            localPlayer.setDataSource(filePath)
            localPlayer.setOnCompletionListener { completedPlayer ->
                completedPlayer.release()
                if (player === completedPlayer) {
                    player = null
                }
            }
            localPlayer.prepare()
            localPlayer.start()
            player = localPlayer
            true
        }.getOrElse {
            runCatching { localPlayer.release() }
            false
        }
    }

    override fun stop() {
        val localPlayer = player ?: return
        player = null
        runCatching {
            if (localPlayer.isPlaying) {
                localPlayer.stop()
            }
            localPlayer.reset()
            localPlayer.release()
        }
    }

    override fun isPlaying(): Boolean = player?.isPlaying == true
}
