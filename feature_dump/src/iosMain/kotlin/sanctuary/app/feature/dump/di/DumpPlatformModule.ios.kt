package sanctuary.app.feature.dump.di

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import org.koin.dsl.module
import sanctuary.app.core.database.SanctuaryDatabase
import sanctuary.app.feature.dump.data.di.dumpDataModule
import sanctuary.app.feature.dump.domain.audio.AudioFileProvider
import sanctuary.app.feature.dump.domain.audio.AudioPlayer
import sanctuary.app.feature.dump.domain.audio.AudioRecorder
import sanctuary.app.feature.dump.domain.di.dumpDomainModule
import sanctuary.app.feature.dump.domain.preferences.PermissionsPreferences
import sanctuary.app.feature.dump.platform.IosAudioFileProvider
import sanctuary.app.feature.dump.platform.IosAudioPlayer
import sanctuary.app.feature.dump.platform.IosAudioRecorder
import sanctuary.app.feature.dump.platform.IosAudioSessionManager
import sanctuary.app.feature.dump.platform.IosMediaRecordingManager
import sanctuary.app.feature.dump.platform.IosSpeechRecognitionManager
import sanctuary.app.feature.dump.presentation.di.dumpPresentationModule

fun dumpFeaturePlatformModule() = module {
    includes(
        dumpDataModule,
        dumpDomainModule,
        dumpPresentationModule,
    )

    single<SqlDriver> { NativeSqliteDriver(SanctuaryDatabase.Schema, "sanctuary.db") }
    single { SanctuaryDatabase(get()) }
    single { IosAudioSessionManager() }
    single { IosMediaRecordingManager(get()) }
    single { IosSpeechRecognitionManager() }
    single<AudioRecorder> { IosAudioRecorder(get(), get()) }
    single<AudioPlayer> { IosAudioPlayer() }
    single<AudioFileProvider> { IosAudioFileProvider() }
    single { PermissionsPreferences() }
}
