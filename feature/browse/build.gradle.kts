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
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.sourceApi)
    implementation(projects.presentationCore)
    implementation(projects.feature.manga)
    implementation(projects.feature.category)
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
