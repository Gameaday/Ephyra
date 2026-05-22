plugins {
    id("ephyra.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "ephyra.core.domain"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(projects.coreMetadata)
    implementation(projects.core.common)
    implementation(projects.sourceApi)
    implementation(androidx.workmanager)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.stringSimilarity)

    api(kotlinx.coroutines.core)

    // Dependencies from unified root domain module
    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)
    implementation(kotlinx.bundles.serialization)

    implementation(libs.unifile)
    implementation(kotlinx.immutables)
    compileOnly(libs.paging.common)

    compileOnly(platform(compose.compose.bom))
    compileOnly(compose.runtime.annotation)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
// Suppress warnings for the following:

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}
