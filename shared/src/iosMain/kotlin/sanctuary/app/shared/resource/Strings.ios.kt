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

        return if (args.isEmpty()) {
            localized
        } else {
            args.fold(localized) { acc, arg ->
                acc
                    .replaceFirst("%s", arg.toString())
                    .replaceFirst("%@", arg.toString())
                    .replaceFirst("%d", arg.toString())
                    .replaceFirst("%f", arg.toString())
            }
        }
    }
}