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
    implementation(projects.feature.reader)
    implementation(projects.feature.webview)
    implementation(projects.feature.category)
    implementation(projects.feature.settings)

    implementation(libs.logcat)
    implementation(libs.bundles.voyager)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.bundles.test)
}

