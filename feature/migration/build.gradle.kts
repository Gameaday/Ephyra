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
    implementation(projects.presentationCore)
    implementation(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.sourceApi)
    implementation(projects.core.data)
    implementation(projects.core.download)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
