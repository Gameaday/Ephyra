plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.settings"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.presentationCore)
    implementation(projects.feature.category)
    // Referenced as a string, not a type-safe accessor: Gradle generates no accessor for a module
    // whose name contains a hyphen.
    implementation(project(":feature:settings-ui"))

    // External dependencies
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.aboutLibraries.compose)
    implementation(androidx.profileinstaller)
    implementation(kotlinx.bundles.serialization)
    implementation(kotlinx.bundles.coroutines)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
