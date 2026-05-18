plugins {
    id("ephyra.library.compose")
    kotlin("plugin.serialization")
}

android {
    namespace = "eu.kanade.tachiyomi.source"

    defaultConfig {
        consumerProguardFiles("consumer-proguard.pro")
    }
}

dependencies {
    api(kotlinx.serialization.json)
    api(libs.jsoup)

    implementation("androidx.compose.runtime:runtime:1.6.7")

    implementation(projects.core.common)
    api(libs.preferencektx)

    implementation(kotlinx.coroutines.android)
    implementation(platform(kotlinx.coroutines.bom))
}
