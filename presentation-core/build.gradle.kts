plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
    kotlin("plugin.serialization")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.presentation.core"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
}

dependencies {
    // Internal Layers
    api(projects.core.common)
    api(projects.core.domain)
    api(projects.core.data)
    api(projects.core.archive)
    api(projects.core.download)
    api(projects.sourceLocal)
    api(projects.sourceApi)

    // Compose Core
    api(compose.activity)
    api(compose.foundation)
    api(compose.material3.core)
    api(compose.material.icons)
    api(compose.animation)
    api(compose.animation.graphics)
    debugImplementation(compose.ui.tooling)
    api(compose.ui.tooling.preview)
    api(compose.ui.util)

    // Essential UI/Lifecycle Libraries
    api(androidx.paging.runtime)
    api(androidx.paging.compose)
    api(androidx.appcompat)
    api(androidx.lifecycle.runtimektx)
    api(kotlinx.immutables)
    api(androidx.lifecycle.runtime.compose)
    api(libs.materialKolor)
    // Not `api`: nothing in this module imports either. `material` is reached only by
    // feature:settings' OpenSourceLibraryLicenseScreen, and `materialmotion` by nobody --
    // they were transitive here, so every one of the 19 consumers inherited a Material
    // Components dependency it never declared.
    implementation(libs.material)
    implementation(libs.compose.materialmotion)
    // Stays `api`, but for an accident worth knowing: this module never imports
    // sh.calvin.reorderable, yet feature:category, feature:migration, feature:download and
    // feature:settings all use it and none declare it. They get it through this line. That
    // is invisible coupling -- deleting the line would break four modules that look
    // self-contained. Giving each of them its own dependency is the fix; it needs their
    // build files touched, so it is recorded rather than bundled in here.
    api(libs.reorderable)

    // Image Loading & Files
    api(platform(libs.coil.bom))
    api(libs.bundles.coil)
    api(libs.unifile)
    api(libs.okio)

    // Navigation & DI
    api(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(libs.hilt.navigation.compose)
    api(libs.navigation.compose)

    // Utilities
    api(androidx.biometricktx)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
