plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.security"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.sourceApi)
    implementation(projects.presentationCore)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
