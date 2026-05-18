plugins {
    id("mihon.library")
    id("mihon.library.compose")
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
    implementation(projects.domain)
    implementation(projects.data)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.presentationCore)

    implementation(libs.logcat)
    implementation(libs.bundles.voyager)
    
    implementation(libs.subsamplingscaleimageview)
    implementation(libs.image.decoder)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
}

