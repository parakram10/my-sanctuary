package sanctuary.app.feature.dump.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.timeIntervalSince1970
import sanctuary.app.feature.dump.domain.audio.AudioFileProvider

internal class IosAudioFileProvider : AudioFileProvider {

    @OptIn(ExperimentalForeignApi::class)
    override fun recordingsDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        )
        val documentsDir = paths.first().toString()
        val recordingsDir = "$documentsDir/recordings"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = recordingsDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        return recordingsDir
    }

    override fun newRecordingFilePath(): String {
        val timestamp = NSDate().timeIntervalSince1970.toLong()
        return "${recordingsDirectory()}/$timestamp.m4a"
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun deleteFile(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }
}
