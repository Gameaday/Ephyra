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

    // `espresso-core` reaches the classpath transitively, via `ui-test-junit4`, and resolves to
    // 3.5.0. That version calls `android.hardware.input.InputManager.getInstance`, which was
    // removed in API 37, so `Espresso.onIdle` — and therefore Compose's entire idle-sync path,
    // `EspressoLink.runUntilIdle` — dies with `NoSuchMethodException` before a test body runs.
    // This is a toolchain incompatibility with the emulator's API level, not a product defect,
    // and it is what `B-024` records.
    //
    // Pinned rather than upgraded at the source: the transitive version is not ours to choose
    // directly, and the `androidx` catalog that declares 3.7.0 is not addressable as `libs.*` from
    // a module script here. Forcing the resolved version is the smallest change that makes the
    // `E3` channel usable, and it is consistent with gradle/androidx.versions.toml.
    constraints {
        androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    }
}
