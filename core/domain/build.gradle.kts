plugins {
    id("ephyra.library")
}

android {
    namespace = "ephyra.core.domain"
}

dependencies {
    api(projects.domain)
    api(projects.coreMetadata)
    implementation(projects.core.common)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.data)
    implementation(androidx.workmanager)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.stringSimilarity)

    api(libs.koin.core)
    implementation(libs.koin.androidx.workmanager)
    api(kotlinx.coroutines.core)
}
