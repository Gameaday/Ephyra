plugins {
    id("ephyra.library")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "ephyra.data"
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("ephyra.data")
        }
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.domain)
    implementation(projects.core.common)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines)
}
