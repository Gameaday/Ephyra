plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.stats"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.presentationCore)

    ksp(libs.hilt.compiler)
}
