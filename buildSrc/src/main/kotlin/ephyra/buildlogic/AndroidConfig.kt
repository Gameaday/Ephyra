package ephyra.buildlogic

import org.gradle.api.JavaVersion as GradleJavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget as KotlinJvmTarget

object AndroidConfig {
    // 37 required by Coil 3.6.x AAR metadata (compileSdk can lead targetSdk).
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 36
    const val MIN_SDK = 34

    // https://youtrack.jetbrains.com/issue/KT-66995/JvmTarget-and-JavaVersion-compatibility-for-easier-JVM-version-setup
    val JavaVersion = GradleJavaVersion.VERSION_17
    val JvmTarget = KotlinJvmTarget.JVM_17
}
