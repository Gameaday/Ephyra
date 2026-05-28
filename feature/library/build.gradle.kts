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
    api(projects.core.domain)
    api(projects.core.data)
    api(projects.sourceApi)
    api(projects.presentationCore)
    api(projects.feature.manga)
    implementation(projects.feature.category)
    implementation(projects.feature.browse)
    implementation(projects.feature.reader)

    implementation(libs.logcat)

    ksp(libs.hilt.compiler)
    testImplementation(libs.bundles.test)
}

