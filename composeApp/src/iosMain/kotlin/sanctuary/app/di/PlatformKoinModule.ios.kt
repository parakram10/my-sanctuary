package sanctuary.app.di

import org.koin.core.module.Module
import org.koin.dsl.module
import sanctuary.app.feature.dump.di.dumpFeaturePlatformModule

actual fun platformAppModule(): Module = module {
    includes(dumpFeaturePlatformModule())
}
