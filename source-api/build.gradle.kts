import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import ephyra.buildlogic.AndroidConfig

plugins {
    id("mihon.library.multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    android {
        namespace = "eu.kanade.tachiyomi.source"
        compileSdk = AndroidConfig.COMPILE_SDK
        minSdk = AndroidConfig.MIN_SDK

        defaultConfig {
            consumerProguardFile("consumer-proguard.pro")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlinx.serialization.json)
                api(libs.koin.core)
                api(libs.jsoup)

                implementation(project.dependencies.platform(compose.bom))
                implementation(compose.runtime)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(projects.core.common)
                api(libs.preferencektx)

                // Workaround for https://youtrack.jetbrains.com/issue/KT-57605
                implementation(kotlinx.coroutines.android)
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}


