import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Dependency rules (enforced by source sets):
 * - domainMain → :shared only
 * - presentationMain → :core_ui + Compose + coroutines (StateFlow / UI state)
 * - dataMain → :core_network + :core_database (no UI)
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            kotlin.setSrcDirs(listOf("src/commonMain/kotlin"))
        }

        val domainMain by creating {
            dependsOn(commonMain)
            kotlin.srcDir("src/domainMain/kotlin")
            dependencies {
                api(project(":shared"))
                api(libs.kotlinx.coroutines.core)
            }
        }

        val presentationMain by creating {
            dependsOn(domainMain)
            kotlin.srcDir("src/presentationMain/kotlin")
            dependencies {
                implementation(project(":core_ui"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val dataMain by creating {
            dependsOn(domainMain)
            kotlin.srcDir("src/dataMain/kotlin")
            dependencies {
                implementation(project(":core_network"))
                implementation(project(":core_database"))
                implementation(libs.sqldelight.coroutines.extensions)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        androidMain.get().dependsOn(presentationMain)
        androidMain.get().dependsOn(dataMain)

        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.sqldelight.android.driver)
        }

        sourceSets.named("iosMain").configure {
            dependsOn(presentationMain)
            dependsOn(dataMain)
            dependencies {
                implementation(libs.sqldelight.native.driver)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.koin.test)
                implementation(libs.turbine)
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.mockk)
            }
        }
    }
}

android {
    namespace = "sanctuary.app.feature.dump"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}
