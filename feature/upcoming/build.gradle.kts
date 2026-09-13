plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.upcoming"
}

dependencies {
    // Internal project dependencies
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.sourceApi)
    implementation(projects.presentationCore)
    implementation(projects.feature.manga)

    // Jetpack Compose
    implementation(compose.material3.core)
    implementation(compose.ui.tooling.preview)
    debugImplementation(compose.ui.tooling)

    // Third-party libraries
    implementation(libs.logcat)
    implementation(libs.compose.grid)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
