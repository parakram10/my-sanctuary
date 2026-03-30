package sanctuary.app.feature.dump.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.setActive

@OptIn(ExperimentalForeignApi::class)
internal class IosAudioSessionManager {

    fun activateForRecording(): Boolean = runCatching {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryRecord, error = null)
        session.setActive(true, error = null)
    }.isSuccess

    fun deactivate() {
        runCatching { AVAudioSession.sharedInstance().setActive(false, error = null) }
    }
}
