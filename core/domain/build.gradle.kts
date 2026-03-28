plugins {
    id("ephyra.library")
}

android {
    namespace = "ephyra.core.domain"
}

dependencies {
    api(projects.domain)
    implementation(projects.core.common)

    api(libs.koin.core)
    api(kotlinx.coroutines.core)
}
