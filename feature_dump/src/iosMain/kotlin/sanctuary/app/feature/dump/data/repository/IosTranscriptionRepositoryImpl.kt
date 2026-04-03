package sanctuary.app.feature.dump.data.repository

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages
import sanctuary.app.feature.dump.domain.repository.TranscriptionRepository
import sanctuary.app.feature.dump.domain.transcription.OnDeviceTranscriber
import sanctuary.app.shared.domain.usecase.UsecaseResult

internal class IosTranscriptionRepositoryImpl(
    private val onDeviceTranscriber: OnDeviceTranscriber,
) : TranscriptionRepository {
    override suspend fun transcribe(filePath: String): UsecaseResult<String, Throwable> =
        runCatching {
            onDeviceTranscriber.transcribe(
                filePath = filePath,
                locale = preferredTranscriptionLocale()
            )
        }.fold(
            onSuccess = { UsecaseResult.Success(it) },
            onFailure = { UsecaseResult.Failure(it) }
        )
}

private fun preferredTranscriptionLocale(): String {
    val preferredLanguage = NSLocale.preferredLanguages.firstOrNull() as? String
    val languageCode = preferredLanguage
        ?.substringBefore('-')
        ?.substringBefore('_')
        ?.lowercase()

    return if (languageCode == "hi") "hi" else "en"
}
