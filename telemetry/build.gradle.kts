import ephyra.buildlogic.Config

plugins {
    id("ephyra.library")
}

android {
    namespace = "ephyra.telemetry"

    sourceSets {
        getByName("main") {
            if (Config.includeTelemetry) {
                kotlin.directories.add("src/firebase/kotlin")
            } else {
                kotlin.directories.add("src/noop/kotlin")
            }
        }
    }
}

dependencies {
    if (Config.includeTelemetry) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.analytics)
        implementation(libs.firebase.crashlytics)
    }
}
