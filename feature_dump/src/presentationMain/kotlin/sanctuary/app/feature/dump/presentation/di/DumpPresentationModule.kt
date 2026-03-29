package sanctuary.app.feature.dump.presentation.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import sanctuary.app.feature.dump.presentation.viewmodel.DumpViewModel

val dumpPresentationModule = module {
    viewModelOf(::DumpViewModel)
}
