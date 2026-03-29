plugins {
    id("ephyra.library")
    kotlin("plugin.serialization")
}

android {
    namespace = "ephyra.core.download"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
        )
    }
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.core.domain)
    api(projects.core.data)
    api(projects.core.archive)
    api(projects.coreMetadata)
    api(projects.sourceApi)
    api(projects.sourceLocal)
    api(projects.i18n)

    implementation(androidx.workmanager)
    implementation(libs.logcat)
    implementation(libs.unifile)

    implementation(kotlinx.bundles.serialization)
}
