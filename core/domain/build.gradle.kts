plugins {
    id("ephyra.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "ephyra.core.domain"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(projects.coreMetadata)
    implementation(projects.core.common)
    implementation(projects.sourceApi)

    // Pure Kotlin dependencies only — no Android framework dependencies.
    // WorkManager, Compose annotations, and Paging must NOT be in this module.
    api(kotlinx.coroutines.core)
    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)
    implementation(kotlinx.bundles.serialization)
    implementation(kotlinx.immutables)
    implementation("javax.inject:javax.inject:1")

    // Logic / utilities that are pure Kotlin
    implementation(libs.stringSimilarity)

    // PagingSource is a pure-Kotlin type in androidx.paging:paging-common.
    // No Android framework dependency; safe for domain layer.
    implementation(libs.paging.common)

    // UniFile is a pure-Kotlin abstraction over file systems (no Android framework).
    // Domain uses it in DownloadProvider and StorageManager interfaces.
    implementation(libs.unifile)

    // sqldelight will be removed as part of Room migration; keep only what's needed
    // for the transition period

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
// Suppress warnings for the following:

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}
