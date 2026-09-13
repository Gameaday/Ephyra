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
    implementation(projects.core.data)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.presentationCore)
    implementation(projects.feature.reader)
    implementation(projects.feature.webview)
    implementation(projects.feature.category)
    implementation(projects.feature.settings)
    implementation(projects.feature.migration)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.logcat)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.bundles.markdown)
    implementation(libs.swipe)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
