package sanctuary.app.feature.dump.di

import org.koin.dsl.module
import sanctuary.app.core.database.SanctuaryDatabase
import sanctuary.app.core.database.db.createEncryptedDatabase
import sanctuary.app.core.database.security.PassphraseManager
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
import sanctuary.app.feature.dump.presentation.di.dumpPresentationModule

fun dumpFeaturePlatformModule() = module {
    includes(
        dumpDataModule,
        dumpDomainModule,
        dumpPresentationModule,
    )

    single<SanctuaryDatabase> {
        // Create encrypted database with secure passphrase
        val passphrase = PassphraseManager.getOrCreatePassphrase()
        createEncryptedDatabase(passphrase)
    }
    single { IosAudioSessionManager() }
    single { IosMediaRecordingManager(get()) }
    single<AudioRecorder> { IosAudioRecorder(get()) }
    single<AudioPlayer> { IosAudioPlayer() }
    single<AudioFileProvider> { IosAudioFileProvider() }
    single { PermissionsPreferences() }
}
