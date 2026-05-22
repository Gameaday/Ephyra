plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.reader"

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.data)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.presentationCore)
    implementation(projects.feature.webview)

    implementation(libs.logcat)

    implementation(libs.subsamplingscaleimageview)
    implementation(libs.image.decoder)
    implementation(libs.directionalviewpager)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
}
