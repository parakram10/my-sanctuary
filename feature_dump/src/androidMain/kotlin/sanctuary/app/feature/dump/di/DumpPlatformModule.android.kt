package sanctuary.app.feature.dump.di

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import sanctuary.app.core.database.SanctuaryDatabase
import sanctuary.app.feature.dump.data.di.dumpDataModule
import sanctuary.app.feature.dump.domain.audio.AudioFileProvider
import sanctuary.app.feature.dump.domain.preferences.PermissionsPreferences
import sanctuary.app.feature.dump.domain.audio.AudioPlayer
import sanctuary.app.feature.dump.domain.audio.AudioRecorder
import sanctuary.app.feature.dump.domain.di.dumpDomainModule
import sanctuary.app.feature.dump.platform.AndroidAudioFileProvider
import sanctuary.app.feature.dump.platform.AndroidAudioPlayer
import sanctuary.app.feature.dump.platform.AndroidAudioRecorder
import sanctuary.app.feature.dump.platform.AndroidMediaRecordingManager
import sanctuary.app.feature.dump.platform.AndroidSpeechRecognitionManager
import sanctuary.app.feature.dump.presentation.di.dumpPresentationModule

fun dumpFeaturePlatformModule() = module {
    includes(
        dumpDataModule,
        dumpDomainModule,
        dumpPresentationModule,
    )

    single<SqlDriver> {
        AndroidSqliteDriver(
            schema = SanctuaryDatabase.Schema,
            context = androidContext(),
            name = "sanctuary.db",
        )
    }
    single { SanctuaryDatabase(get()) }
    single { AndroidMediaRecordingManager(androidContext()) }
    single { AndroidSpeechRecognitionManager(androidContext()) }
    single { AndroidAudioRecorder(get(), get()) }
    single<AudioRecorder> { get<AndroidAudioRecorder>() }
    single<AudioPlayer> { AndroidAudioPlayer() }
    single<AudioFileProvider> { AndroidAudioFileProvider(androidContext()) }
    single { PermissionsPreferences(androidContext()) }
}
