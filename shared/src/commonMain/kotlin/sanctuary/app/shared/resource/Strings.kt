package sanctuary.app.shared.resource


expect class StringResolver {
    fun resolve(key: AppString, vararg args: Any): String
}

enum class AppString{
    ONBOARDING_PAGE_1_TITLE
}