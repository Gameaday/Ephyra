import ephyra.buildlogic.Config

plugins {
    id("ephyra.library")
}

android {
    namespace = "ephyra.telemetry"

    sourceSets {
        getByName("main") {
            if (Config.includeTelemetry) {
                java.srcDir("src/firebase/kotlin")
            } else {
                java.srcDir("src/noop/kotlin")
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
