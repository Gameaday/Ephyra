plugins {
    id("ephyra.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "tachiyomi.source.local"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(projects.sourceApi)

    implementation(libs.unifile)

    implementation(projects.core.archive)
    implementation(projects.core.common)
    implementation(projects.coreMetadata)

    // Move ChapterRecognition to separate module?
    implementation(projects.core.domain)

    implementation(libs.bundles.sqlite)
    implementation(kotlinx.bundles.serialization)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}
