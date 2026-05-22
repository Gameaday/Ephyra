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
    api(projects.core.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.presentationCore)
    api(projects.feature.manga)
    api(projects.feature.category)

    implementation(libs.logcat)

    testImplementation(libs.bundles.test)
}
