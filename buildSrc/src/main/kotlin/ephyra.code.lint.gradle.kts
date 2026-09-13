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

tasks.register("detekt") {
    group = "verification"
    description = "Runs static code analysis"
}
