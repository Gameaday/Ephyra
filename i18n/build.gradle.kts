import ephyra.buildlogic.AndroidConfig
import ephyra.buildlogic.generatedBuildDir
import ephyra.buildlogic.tasks.getLocalesConfigTask

plugins {
    id("ephyra.library.multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.moko)
}

kotlin {
    android {
        namespace = "ephyra.i18n"
        compileSdk = AndroidConfig.COMPILE_SDK
        minSdk = AndroidConfig.MIN_SDK
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                api(libs.moko.core)
            }
        }

        // moko-resources 0.26.x generates the actual MR class into
        // build/generated/moko-resources/androidMain/src/ but the AKMP plugin
        // (com.android.kotlin.multiplatform.library) does not automatically include
        // that directory in its compilation. Wire it in explicitly so the Kotlin compiler
        // can resolve the `actual object MR` declaration.
        androidMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/moko-resources/androidMain/src"))
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

multiplatformResources {
    resourcesPackage.set("ephyra.i18n")
}

// The moko-resources AKMP generator writes actual MR.kt into androidMain/src/ but does not
// declare that directory as a Gradle task output, so Gradle's up-to-date check can mark the
// task as UP-TO-DATE even when the src/ tree was cleaned. Register the directory as an
// explicit output so Gradle knows to re-run the task if it is missing.
afterEvaluate {
    tasks.named("generateMRandroidMain") {
        outputs.dir(layout.buildDirectory.dir("generated/moko-resources/androidMain/src"))
    }
}

// Ensure the AKMP androidMain compilation waits for moko to generate the actual MR class.
tasks.matching { it.name.startsWith("compile") && it.name.contains("AndroidMain") }.configureEach {
    dependsOn("generateMRandroidMain")
}

val generatedAndroidResourceDir = generatedBuildDir.resolve("android/res")
val localesConfigTask = project.getLocalesConfigTask(generatedAndroidResourceDir)

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(localesConfigTask)
}
