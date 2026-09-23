plugins {
    id("com.android.application") apply false
    id("com.android.library") apply false
    alias(kotlinx.plugins.serialization) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// Module-wide build configuration (SDK levels, Kotlin compiler options, test tuning, lint) lives in
// the `ephyra.*` convention plugins under buildSrc. Anything configured through
// `tasks.withType<...>` in this file would only ever apply to this source-less root project and
// silently do nothing.
