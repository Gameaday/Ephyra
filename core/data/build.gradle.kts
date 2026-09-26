import ephyra.buildlogic.getBuildTime
import ephyra.buildlogic.getCommitCount
import ephyra.buildlogic.getGitSha

plugins {
    id("ephyra.library")
    kotlin("plugin.serialization")
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.core.data"

    defaultConfig {
        buildConfigField("String", "COMMIT_COUNT", "\"${getCommitCount()}\"")
        buildConfigField("String", "COMMIT_SHA", "\"${getGitSha()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime(useLastCommitTime = false)}\"")
        buildConfigField("String", "APPLICATION_ID", "\"app.ephyra\"")
        buildConfigField("String", "VERSION_NAME", "\"0.21.0\"")

        // Required for the `E3` channel. Without it, instrumentation fails with
        // `INSTRUMENTATION_FAILED` and Gradle still reports the task SUCCESSFUL with `tests="0"` --
        // a green device task that ran nothing. Same defect fixed in `feature:reader`; see B-030.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        getByName("test") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Robolectric is only pulled in by the Room/DAO suites; plain JVM tests still touch
            // android.util.Log through the project's `logcat` helper, which must not throw.
            isReturnDefaultValues = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(projects.core.domain)

    implementation(projects.core.common)
    implementation(projects.core.archive)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)

    implementation(libs.unifile)
    implementation(platform(libs.coil.bom))
    implementation(libs.coil.core)
    api(libs.jxl.coder.coil)
    implementation(libs.okhttp.core)

    implementation(kotlinx.bundles.serialization)
    implementation(kotlinx.immutables)

    api(kotlinx.coroutines.core)

    // Room - Processed via KSP
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    implementation("javax.inject:javax.inject:1")
    implementation(libs.stringSimilarity)

    // Testing Dependencies
    testImplementation(kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // E3 instrumentation, for the media pipeline in this module: the JXL bridge signature
    // (`TST-001C3`) and the border-crop transformation (`DEF-008`). Both are decode paths, and a
    // decode is exactly the claim a JVM harness cannot make — the E2 suites cover the decision
    // logic, not the result of running the codec on the device's own decoder.
    //
    // The espresso pin is required for API 37. `ui-test-junit4` brings `espresso-core` 3.5.0
    // transitively, which calls `InputManager.getInstance` — removed in API 37 — so
    // `Espresso.onIdle` dies with `NoSuchMethodException` before any test body runs. Fixed the same
    // way in `feature:reader`; see B-024.
    androidTestImplementation("androidx.test.ext:junit-ktx:1.3.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    constraints {
        androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    }
}
// Suppress warnings for the following:

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}
