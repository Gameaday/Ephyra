import org.gradle.internal.impldep.org.eclipse.jgit.diff.Subsequence.a

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(androidx.gradle)
    implementation(kotlinx.gradle)
    implementation(libs.kotlin.compose.gradle)
    implementation(libs.spotless.gradle)
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.7")
    implementation("com.google.dagger:hilt-android-gradle-plugin:2.51.1")
    implementation(gradleApi())
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
}
