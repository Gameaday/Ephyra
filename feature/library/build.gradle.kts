plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.library"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.presentationCore)
    api(projects.feature.manga) // Library needs to jump to MangaScreen

    implementation(libs.logcat)
    implementation(libs.bundles.voyager)

    ksp(libs.hilt.compiler)
    testImplementation(libs.bundles.test)
}

