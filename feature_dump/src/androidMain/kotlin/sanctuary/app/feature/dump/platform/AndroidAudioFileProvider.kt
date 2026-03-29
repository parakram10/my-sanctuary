package sanctuary.app.feature.dump.platform

import android.content.Context
import sanctuary.app.feature.dump.domain.audio.AudioFileProvider
import java.io.File

internal class AndroidAudioFileProvider(private val context: Context) : AudioFileProvider {

    override fun recordingsDirectory(): String {
        val dir = File(context.filesDir, "recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    override fun newRecordingFilePath(): String =
        "${recordingsDirectory()}/${System.currentTimeMillis()}.m4a"
}
