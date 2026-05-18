import ephyra.buildlogic.AndroidConfig
import ephyra.buildlogic.generatedBuildDir
import ephyra.buildlogic.tasks.getLocalesConfigTask

plugins {
    id("mihon.library.multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.moko)
}

kotlin {
    android {
        namespace = "ephyra.i18n"
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                api(libs.moko.core)
            }
        }

        androidMain {
            resources.srcDirs(
                "src/commonMain/resources",
                generatedBuildDir.resolve("android/res"),
            )
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

multiplatformResources {
    resourcesPackage.set("ephyra.i18n")
}

tasks {
    val generatedAndroidResourceDir = generatedBuildDir.resolve("android/res")
    val localesConfigTask = project.getLocalesConfigTask(generatedAndroidResourceDir)
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>> {
        dependsOn(localesConfigTask)
    }
}
