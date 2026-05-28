import ephyra.buildlogic.getBuildTime
import ephyra.buildlogic.getCommitCount
import ephyra.buildlogic.getGitSha

plugins {
    id("ephyra.library")
    kotlin("plugin.serialization")
    alias(libs.plugins.ksp)
}

android {
    namespace = "ephyra.core.data"

    defaultConfig {
        buildConfigField("String", "COMMIT_COUNT", "\"${getCommitCount()}\"")
        buildConfigField("String", "COMMIT_SHA", "\"${getGitSha()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime(useLastCommitTime = false)}\"")
        buildConfigField("String", "APPLICATION_ID", "\"app.ephyra\"")
        buildConfigField("String", "VERSION_NAME", "\"0.20.0\"")
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        getByName("test") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(projects.core.domain)

    implementation(projects.core.common)
    implementation(projects.core.archive)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)

    implementation(libs.unifile)
    implementation(platform(libs.coil.bom))
    implementation(libs.coil.core)
    implementation(libs.okhttp.core)

    implementation(kotlinx.bundles.serialization)
    implementation(kotlinx.immutables)

    api(kotlinx.coroutines.core)

    // Room - Processed via KSP
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    implementation("javax.inject:javax.inject:1")
    implementation(libs.stringSimilarity)

    // Testing Dependencies
    testImplementation(libs.room.testing)
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
// Suppress warnings for the following:

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
