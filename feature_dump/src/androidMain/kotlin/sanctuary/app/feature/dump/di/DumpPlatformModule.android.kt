package sanctuary.app.feature.dump.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import sanctuary.app.core.database.SanctuaryDatabase
import sanctuary.app.core.database.db.createEncryptedDatabase
import sanctuary.app.core.database.db.setAndroidContext
import sanctuary.app.core.database.security.PassphraseManager
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
import sanctuary.app.feature.dump.presentation.di.dumpPresentationModule

fun dumpFeaturePlatformModule() = module {
    includes(
        dumpDataModule,
        dumpDomainModule,
        dumpPresentationModule,
    )

    single<SanctuaryDatabase> {
        val context = androidContext()
        setAndroidContext(context)
        PassphraseManager.init(context)

        // Create encrypted database with secure passphrase
        val passphrase = PassphraseManager.getOrCreatePassphrase()
        createEncryptedDatabase(passphrase)
    }
    single { AndroidMediaRecordingManager(androidContext()) }
    single { AndroidAudioRecorder(get()) }
    single<AudioRecorder> { get<AndroidAudioRecorder>() }
    single<AudioPlayer> { AndroidAudioPlayer() }
    single<AudioFileProvider> { AndroidAudioFileProvider(androidContext()) }
    single { PermissionsPreferences(androidContext()) }
}
