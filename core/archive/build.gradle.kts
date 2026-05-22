plugins {
    id("ephyra.library")
}

android {
    namespace = "ephyra.core.archive"
}

dependencies {
    implementation(libs.jsoup)
    implementation(libs.libarchive)
    implementation(libs.unifile)
}
