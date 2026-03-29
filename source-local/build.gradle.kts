import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("ephyra.library.multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    android {
        // Matches your folder structure: ephyra/source/local
        namespace = "ephyra.source.local"

        optimization {
            consumerKeepRules.file("consumer-rules.pro")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.sourceApi)
            api(projects.i18n)
            implementation(libs.unifile)
        }

        androidMain.dependencies {
            implementation(projects.core.archive)
            implementation(projects.core.common)
            implementation(projects.coreMetadata)
            implementation(projects.domain)
            implementation(kotlinx.bundles.serialization)
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}
