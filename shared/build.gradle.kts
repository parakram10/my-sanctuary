import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
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
        }

        val dataMain by creating {
            dependsOn(domainMain)
            kotlin.srcDir("src/dataMain/kotlin")
            dependencies {
                implementation(project(":core_network"))
                implementation(project(":core_database"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        androidMain.get().dependsOn(dataMain)
        sourceSets.named("iosMain").configure { dependsOn(dataMain) }
    }
}

android {
    namespace = "sanctuary.app.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        @Suppress("DEPRECATION")
        resourceConfigurations += listOf("en", "hi")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
