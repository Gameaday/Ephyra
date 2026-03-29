plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "ephyra.feature.category"
}

dependencies {
    // Internal project dependencies
    api(projects.core.common)
    api(projects.core.domain) // FIX: Koin needs this to verify UiPreferences
    api(projects.core.data)   // FIX: Koin needs this to verify repositories
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.i18n)
    api(projects.presentationCore)

    // Jetpack Compose
    implementation(compose.material3.core)
    implementation(compose.ui.tooling.preview)
    debugImplementation(compose.ui.tooling)

    // Third-party libraries
    implementation(libs.logcat)
    api(libs.bundles.voyager)

    // Dependency Injection (Koin)
    api(libs.koin.core)
    implementation(libs.koin.androidx.compose) // FIX: Resolves koinScreenModel
    implementation(libs.koin.annotations)

    // Testing
    testImplementation(libs.bundles.test)
}
