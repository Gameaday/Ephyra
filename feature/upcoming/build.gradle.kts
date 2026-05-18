plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.hilt)
    id("com.google.devtools.ksp")
}

android {
    namespace = "ephyra.feature.upcoming"
}

dependencies {
    // Internal project dependencies
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.presentationCore)
    implementation(projects.feature.manga)

    // Jetpack Compose
    implementation(compose.material3.core)
    implementation(compose.ui.tooling.preview)
    debugImplementation(compose.ui.tooling)

    // Third-party libraries
    implementation(libs.logcat)
    implementation(libs.compose.grid)
    api(libs.bundles.voyager)

    // Dependency Injection (Hilt & Koin)
    api(libs.koin.core)
    implementation(libs.koin.annotations)
    ksp(libs.hilt.compiler)
}
