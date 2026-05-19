plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.browse"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.presentationCore)
    api(projects.feature.manga)

    implementation(libs.logcat)
    implementation(libs.bundles.voyager)

    testImplementation(libs.bundles.test)
}

