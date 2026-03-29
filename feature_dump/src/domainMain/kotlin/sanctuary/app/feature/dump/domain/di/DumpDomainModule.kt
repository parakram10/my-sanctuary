package sanctuary.app.feature.dump.domain.di

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import sanctuary.app.feature.dump.domain.usecase.DeleteRecordingUseCase
import sanctuary.app.feature.dump.domain.usecase.GetRecordingsUseCase
import sanctuary.app.feature.dump.domain.usecase.SaveRecordingUseCase

val dumpDomainModule = module {
    factoryOf(::GetRecordingsUseCase)
    factoryOf(::SaveRecordingUseCase)
    factoryOf(::DeleteRecordingUseCase)
}
