package sanctuary.app.shared.resource

import platform.Foundation.NSBundle

actual class StringResolver {

    actual fun resolve(
        key: AppString,
        vararg args: Any
    ): String {
        val localized = NSBundle.mainBundle.localizedStringForKey(
            key = key.name,
            value = key.name,
            table = null
        )

        if (args.isEmpty()) return localized

        val placeholder = Regex("%(?:\\d+\\$)?[@sdf]")
        var result = localized
        for (arg in args) {
            result = placeholder.replaceFirst(result, Regex.escapeReplacement(arg.toString()))
        }
        return result
    }
}