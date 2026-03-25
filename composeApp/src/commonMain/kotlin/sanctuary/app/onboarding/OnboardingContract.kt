package sanctuary.app.onboarding

enum class OnboardingPage {
    First,
    Second,
    Third,
}

sealed interface OnboardingIntent {
    data object Skip : OnboardingIntent
    data object Next : OnboardingIntent
}

data class OnboardingDataState(
    val page: OnboardingPage = OnboardingPage.First,
)

data class OnboardingViewState(
    val page: OnboardingPage,
)

sealed interface OnboardingSideEffect {
    data object NavigateToHome : OnboardingSideEffect
}
