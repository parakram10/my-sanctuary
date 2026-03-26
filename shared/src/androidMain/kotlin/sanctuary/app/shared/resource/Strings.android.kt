package sanctuary.app.shared.resource

import android.content.Context

actual class StringResolver {

    constructor()

    constructor(context: Context) : this() {
        initialize(context)
    }

    actual fun resolve(
        key: AppString,
        vararg args: Any,
    ): String {
        val ctx = appContext ?: return key.resourceKey
        val resId = ctx.resources.getIdentifier(
            key.resourceKey,
            "string",
            ctx.packageName,
        )
        if (resId == 0) return key.resourceKey

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
