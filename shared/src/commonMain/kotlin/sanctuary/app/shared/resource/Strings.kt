package sanctuary.app.shared.resource


expect class StringResolver {
    fun resolve(key: AppString, vararg args: Any): String
}

enum class AppString(val key: String) {
    ONBOARDING_PAGE_1_TITLE("onboarding_page1_title")
}