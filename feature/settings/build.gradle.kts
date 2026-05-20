plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.settings"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(projects.data)
    implementation(projects.presentationCore)
    implementation(projects.feature.category)

    implementation(libs.bundles.voyager)

    // External dependencies
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.aboutLibraries.compose)
    implementation(androidx.profileinstaller)
    implementation(kotlinx.bundles.serialization)
    implementation(kotlinx.bundles.coroutines)
}


