package sanctuary.app.shared.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Cross-platform Koin modules (use cases, repository bindings, etc.).
 * Platform-specific singles (e.g. SQL driver) should live in the app’s `platformAppModule`.
 */
fun sharedKoinModules(): List<Module> = listOf(sharedCoreModule)

private val sharedCoreModule = module {
}
