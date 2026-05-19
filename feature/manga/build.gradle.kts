plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.manga"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.sourceLocal)
    api(projects.presentationCore)

    implementation(libs.logcat)
    implementation(libs.bundles.voyager)

    testImplementation(libs.bundles.test)
}

