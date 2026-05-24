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

    implementation(libs.compose.runtime)

    implementation(projects.core.common)
    api(libs.preferencektx)

    implementation(libs.hilt.android)

    implementation(kotlinx.coroutines.android)
    implementation(platform(kotlinx.coroutines.bom))
}
