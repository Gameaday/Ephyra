plugins {
    id("mihon.library")
    id("mihon.library.compose")

    id("com.google.devtools.ksp")
}

android {
    namespace = "ephyra.feature.manga"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.sourceLocal)
    api(projects.i18n)
    api(projects.presentationCore)

    implementation(libs.logcat)
    implementation(libs.bundles.voyager)

    testImplementation(libs.bundles.test)
}

