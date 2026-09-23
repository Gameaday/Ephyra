import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("com.diffplug.spotless")
}

val catalogs = project.extensions.getByType<VersionCatalogsExtension>()
val libs = catalogs.named("libs")

spotless {
    kotlin {
        target("**/*.kt", "**/*.kts")
        targetExclude("**/build/**/*.kt")

        val ktlintVersion = libs.findVersion("ktlint-core").get().requiredVersion
        ktlint(ktlintVersion)

        trimTrailingWhitespace()
        endWithNewline()
    }
    format("xml") {
        target("**/*.xml")
        targetExclude("**/build/**/*.xml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.register("ktlintCheck") {
    group = "verification"
    description = "Runs ktlint check via Spotless"
    dependsOn("spotlessKotlinCheck")
}

// NOTE: there is deliberately no `detekt` task here. This build does not apply the detekt plugin,
// so a task registered under that name would silently do nothing while CI reported a passing
// "static analysis" step. Wire a real detekt (plugin + config + baseline) before re-adding one, and
// add it to the CI verification command at the same time.
