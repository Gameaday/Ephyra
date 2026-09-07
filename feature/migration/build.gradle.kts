plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.migration"
}

dependencies {
    api(projects.presentationCore)
    api(projects.core.domain)
    api(projects.core.common)
    api(projects.sourceApi)
    implementation(projects.core.data)
    implementation(projects.core.download)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
