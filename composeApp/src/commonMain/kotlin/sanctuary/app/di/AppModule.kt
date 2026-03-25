package sanctuary.app.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import sanctuary.app.onboarding.OnboardingViewModel

val appModule = module {
    viewModelOf(::OnboardingViewModel)
}
