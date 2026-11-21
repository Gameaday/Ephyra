plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.feature.reader"

    defaultConfig {
        // Required for the `E3` channel. Without an explicit runner, instrumentation fails with
        // `INSTRUMENTATION_FAILED: .../androidx.test.runner.AndroidJUnitRunner` and — the part that
        // matters — Gradle reports the task **SUCCESSFUL** with `tests="0"`. A green build that ran
        // nothing is worse than a red one, because it reads as evidence. Declared here to match
        // `:app`, which has always set it.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests {
            // Required for Robolectric-based Compose UI tests (resource loading).
            isIncludeAndroidResources = true
        }

        // Forward the fixture materialisation switch into the test JVM. Gradle does not pass
        // arbitrary `-D` flags through to forked test processes, so without this
        // `-Dfixture.materialise=true` silently leaves `FixtureMaterialiser` SKIPPED — a green
        // run that wrote nothing, which is the same "looks like success, did nothing" shape as the
        // missing `testInstrumentationRunner` recorded in `B-030`.
        unitTests.all {
            it.systemProperty(
                "fixture.materialise",
                System.getProperty("fixture.materialise") ?: "false",
            )

            // Robolectric + Compose does not fit Gradle's 512m default test-worker heap. The
            // worker is killed by the OOM killer before it can flush its results, and Gradle then
            // reports the symptom rather than the cause:
            //
            //   java.nio.file.NoSuchFileException: ...\binary\in-progress-results-generic.bin
            //
            // which reads like corrupt output and is not. `:app` has carried `maxHeapSize = "2g"`
            // for this; this module is the other half of the same problem, since it is the one that
            // actually runs the heaviest Compose reader tests. Any module with
            // `isIncludeAndroidResources = true` needs the same heap.
            it.maxHeapSize = "2g"

            // One JVM per test class, matching `:app` (see app/build.gradle.kts for the full
            // rationale). This module is the one whose CI worker died mid-discovery with
            // "TestEngine with ID 'junit-vintage' failed to discover tests", and a fresh JVM per
            // class is the fix that addresses the mechanism rather than the symptom:
            //
            //  * Robolectric installs a sandbox per class and Compose/Coil retain native bitmap
            //    and shadow state. Across 50 classes in one long-lived worker that memory only
            //    grows, so the worker is killed while discovering a *later* class -- which is
            //    exactly the reported shape (death during discovery, not during an assertion).
            //    Forking per class releases all of it at the class boundary and bounds the peak.
            //  * It also removes the shadow-installation-order hazard `:app` documented: a
            //    `Resources` shadow installed without ours fails a cast in any later test that
            //    reaches DecorView, and whether that happens depends on what shared the JVM.
            //
            // The cost is JVM startup per class, paid in CI rather than as a test that fails for
            // reasons unrelated to what it tests.
            it.forkEvery = 1
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    testImplementation(projects.core.data)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.presentationCore)

    implementation(libs.logcat)
    implementation(platform(libs.coil.bom))
    implementation(libs.bundles.coil)
    implementation(libs.okio)
    implementation(libs.unifile)

    // Hilt
    // Declared: reader uses coil3, okio and unifile. All used to arrive only through presentation-core.
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
