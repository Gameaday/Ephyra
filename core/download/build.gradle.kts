plugins {
    id("ephyra.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "ephyra.core.download"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
        )
    }
}

dependencies {
    api(projects.core.common)
    api(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.archive)
    implementation(projects.coreMetadata)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)

    implementation(androidx.workmanager)
    implementation(libs.logcat)
    implementation(libs.unifile)

    // AndroidX DataStore for preference-backed stores
    implementation(libs.datastore)
    // Explicit fallbacks in case version catalog alias doesn't resolve in this module
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.datastore:datastore-core:1.2.1")

    implementation(kotlinx.bundles.serialization)
}
// Suppress warnings for the following:

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}
