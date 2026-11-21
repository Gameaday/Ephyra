plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.manga"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi",
        )
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.presentationCore)
    implementation(projects.feature.reader)
    implementation(projects.feature.migration)

    // Declared: manga uses coil3 for covers. It used to arrive only through presentation-core.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.logcat)
    implementation(platform(libs.coil.bom))
    implementation(libs.bundles.coil)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.bundles.markdown)
    implementation(libs.swipe)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
