package sanctuary.app.feature.dump.data.repository

import android.content.Context
import android.os.Build
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import sanctuary.app.feature.dump.domain.repository.TranscriptionRepository
import sanctuary.app.feature.dump.domain.transcription.OnDeviceTranscriber
import sanctuary.app.shared.domain.usecase.UsecaseResult

internal class AndroidTranscriptionRepositoryImpl(
    private val onDeviceTranscriber: OnDeviceTranscriber,
) : TranscriptionRepository, KoinComponent {
    private val context: Context by inject()

    override suspend fun transcribe(filePath: String): UsecaseResult<String, Throwable> =
        runCatching {
            onDeviceTranscriber.transcribe(
                filePath = filePath,
                locale = context.preferredTranscriptionLocale()
            )
        }.fold(
            onSuccess = { UsecaseResult.Success(it) },
            onFailure = { UsecaseResult.Failure(it) }
        )
}

private fun Context.preferredTranscriptionLocale(): String {
    val language = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        resources.configuration.locales[0]?.language
    } else {
        @Suppress("DEPRECATION")
        resources.configuration.locale?.language
    }?.lowercase()

    return if (language == "hi") "hi" else "en"
}
