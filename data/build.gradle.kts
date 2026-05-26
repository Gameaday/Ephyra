plugins {
    id("ephyra.library")
    kotlin("plugin.serialization")
    // Keep KSP for Room database processing
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.data"
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.core.domain)
    implementation(projects.core.common)

    // Room - Processed via KSP
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
}
