package sanctuary.app.feature.dump.data.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import sanctuary.app.feature.dump.data.datasource.RecordingLocalDataSource
import sanctuary.app.feature.dump.data.datasource.RecordingLocalDataSourceImpl
import sanctuary.app.feature.dump.data.repository.RecordingRepositoryImpl
import sanctuary.app.feature.dump.domain.repository.RecordingRepository

val dumpDataModule = module {
    singleOf(::RecordingLocalDataSourceImpl) bind RecordingLocalDataSource::class
    singleOf(::RecordingRepositoryImpl) bind RecordingRepository::class
    includes(providePlatformTranscriptionModule())
}

/**
 * Platform-specific factory providing TranscriptionRepository binding.
 * Implemented in androidMain and iosMain.
 */
internal expect fun providePlatformTranscriptionModule(): Module
