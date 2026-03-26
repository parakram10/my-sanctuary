package sanctuary.app.shared.resource

import platform.Foundation.NSBundle

actual class StringResolver {

    actual fun resolve(
        key: AppString,
        vararg args: Any
    ): String {
        val localized = NSBundle.mainBundle.localizedStringForKey(
            key = key.resourceKey,
            value = key.resourceKey,
            table = null
        )

        if (args.isEmpty()) return localized

        return formatLocalizedString(localized, args)
    }

    /**
     * Supports escaped percent (%%), sequential placeholders (%@, %s, %d, %f),
     * and positional placeholders (e.g. %2$@).
     */
    private fun formatLocalizedString(template: String, args: Array<out Any>): String {
        val out = StringBuilder()
        var cursor = 0
        var nextSequentialArgIndex = 0

        while (cursor < template.length) {
            val current = template[cursor]
            if (current != '%') {
                out.append(current)
                cursor++
                continue
            }

            if (cursor + 1 < template.length && template[cursor + 1] == '%') {
                out.append('%')
                cursor += 2
                continue
            }

            var scan = cursor + 1
            var positionalArgIndex: Int? = null

            val digitsStart = scan
            while (scan < template.length && template[scan].isDigit()) {
                scan++
            }
            if (scan < template.length && scan > digitsStart && template[scan] == '$') {
                positionalArgIndex = template.substring(digitsStart, scan).toInt() - 1
                scan++
            } else {
                scan = cursor + 1
            }

            if (scan >= template.length) {
                out.append('%')
                cursor++
                continue
            }

            val specifier = template[scan]
            if (specifier !in SupportedFormatSpecifiers) {
                out.append('%')
                cursor++
                continue
            }

            val argIndex = positionalArgIndex ?: nextSequentialArgIndex++
            val arg = args.getOrNull(argIndex)

            val rendered = when (specifier) {
                'd' -> (arg as? Number)?.toLong()?.toString() ?: arg?.toString().orEmpty()
                'f' -> (arg as? Number)?.toDouble()?.toString() ?: arg?.toString().orEmpty()
                else -> arg?.toString().orEmpty()
            }

            out.append(rendered)
            cursor = scan + 1
        }

        return out.toString()
    }

    private companion object {
        val SupportedFormatSpecifiers = setOf('@', 's', 'd', 'f')
    }
}
