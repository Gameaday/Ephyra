package ephyra.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.time.Duration

val Project.androidxCatalog get() = extensions.getByType<VersionCatalogsExtension>().named("androidx")
val Project.composeCatalog get() = extensions.getByType<VersionCatalogsExtension>().named("compose")
val Project.kotlinxCatalog get() = extensions.getByType<VersionCatalogsExtension>().named("kotlinx")
val Project.libsCatalog get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun VersionCatalog.getLib(name: String) =
    findLibrary(name).orElseThrow { NoSuchElementException("Library $name not found in catalog") }.get()

private fun VersionCatalog.getPlugin(name: String) =
    findPlugin(name).orElseThrow { NoSuchElementException("Plugin $name not found in catalog") }.get()

internal fun Project.configureAndroid(commonExtension: CommonExtension) {
    val compileSdkValue = AndroidConfig.COMPILE_SDK
    val minSdkVersion = AndroidConfig.MIN_SDK

    when (commonExtension) {
        is ApplicationExtension -> {
            commonExtension.compileSdk = compileSdkValue
            commonExtension.defaultConfig.minSdk = minSdkVersion
            commonExtension.compileOptions {
                sourceCompatibility = AndroidConfig.JavaVersion
                targetCompatibility = AndroidConfig.JavaVersion
            }
            commonExtension.lint {
                abortOnError = false
                checkReleaseBuilds = false
                lintConfig = rootProject.file("lint.xml")
                baseline = file("lint-baseline.xml")
                checkDependencies = true
                ignoreTestSources = true
            }
        }

        is LibraryExtension -> {
            commonExtension.compileSdk = compileSdkValue
            commonExtension.defaultConfig.minSdk = minSdkVersion
            commonExtension.compileOptions {
                sourceCompatibility = AndroidConfig.JavaVersion
                targetCompatibility = AndroidConfig.JavaVersion
            }
            commonExtension.lint {
                abortOnError = false
                checkReleaseBuilds = false
                lintConfig = rootProject.file("lint.xml")
                baseline = file("lint-baseline.xml")
                checkDependencies = false
                ignoreTestSources = true
            }
        }

        is TestExtension -> {
            commonExtension.compileSdk = compileSdkValue
            commonExtension.defaultConfig.minSdk = minSdkVersion
            commonExtension.compileOptions {
                sourceCompatibility = AndroidConfig.JavaVersion
                targetCompatibility = AndroidConfig.JavaVersion
            }
            commonExtension.lint {
                abortOnError = false
                checkReleaseBuilds = false
                lintConfig = rootProject.file("lint.xml")
                baseline = file("lint-baseline.xml")
                checkDependencies = false
                ignoreTestSources = true
            }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(AndroidConfig.JvmTarget)
            freeCompilerArgs.addAll(
                "-language-version", "2.3",
                "-api-version", "2.3",
                "-Xcontext-parameters",
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlin.ExperimentalStdlibApi",
            )

            val warningsAsErrors = project.providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false)
            allWarningsAsErrors.set(warningsAsErrors)
        }
    }

    val kotlinVersion = libsCatalog.findVersion("kotlin").get().requiredVersion
    configurations.all {
        resolutionStrategy {
            eachDependency {
                if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-metadata-jvm") {
                    useVersion(kotlinVersion)
                }
            }
        }
    }

    // Robolectric's FileDescriptorInterceptor accesses jdk.internal.access.SharedSecrets
    // via reflection; JDK 17+ module system blocks this without an explicit --add-opens.
    tasks.withType<Test>().configureEach {
        jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
    }
}

internal fun Project.configureCompose(commonExtension: CommonExtension) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

    when (commonExtension) {
        is ApplicationExtension -> {
            commonExtension.buildFeatures.compose = true
        }

        is LibraryExtension -> {
            commonExtension.buildFeatures.compose = true
        }
    }

    commonExtension.apply {
        dependencies {
            "implementation"(platform(composeCatalog.getLib("compose-bom")))
        }
    }

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        val enableMetrics = project.providers.gradleProperty("enableComposeCompilerMetrics").orNull.toBoolean()
        val enableReports = project.providers.gradleProperty("enableComposeCompilerReports").orNull.toBoolean()

        val rootBuildDir = rootProject.layout.buildDirectory
        val relativePath = projectDir.relativeTo(rootDir)

        if (enableMetrics) {
            metricsDestination.set(rootBuildDir.dir("compose-metrics").map { it.dir(relativePath.path) })
        }

        if (enableReports) {
            reportsDestination.set(rootBuildDir.dir("compose-reports").map { it.dir(relativePath.path) })
        }

        val stabilityConfig = rootProject.layout.projectDirectory.file("app/compose_stability.conf")
        if (stabilityConfig.asFile.exists()) {
            stabilityConfigurationFiles.add(stabilityConfig)
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        val hasMaterial3 = listOf("implementation", "api", "compileOnly").any { configName ->
            project.configurations.findByName(configName)?.dependencies?.any { dep ->
                dep.name == "presentation-core" ||
                dep.name == "material3" ||
                dep.group == "androidx.compose.material3"
            } ?: false
        }
        if (hasMaterial3) {
            compilerOptions {
                freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
            }
        }
    }
}

internal fun Project.configureTest() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Bound the number of forked test JVMs. Every module schedules its own Test tasks and
        // `org.gradle.parallel=true` runs those tasks concurrently, so forking on every processor
        // spawns hundreds of JVMs during a full build. Robolectric (API 36) forks are memory heavy,
        // and oversubscribing CPU/memory kills test workers, which surfaces as
        // `java.io.EOFException` / `NoSuchFileException` on the test task instead of a test failure.
        maxParallelForks = 1
        // Fail a hung suite instead of letting it block the build forever.
        timeout.set(Duration.ofMinutes(10))
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }
}

val Project.generatedBuildDir: File get() = project.layout.buildDirectory.asFile.get().resolve("generated/ephyra")
