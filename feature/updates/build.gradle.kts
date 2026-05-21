plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.updates"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.presentationCore)

    implementation(projects.feature.download)
    implementation(projects.feature.manga)
    implementation(projects.feature.reader)
    implementation(projects.feature.upcoming)

    ksp(libs.hilt.compiler)
}
