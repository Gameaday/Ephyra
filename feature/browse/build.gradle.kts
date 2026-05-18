plugins {
    id("mihon.library")
    id("mihon.library.compose")

    id("com.google.devtools.ksp")
}

android {
    namespace = "ephyra.feature.browse"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.presentationCore)
    api(projects.feature.manga)

    implementation(libs.logcat)
    implementation(libs.bundles.voyager)

    testImplementation(libs.bundles.test)
}

