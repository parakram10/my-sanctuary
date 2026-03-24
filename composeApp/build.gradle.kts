import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
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

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(project(":shared"))
            implementation(project(":core_ui"))
            implementation(project(":feature_dump"))
            implementation(project(":feature_summary"))
            implementation(project(":feature_history"))
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }
    }
}

android {
    namespace = "sanctuary.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "sanctuary.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

// KMP + AGP: variant-level locale filters actually strip non–en/hi languages from the packaged APK.
// (The mergeDebugResources/merged.dir tree may still list every locale until the prune task below runs.)
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.androidResources.localeFilters.set(listOf("en", "hi"))
    }
}

private val valuesApiLevel = Regex("^values-v\\d")
private val valuesSmallestWidth = Regex("^values-sw\\d")
private val valuesWidth = Regex("^values-w\\d")
private val valuesHeight = Regex("^values-h\\d")

fun keepMergedValuesDir(name: String): Boolean {
    if (name == "values") return true
    if (name.startsWith("values-en") || name == "values-hi") return true
    if (valuesApiLevel.matches(name)) return true
    if (valuesSmallestWidth.matches(name)) return true
    if (valuesWidth.matches(name)) return true
    if (valuesHeight.matches(name)) return true
    if (name.startsWith("values-land") || name.startsWith("values-port")) return true
    if (name.startsWith("values-night") || name.startsWith("values-notnight")) return true
    if (name.startsWith("values-large") || name.startsWith("values-xlarge")) return true
    return false
}

fun pruneNonEnHiValuesDirs(buildDir: File, variantSegment: String, mergeTaskBase: String) {
    val mergedDir = buildDir.resolve("intermediates/incremental/$variantSegment/${mergeTaskBase}Resources/merged.dir")
    if (!mergedDir.isDirectory) return
    mergedDir.listFiles()?.forEach { dir ->
        if (!dir.isDirectory) return@forEach
        val n = dir.name
        if (keepMergedValuesDir(n)) return@forEach
        if (n.startsWith("values-")) dir.deleteRecursively()
    }
}

/** Blame / merge metadata: drop values-*.json for locales we do not ship (same rules as [keepMergedValuesDir]). */
fun pruneBlameFolderValuesJson(buildDir: File, variantSegment: String, mergeTaskBase: String) {
    val multiV2 = buildDir.resolve(
        "intermediates/merged_res_blame_folder/$variantSegment/${mergeTaskBase}Resources/out/multi-v2",
    )
    if (!multiV2.isDirectory) return
    multiV2.listFiles()?.forEach { file ->
        if (!file.isFile || !file.name.endsWith(".json")) return@forEach
        if (!file.name.startsWith("values")) return@forEach
        val stem = file.name.removeSuffix(".json")
        if (keepMergedValuesDir(stem)) return@forEach
        if (stem.startsWith("values-")) file.delete()
    }
}

afterEvaluate {
    tasks.named("mergeDebugResources") {
        doLast {
            val buildDir = layout.buildDirectory.get().asFile
            pruneNonEnHiValuesDirs(buildDir, "debug", "mergeDebug")
            pruneBlameFolderValuesJson(buildDir, "debug", "mergeDebug")
        }
    }
    tasks.named("mergeReleaseResources") {
        doLast {
            val buildDir = layout.buildDirectory.get().asFile
            pruneNonEnHiValuesDirs(buildDir, "release", "mergeRelease")
            pruneBlameFolderValuesJson(buildDir, "release", "mergeRelease")
        }
    }
}
