package sanctuary.app.di

import org.koin.core.module.Module
import sanctuary.app.shared.di.sharedKoinModules

expect fun platformAppModule(): Module

fun allAppModules(): List<Module> = buildList {
    addAll(sharedKoinModules())
    add(appModule)
    add(platformAppModule())
}
