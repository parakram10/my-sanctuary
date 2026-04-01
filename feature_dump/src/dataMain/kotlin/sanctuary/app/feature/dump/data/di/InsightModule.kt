package sanctuary.app.feature.dump.data.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import kotlin.time.Duration.Companion.seconds
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import sanctuary.app.feature.dump.data.datasource.InsightLocalDataSource
import sanctuary.app.feature.dump.data.datasource.InsightLocalDataSourceImpl
import sanctuary.app.feature.dump.data.repository.InsightRepositoryImpl
import sanctuary.app.feature.dump.data.service.ClaudeInsightGenerationService
import sanctuary.app.feature.dump.domain.repository.InsightRepository
import sanctuary.app.feature.dump.domain.service.InsightGenerationService

/**
 * Koin DI module for Insight feature services.
 *
 * Wires:
 * - InsightLocalDataSourceImpl → InsightLocalDataSource (database access)
 * - ClaudeInsightGenerationService → InsightGenerationService (AI insights)
 * - InsightRepositoryImpl → InsightRepository (main business logic)
 *
 * Also configures:
 * - HttpClient for API communication
 * - Claude API key for authentication
 *
 * This module is included in DumpDataModule and loaded at app startup.
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

    // Claude API key (in production, this should come from secure storage or BuildConfig)
    // TODO: Move to BuildConfig or secure configuration in production
    single<String>(qualifier = named("claudeApiKey")) {
        // PLACEHOLDER: Replace with actual API key from secure storage
        System.getenv("CLAUDE_API_KEY") ?: throw IllegalStateException(
            "CLAUDE_API_KEY environment variable not set. " +
            "Set it before running the app or configure it in BuildConfig."
        )
    }

    // Database access layer
    singleOf(::InsightLocalDataSourceImpl) bind InsightLocalDataSource::class

    // AI service for generating insights via Claude API
    single<InsightGenerationService> {
        ClaudeInsightGenerationService(
            httpClient = get(),
            apiKey = get(qualifier = named("claudeApiKey"))
        )
    }

    // Main repository combining database and AI service
    singleOf(::InsightRepositoryImpl) bind InsightRepository::class
}
