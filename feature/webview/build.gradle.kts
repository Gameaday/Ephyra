plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.koin.compiler)
}

android {
    namespace = "ephyra.feature.webview"
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Internal project dependencies
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.i18n)
    api(projects.presentationCore)

    // Third-party libraries
    implementation(libs.logcat)
    implementation(libs.compose.webview)
    implementation(androidx.appcompat) // FIX: Guarantees R class resolution
    api(libs.bundles.voyager)

    // Dependency Injection (Koin 4.2.0)
    api(libs.koin.core)
    implementation(libs.koin.androidx.compose) // FIX: Resolves koinScreenModel
    implementation(libs.koin.annotations)

    // Testing
    testImplementation(libs.bundles.test)
}
