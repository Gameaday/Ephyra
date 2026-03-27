import ephyra.buildlogic.AndroidConfig
import ephyra.buildlogic.generatedBuildDir
import ephyra.buildlogic.tasks.getLocalesConfigTask

plugins {
    id("mihon.library.multiplatform")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.moko)
}

kotlin {
    androidTarget()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                api(libs.moko.core)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

android {
    namespace = "ephyra.i18n"

    sourceSets {
        getByName("main") {
            res.srcDirs(
                "src/commonMain/resources",
                generatedBuildDir.resolve("android/res"),
            )
        }
    }
}

multiplatformResources {
    resourcesPackage.set("ephyra.i18n")
}

tasks {
    val generatedAndroidResourceDir = generatedBuildDir.resolve("android/res")
    val localesConfigTask = project.getLocalesConfigTask(generatedAndroidResourceDir)
    preBuild {
        dependsOn(localesConfigTask)
    }
}
