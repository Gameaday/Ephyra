package ephyra.buildlogic

import org.gradle.api.Project

interface BuildConfig {
    val includeTelemetry: Boolean
    val enableUpdater: Boolean
    val enableCodeShrink: Boolean
    val includeDependencyInfo: Boolean

    /**
     * Gates developer-only shortcuts that hard-code pointers to third-party
     * extension catalogs (e.g. the "Use official Mihon repo" one-tap button).
     * Shipping a built-in pointer to a manga catalog in release builds is a
     * potential piracy liability, so release/F-Droid/Play builds must not set
     * this. Enable locally with `-Pinclude-catalog-shortcuts`.
     */
    val includeCatalogShortcuts: Boolean
}

val Project.Config: BuildConfig
    get() = object : BuildConfig {
        override val includeTelemetry: Boolean = project.hasProperty("include-telemetry")
        override val enableUpdater: Boolean = project.hasProperty("enable-updater")
        override val enableCodeShrink: Boolean = !project.hasProperty("disable-code-shrink")
        override val includeDependencyInfo: Boolean = project.hasProperty("include-dependency-info")
        override val includeCatalogShortcuts: Boolean = project.hasProperty("include-catalog-shortcuts")
    }
