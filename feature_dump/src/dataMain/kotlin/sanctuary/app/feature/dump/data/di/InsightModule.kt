package sanctuary.app.feature.dump.data.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import sanctuary.app.feature.dump.data.datasource.InsightLocalDataSource
import sanctuary.app.feature.dump.data.datasource.InsightLocalDataSourceImpl
import sanctuary.app.feature.dump.data.repository.InsightRepositoryImpl
import sanctuary.app.feature.dump.data.service.ClaudeInsightGenerationService
import sanctuary.app.feature.dump.data.service.GroqInsightGenerationService
import sanctuary.app.feature.dump.domain.repository.InsightRepository
import sanctuary.app.feature.dump.domain.service.InsightGenerationService

/**
 * Koin DI module for Insight feature services.
 *
 * Supports multiple AI providers for insight generation:
 * - GROQ (default, free tier, fastest)
 * - CLAUDE (premium, highest quality)
 *
 * Wires:
 * - InsightLocalDataSourceImpl → InsightLocalDataSource (database access)
 * - InsightGenerationService (swappable provider: Groq or Claude)
 * - InsightRepositoryImpl → InsightRepository (main business logic)
 *
 * Also configures:
 * - HttpClient for API communication
 * - API keys for selected provider
 *
 * This module is included in DumpDataModule and loaded at app startup.
 *
 * To switch providers, see [AI_PROVIDER] constant below.
 */
val insightModule = module {
    // HttpClient for API communication
    // Configured with timeouts for reliability
    single<HttpClient> {
        HttpClient {
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 10_000L  // 10 second timeout
                connectTimeoutMillis = 10_000L
                socketTimeoutMillis = 10_000L
            }
        }
    }

    // API keys for providers (provided through platform-specific module)
    // Android: From BuildConfig or environment variables
    // iOS: From Info.plist or configuration

    single<String>(qualifier = named("groqApiKey")) {
        getGroqApiKey()
    }

    single<String>(qualifier = named("claudeApiKey")) {
        getClaudeApiKey()
    }

    // Database access layer
    singleOf(::InsightLocalDataSourceImpl) bind InsightLocalDataSource::class

    // AI service for generating insights (provider-dependent)
    // Change AI_PROVIDER to switch between "groq" and "claude"
    single<InsightGenerationService> {
        when (AI_PROVIDER.lowercase()) {
            "groq" -> GroqInsightGenerationService(
                httpClient = get(),
                apiKey = get(qualifier = named("groqApiKey"))
            )
            "claude" -> ClaudeInsightGenerationService(
                httpClient = get(),
                apiKey = get(qualifier = named("claudeApiKey"))
            )
            else -> throw IllegalStateException(
                "Unknown AI provider: $AI_PROVIDER. " +
                "Valid options: 'groq' (default, free), 'claude' (premium)"
            )
        }
    }

    // Main repository combining database and AI service
    singleOf(::InsightRepositoryImpl) bind InsightRepository::class
}

/**
 * AI provider selection.
 *
 * Options:
 * - "groq" (default): Free tier, very fast, generous limits (perfect for testing)
 * - "claude": Premium, highest quality, requires paid API key
 *
 * To switch providers:
 * 1. Change this constant to "groq" or "claude"
 * 2. Ensure the corresponding API key is set
 * 3. Rebuild and redeploy
 *
 * Environment variable override: Set AI_PROVIDER_TYPE env var to override
 */
internal const val AI_PROVIDER = "groq"  // Change to "claude" for premium provider

/**
 * Platform-specific function to get the Groq API key.
 * Implemented in androidMain and iosMain source sets.
 *
 * Android: Reads from BuildConfig.GROQ_API_KEY or environment variable
 * iOS: Reads from Info.plist configuration
 *
 * @return The Groq API key for authentication
 * @throws IllegalStateException if the API key is not available
 */
internal expect fun getGroqApiKey(): String

/**
 * Platform-specific function to get the Claude API key.
 * Implemented in androidMain and iosMain source sets.
 *
 * Android: Reads from BuildConfig.CLAUDE_API_KEY or environment variable
 * iOS: Reads from Info.plist configuration
 *
 * @return The Claude API key for authentication
 * @throws IllegalStateException if the API key is not available
 */
internal expect fun getClaudeApiKey(): String
