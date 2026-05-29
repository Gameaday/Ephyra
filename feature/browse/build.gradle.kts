plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.browse"
}

dependencies {
    api(projects.core.common)
    api(projects.core.domain)
    api(projects.core.data)
    api(projects.sourceApi)
    api(projects.presentationCore)
    api(projects.feature.manga)
    api(projects.feature.category)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.logcat)

    testImplementation(libs.bundles.test)
}
