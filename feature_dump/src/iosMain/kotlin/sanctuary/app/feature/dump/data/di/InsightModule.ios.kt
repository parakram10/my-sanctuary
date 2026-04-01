package sanctuary.app.feature.dump.data.di

import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo

/**
 * iOS implementation of getClaudeApiKey().
 * Retrieves the Claude API key from:
 * 1. Environment variable (for development)
 * 2. Info.plist configuration (for production builds)
 *
 * To set up the API key on iOS:
 * 1. Set CLAUDE_API_KEY environment variable in scheme settings
 * 2. Or add CLAUDE_API_KEY key to Info.plist
 *
 * @return The Claude API key
 * @throws IllegalStateException if the API key is not configured
 */
internal actual fun getClaudeApiKey(): String {
    // First try environment variable (for development)
    val processInfo = NSProcessInfo.processInfo
    processInfo.environment["CLAUDE_API_KEY"]?.let { return it as String }

    // Then try Info.plist configuration
    val bundle = NSBundle.mainBundle
    bundle.infoDictionary?.get("CLAUDE_API_KEY")?.let { return it as String }

    throw IllegalStateException(
        "Claude API key not configured on iOS. " +
        "Set CLAUDE_API_KEY in scheme environment variables or add to Info.plist. " +
        "Get your API key from: https://console.anthropic.com/account/keys"
    )
}
