package sanctuary.app.feature.dump.data.di

import org.koin.core.module.Module
import org.koin.dsl.module
import sanctuary.app.feature.dump.data.datasource.AndroidTranscriptionDataSource
import sanctuary.app.feature.dump.data.repository.AndroidTranscriptionRepositoryImpl
import sanctuary.app.feature.dump.domain.repository.TranscriptionRepository
import sanctuary.app.feature.dump.platform.AndroidSpeechRecognitionManager

internal actual fun providePlatformTranscriptionModule(): Module = module {
    single<TranscriptionRepository> {
        AndroidTranscriptionRepositoryImpl(
            AndroidTranscriptionDataSource(get<AndroidSpeechRecognitionManager>())
        )
    }
}
