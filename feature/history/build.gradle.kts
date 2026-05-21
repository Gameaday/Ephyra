plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.history"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.presentationCore)

    implementation(projects.feature.category)
    implementation(projects.feature.manga)
    implementation(projects.feature.migration)
    implementation(projects.feature.reader)

    ksp(libs.hilt.compiler)
}
