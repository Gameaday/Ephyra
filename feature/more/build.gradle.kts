plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.more"
}

dependencies {
    api(projects.core.common)
    api(projects.core.domain)
    api(projects.core.data)
    api(projects.sourceApi)
    api(projects.presentationCore)

    implementation(projects.feature.category)
    implementation(projects.feature.download)
    implementation(projects.feature.settings)
    implementation(projects.feature.stats)
    implementation(projects.feature.manga)
    implementation(libs.bundles.markdown)

    ksp(libs.hilt.compiler)
}

