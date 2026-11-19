plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
}

android {
    namespace = "ephyra.feature.settings.ui"
}

// The settings design system: the preference widget vocabulary, the `Preference` model, the
// scaffold and screen that render them, and the `SearchableSettings` contract the screens implement.
//
// Split out of `feature:settings` because it is a *design system with one consumer*, not a feature:
// all 12 widgets had zero references outside settings, so a re-theme had to edit a module that also
// contained the data screens, the backup flows and the tracker OAuth activities. Keeping the
// vocabulary separate means a re-theme is a change to one composable-only module.
//
// No Hilt: nothing here injects. The ViewModels that back settings stay in `feature:settings`, which
// is what makes the boundary meaningful -- this module cannot grow a dependency on app state.
dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.presentationCore)

    implementation(androidx.annotation)
    implementation(kotlinx.bundles.coroutines)
    implementation(kotlinx.immutables)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
