package sanctuary.app.shared.resource

import android.content.Context
import sanctuary.app.shared.R

actual class StringResolver(private val context: Context) {

    actual fun resolve(
        key: AppString,
        vararg args: Any,
    ): String {
        val resId = when (key) {
            AppString.ONBOARDING_PAGE_1_TITLE -> R.string.onboarding_page1_title
        }


        return if (args.isEmpty()) {
            context.getString(resId)
        } else {
            context.getString(resId, *args)
        }
    }
}
