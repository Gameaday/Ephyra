plugins {
    id("ephyra.library")
    id("ephyra.library.compose")

    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.more"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.sourceApi)
    implementation(projects.presentationCore)

    implementation(projects.feature.settings)
    // Direct, not transitive through feature:settings: the widgets it uses live in the settings
    // design-system module now, and `implementation` does not re-export.
    implementation(project(":feature:settings-ui"))
    implementation(projects.feature.manga)
    implementation(libs.bundles.markdown)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
