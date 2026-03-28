plugins {
    id("ephyra.library")
}

android {
    namespace = "ephyra.core.data"
}

dependencies {
    api(projects.data)
    api(projects.core.domain)
    
    implementation(projects.core.common)
    implementation(projects.core.archive)
    implementation(projects.core.download)
    
    implementation(libs.unifile)
    implementation(libs.coil.core)
    implementation(libs.okhttp.core)
    
    api(libs.koin.core)
    api(kotlinx.coroutines.core)
}
