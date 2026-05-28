plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.history"
}

dependencies {
    api(projects.core.common)
    api(projects.core.domain)
    api(projects.core.data)
    api(projects.sourceApi)
    api(projects.presentationCore)

    implementation(projects.feature.category)
    implementation(projects.feature.manga)
    implementation(projects.feature.migration)
    implementation(projects.feature.reader)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}

