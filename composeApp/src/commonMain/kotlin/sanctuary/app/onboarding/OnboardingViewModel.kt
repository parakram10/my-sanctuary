package sanctuary.app.onboarding

import sanctuary.app.presentation.BaseStateMviViewModel

class OnboardingViewModel :
    BaseStateMviViewModel<OnboardingIntent, OnboardingDataState, OnboardingViewState, OnboardingSideEffect>() {

    override fun initialDataState(): OnboardingDataState = OnboardingDataState()

    override suspend fun convertToUiState(dataState: OnboardingDataState): OnboardingViewState =
        OnboardingViewState(page = dataState.page)

    override fun processIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.Skip -> emitSideEffect(OnboardingSideEffect.NavigateToHome)
            OnboardingIntent.Next -> {
                when (dataState.value.page) {
                    OnboardingPage.First -> updateState { it.copy(page = OnboardingPage.Second) }
                    OnboardingPage.Second -> updateState { it.copy(page = OnboardingPage.Third) }
                    OnboardingPage.Third -> emitSideEffect(OnboardingSideEffect.NavigateToHome)
                }
            }
        }
    }
}
