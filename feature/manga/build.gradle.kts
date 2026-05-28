plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.manga"
}

dependencies {
    api(projects.core.common)
    api(projects.core.domain)
    api(projects.core.data)
    api(projects.sourceApi)
    api(projects.sourceLocal)
    api(projects.presentationCore)
    implementation(projects.feature.reader)
    implementation(projects.feature.webview)
    implementation(projects.feature.category)
    implementation(projects.feature.settings)
    api(projects.feature.migration)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.logcat)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.bundles.markdown)
    implementation(libs.swipe)

    testImplementation(libs.bundles.test)
}

