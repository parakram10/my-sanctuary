package sanctuary.app.shared.resource

import android.content.Context
import sanctuary.app.shared.R

actual class StringResolver {

    constructor()

    constructor(context: Context) : this() {
        appContext = context.applicationContext
    }

    actual fun resolve(
        key: AppString,
        vararg args: Any,
    ): String {
        val ctx = appContext
            ?: throw IllegalStateException("StringResolver has not been initialized with a Context")

        val resId = when (key) {
            AppString.ONBOARDING_PAGE_1_TITLE -> R.string.onboarding_page1_title
        }

        return if (args.isEmpty()) {
            ctx.getString(resId)
        } else {
            ctx.getString(resId, *args)
        }
    }

    companion object {
        @Volatile
        private var appContext: Context? = null

        fun initialize(context: Context) {
            appContext = context.applicationContext
        }
    }
}
