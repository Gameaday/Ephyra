plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.browse"

    testOptions {
        unitTests {
            // Required for Robolectric-based Compose UI tests (resource loading).
            isIncludeAndroidResources = true

            all {
                // Robolectric + Compose does not fit Gradle's 512m default test-worker heap. The
                // worker is killed by the OOM killer before it can flush its results, and Gradle
                // then reports the *symptom* rather than the cause:
                //
                //   java.nio.file.NoSuchFileException: ...\binary\in-progress-results-generic.bin
                //
                // which reads like a corrupt-output problem and is not one. This is why `:app` has
                // carried `maxHeapSize = "2g"` while the other Robolectric-enabled modules did not:
                // the limit was added where the failure first appeared, not where it applies. Any
                // module with `isIncludeAndroidResources = true` needs the same heap.
                it.maxHeapSize = "2g"
            }
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.sourceApi)
    implementation(projects.presentationCore)
    implementation(projects.feature.manga)
    implementation(projects.feature.migration)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.logcat)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testDebugImplementation(libs.compose.ui.test.manifest)
}
