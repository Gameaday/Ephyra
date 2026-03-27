import ephyra.buildlogic.AndroidConfig

plugins {
    id("mihon.library.multiplatform")

    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

kotlin {
    android {
        namespace = "tachiyomi.data"
        compileSdk = AndroidConfig.COMPILE_SDK
        minSdk = AndroidConfig.MIN_SDK

        defaultConfig {
            consumerProguardFiles("consumer-rules.pro")
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.core.common)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
}

