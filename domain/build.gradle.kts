plugins {
    id("ephyra.library")

    kotlin("plugin.serialization")
}

android {
    namespace = "tachiyomi.domain"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.core.common)
    api(projects.i18n)

    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)
    implementation(kotlinx.bundles.serialization)

    implementation(libs.unifile)
    implementation(kotlinx.immutables)
    // PagingSource<Long, Manga> is used by SourceRepository / GetRemoteManga.
    compileOnly(libs.paging.common)

    compileOnly(platform(compose.compose.bom))
    compileOnly(compose.runtime.annotation)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
