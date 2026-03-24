pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Sanctuary"

include(":shared")
include(":core_network")
include(":core_database")
include(":core_ui")

include(":feature_dump")
include(":feature_summary")
include(":feature_history")

include(":composeApp")
