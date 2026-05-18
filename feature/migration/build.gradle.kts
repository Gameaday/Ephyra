plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    alias(libs.plugins.hilt)
    id("com.google.devtools.ksp")
}

android {
    namespace = "ephyra.feature.migration"
}

dependencies {
    api(projects.presentationCore)
    api(projects.domain)
    implementation(projects.core.data)
    implementation(projects.core.download)

    ksp(libs.hilt.compiler)
}
