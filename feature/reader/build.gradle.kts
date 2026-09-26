plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.reader"

    testOptions {
        unitTests {
            // Required for Robolectric-based Compose UI tests (resource loading).
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.presentationCore)
    implementation(projects.feature.webview)

    implementation(libs.logcat)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testDebugImplementation(libs.compose.ui.test.manifest)

    // E3 instrumentation. The JVM harness cannot observe a `graphicsLayer` rasterisation --
    // `B-024` records that limit and the control test that established it -- so the
    // "computed transform actually reaches the screen" claim for `DEF-001` is provable only here.
    // `feature:reader` is the first library module with an `androidTest` source set. The AndroidX
    // test artifacts are declared in gradle/androidx.versions.toml under the `androidx` catalog,
    // but that catalog is not addressable as `libs.androidx.*` from a module script in this build;
    // `:app` reaches the same artifacts through the AGP `androidx.test` extension instead. The
    // coordinates are therefore spelled out, and these three versions must be kept in step with
    // gradle/androidx.versions.toml by hand.
    androidTestImplementation("androidx.test.ext:junit-ktx:1.3.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
