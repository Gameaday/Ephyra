plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
}

android {
    namespace = "ephyra.presentation.widget"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.presentationCore)

    implementation(compose.glance)
    implementation(libs.material)

    implementation(kotlinx.immutables)

    implementation(platform(libs.coil.bom))
    implementation(libs.coil.core)
}
