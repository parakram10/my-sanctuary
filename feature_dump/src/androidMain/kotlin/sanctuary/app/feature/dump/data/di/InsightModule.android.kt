package sanctuary.app.feature.dump.data.di

/**
 * Android implementation of getClaudeApiKey().
 * Retrieves the Claude API key from environment variable (for development)
 * or from BuildConfig (for production builds).
 *
 * To set up the API key:
 * 1. Set CLAUDE_API_KEY environment variable before running the app
 * 2. Or configure it in BuildConfig at build time
 *
 * @return The Claude API key
 * @throws IllegalStateException if the API key is not configured
 */
internal actual fun getClaudeApiKey(): String {
    // First try environment variable (for development)
    System.getenv("CLAUDE_API_KEY")?.let { return it }

    // Then try BuildConfig (commented out until API key is added to build config)
    // Uncomment this line once API key is added to build.gradle.kts:
    // return BuildConfig.CLAUDE_API_KEY

    throw IllegalStateException(
        "Claude API key not configured. " +
        "Set CLAUDE_API_KEY environment variable or add to BuildConfig. " +
        "Get your API key from: https://console.anthropic.com/account/keys"
    )
}
