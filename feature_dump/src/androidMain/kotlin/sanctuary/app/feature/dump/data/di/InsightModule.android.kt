package sanctuary.app.feature.dump.data.di

/**
 * Android implementation of getGroqApiKey().
 * Retrieves the Groq API key from environment variable (for development)
 * or from BuildConfig (for production builds).
 *
 * To set up the API key:
 * 1. Set GROQ_API_KEY environment variable before running the app
 * 2. Or configure it in BuildConfig at build time
 *
 * Get free API key from: https://console.groq.com/keys
 *
 * @return The Groq API key
 * @throws IllegalStateException if the API key is not configured
 */
internal actual fun getGroqApiKey(): String {
    // First try environment variable (for development)
    System.getenv("GROQ_API_KEY")?.let { return it }

    // Then try BuildConfig (configured in composeApp/build.gradle.kts)
    try {
        val clazz = Class.forName("sanctuary.app.BuildConfig")
        val field = clazz.getField("GROQ_API_KEY")
        return field.get(null) as String
    } catch (e: Exception) {
        // Fall through to error
    }

    throw IllegalStateException(
        "Groq API key not configured. " +
        "Set GROQ_API_KEY environment variable or add to BuildConfig. " +
        "Get your FREE API key from: https://console.groq.com/keys"
    )
}

/**
 * Android implementation of getClaudeApiKey().
 * Retrieves the Claude API key from environment variable (for development)
 * or from BuildConfig (for production builds).
 *
 * To set up the API key:
 * 1. Set CLAUDE_API_KEY environment variable before running the app
 * 2. Or configure it in BuildConfig at build time
 *
 * Get API key from: https://console.anthropic.com/account/keys
 *
 * @return The Claude API key
 * @throws IllegalStateException if the API key is not configured
 */
internal actual fun getClaudeApiKey(): String {
    // First try environment variable (for development)
    System.getenv("CLAUDE_API_KEY")?.let { return it }

    // Then try BuildConfig (configured in composeApp/build.gradle.kts)
    try {
        val clazz = Class.forName("sanctuary.app.BuildConfig")
        val field = clazz.getField("CLAUDE_API_KEY")
        return field.get(null) as String
    } catch (e: Exception) {
        // Fall through to error
    }

    throw IllegalStateException(
        "Claude API key not configured. " +
        "Set CLAUDE_API_KEY environment variable or add to BuildConfig. " +
        "Get your API key from: https://console.anthropic.com/account/keys"
    )
}
