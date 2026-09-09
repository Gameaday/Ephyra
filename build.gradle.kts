plugins {
    id("com.android.application") apply false
    id("com.android.library") apply false
    alias(kotlinx.plugins.serialization) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// Suppress warnings for the following:

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
        )
    }
}

// Test configuration for faster CI builds and flaky test resilience
tasks.withType<Test>().configureEach {
    // Reduce parallelism in CI to avoid resource contention causing flaky failures
    maxParallelForks = if (System.getenv("CI") != null) 2 else Runtime.getRuntime().availableProcessors()
    // Enable test result caching
    outputs.upToDateWhen { true }
    // Set test timeout to prevent hanging tests
    timeout.set(java.time.Duration.ofMinutes(10))
    // Show test progress
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

