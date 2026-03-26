package sanctuary.app.shared.resource

expect class StringResolver {
    fun resolve(key: AppString, vararg args: Any): String
}

/**
 * Resource key convention:
 * enum name lowercased == localization key (e.g. ONBOARDING_PAGE_1_TITLE -> onboarding_page_1_title).
 */
enum class AppString {
    ONBOARDING_PAGE_1_TITLE,
    ONBOARDING_PAGE_1_BODY,
    ONBOARDING_PAGE_2_TITLE,
    ONBOARDING_PAGE_2_BODY,
    ONBOARDING_PAGE_3_TITLE,
    ONBOARDING_PAGE_3_BODY,
}

val AppString.resourceKey: String
    get() = name.lowercase()
