plugins {
    id("mihon.library")
    id("mihon.library.compose")

    alias(libs.plugins.hilt)
    id("com.google.devtools.ksp")
}

android {
    namespace = "ephyra.feature.more"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.presentationCore)

    ksp(libs.hilt.compiler)
}
