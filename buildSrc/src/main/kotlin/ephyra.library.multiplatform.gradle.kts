import ephyra.buildlogic.configureAndroidMultiplatform
import ephyra.buildlogic.configureTest

plugins {
    id("com.android.kotlin.multiplatform.library")
    kotlin("multiplatform")

    id("ephyra.code.lint")
}

kotlin {
    android {
        configureAndroidMultiplatform(this)
    }

    configureTest()
}

// Android Multiplatform plugin does not provide standard assemble tasks by default.
// Adding them for compatibility with standard Android project commands.
tasks.register("assembleDebug") {
    dependsOn("assemble")
}

tasks.register("assembleRelease") {
    dependsOn("assemble")
}
