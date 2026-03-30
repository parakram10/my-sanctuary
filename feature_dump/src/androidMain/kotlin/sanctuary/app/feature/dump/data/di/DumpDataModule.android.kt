package sanctuary.app.feature.dump.data.di

import org.koin.core.module.Module
import org.koin.dsl.module
import sanctuary.app.feature.dump.data.repository.AndroidTranscriptionRepositoryImpl
import sanctuary.app.feature.dump.domain.repository.TranscriptionRepository

internal actual fun providePlatformTranscriptionModule(): Module = module {
    single<TranscriptionRepository> { AndroidTranscriptionRepositoryImpl() }
}
