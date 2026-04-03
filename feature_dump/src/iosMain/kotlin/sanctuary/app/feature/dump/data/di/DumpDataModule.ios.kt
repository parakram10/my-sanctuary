package sanctuary.app.feature.dump.data.di

import org.koin.core.module.Module
import org.koin.dsl.module
import sanctuary.app.feature.dump.data.datasource.IosTranscriptionDataSource
import sanctuary.app.feature.dump.data.repository.IosTranscriptionRepositoryImpl
import sanctuary.app.feature.dump.domain.repository.TranscriptionRepository
import sanctuary.app.feature.dump.domain.transcription.OnDeviceTranscriber
import sanctuary.app.feature.dump.platform.IosSpeechRecognitionManager
import sanctuary.app.feature.dump.platform.IosOnDeviceTranscriber

internal actual fun providePlatformTranscriptionModule(): Module = module {
    single<TranscriptionRepository> {
        IosTranscriptionRepositoryImpl(
            IosTranscriptionDataSource(get<IosSpeechRecognitionManager>())
        )
    }

    // iOS uses native SFSpeechRecognizer (no alternative on iOS)
    single<OnDeviceTranscriber> {
        IosOnDeviceTranscriber()
    }
}
