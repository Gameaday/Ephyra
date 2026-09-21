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
    // Bound the number of forked test JVMs. Every Gradle module schedules its own Test tasks and
    // `org.gradle.parallel=true` runs those tasks concurrently, so forking on every processor
    // spawns hundreds of JVMs during a full build. Robolectric (API 36) forks are memory heavy,
    // and oversubscribing CPU/memory kills test workers, which surfaces as
    // `java.io.EOFException` / `NoSuchFileException` on the test task instead of a test failure.
    val availableProcessors = Runtime.getRuntime().availableProcessors()
    maxParallelForks = if (System.getenv("CI") != null) 2 else (availableProcessors / 4).coerceIn(1, 4)
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
