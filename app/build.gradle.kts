
import com.android.build.api.variant.BuildConfigField
import ephyra.buildlogic.Config
import ephyra.buildlogic.buildTimeProvider
import ephyra.buildlogic.commitCountProvider
import ephyra.buildlogic.gitShaProvider

plugins {
    id("ephyra.android.application")
    id("ephyra.android.application.compose")
    kotlin("plugin.serialization")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.aboutLibraries)
}

if (Config.includeTelemetry) {
    pluginManager.apply {
        apply(libs.plugins.google.services.get().pluginId)
        apply(libs.plugins.firebase.crashlytics.get().pluginId)
    }
}

// Single source for the base version name; per-build-type suffixes are appended lazily in
// androidComponents below.
val defaultVersionName = "0.21.0"

android {
    namespace = "ephyra.app"

    // NDK r29+ is required for ARMv9.2-A (arm64-v8a with SVE2/SME) support used by
    // devices such as the Samsung Galaxy S24 series (Snapdragon 8 Gen 3 / Exynos 2400).
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "app.ephyra"

        versionCode = 21
        versionName = defaultVersionName

        buildConfigField("boolean", "TELEMETRY_INCLUDED", "${Config.includeTelemetry}")
        buildConfigField("boolean", "UPDATER_ENABLED", "${Config.enableUpdater}")
        buildConfigField("boolean", "INCLUDE_CATALOG_SHORTCUTS", "${Config.includeCatalogShortcuts}")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // arm64-v8a covers ARMv8 through ARMv9.2-A (e.g. Samsung Galaxy S24 series).
            // The universal APK and the dedicated arm64-v8a split both target this ABI.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    signingConfigs {
        // SEC-001: credentials are never literals in version control. They are read from
        // Gradle properties (CI secrets, ~/.gradle/gradle.properties) or the environment.
        // A missing value is an error only for builds that actually need to sign; debug and
        // the local unit-test gate must keep working without a keystore.
        val nightlyStorePassword = signingSecret("nightlyStorePassword")
        val nightlyKeyPassword = signingSecret("nightlyKeyPassword")
        val nightlyKeyAlias = signingSecret("nightlyKeyAlias") ?: "nightlykey"
        val nightlyStoreFile = signingSecret("nightlyStoreFile") ?: "nightly.keystore"

        create("nightly") {
            storeFile = file(nightlyStoreFile)
            keyAlias = nightlyKeyAlias
            if (gradle.startParameter.taskNames.any { it.contains("Nightly", ignoreCase = true) }) {
                storePassword = requireNotNull(nightlyStorePassword) {
                    "nightly signing requested but 'nightlyStorePassword' is unset. Supply it via " +
                        "~/.gradle/gradle.properties or the EPHYRA_NIGHTLY_STORE_PASSWORD env var."
                }
                keyPassword = requireNotNull(nightlyKeyPassword) {
                    "nightly signing requested but 'nightlyKeyPassword' is unset. Supply it via " +
                        "~/.gradle/gradle.properties or the EPHYRA_NIGHTLY_KEY_PASSWORD env var."
                }
            }
        }
    }

    buildTypes {
        val debug = getByName("debug") {
            applicationIdSuffix = ".dev"
            isPseudoLocalesEnabled = true
        }
        val release = getByName("release") {
            isMinifyEnabled = Config.enableCodeShrink
            isShrinkResources = Config.enableCodeShrink

            proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")
        }

        val commonMatchingFallbacks = listOf(release.name)

        create("nightly") {
            initWith(release)
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("nightly")

            applicationIdSuffix = ".nightly"

            matchingFallbacks.addAll(commonMatchingFallbacks)
        }

        create("preview") {
            initWith(release)

            applicationIdSuffix = ".debug"

            signingConfig = debug.signingConfig

            matchingFallbacks.addAll(commonMatchingFallbacks)
        }
    }

    sourceSets {
        getByName("preview").res.directories.add(file("src/debug/res").toString())
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols += listOf(
                "libandroidx.graphics.path",
                "libarchive-jni",
                "librjxlcoder",
                "libsqlite3x",
            )
                .map { "**/$it.so" }
        }
        resources {
            excludes += setOf(
                "kotlin-tooling-metadata.json",
                "LICENSE.txt",
                "META-INF/**/*.properties",
                "META-INF/**/LICENSE.txt",
                "META-INF/*.properties",
                "META-INF/*.version",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/README.md",
            )
        }
    }

    dependenciesInfo {
        includeInApk = Config.includeDependencyInfo
        includeInBundle = Config.includeDependencyInfo
    }

    buildFeatures {
        buildConfig = true
        aidl = true

        // Disable some unused things
        dataBinding = false
        shaders = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2g"
                it.exclude("**/ShadowAnimatedVectorResources*")

                // One JVM per test class.
                //
                // `ShadowAnimatedVectorResources` shadows the whole of `android.content.res.Resources`
                // so that Compose's animated vector drawables load under Robolectric, which throws on
                // an `<animated-vector>` root. Shadowing a framework class this broadly is global
                // state: Robolectric installs the shadow per sandbox, and instrumented call sites
                // cast the extracted shadow back to the declaring class. When a `Resources` instance is
                // shadowed without ours — which happens once another Robolectric class in the same JVM
                // has run first — that cast fails:
                //
                //   Cannot cast org.robolectric.shadows.ShadowResources to ShadowAnimatedVectorResources
                //     at android.content.res.Resources.loadXmlResourceParser
                //     at android.content.res.Resources.getAnimation
                //     at android.view.animation.AnimationUtils.loadInterpolator
                //     at com.android.internal.policy.DecorView.<init>
                //
                // That path is reached by any test that launches a real Activity (DecorView's
                // constructor loads the platform window animation), which is why it surfaced in
                // `HomeScreenUiTest` and `DeepLinkActivityTest` and nowhere else. It was also
                // intermittent: `:app:testDebugUnitTest` alone passed while the same task failed when
                // run with other modules, because that changes what shares the JVM.
                //
                // A fresh JVM per class is the fix that actually holds, rather than the
                // `exclude` above, which only keeps the shadow out of test *discovery* and does
                // nothing about shadow installation order. The cost is JVM startup per class; it is
                // paid in CI rather than as a test that fails for reasons unrelated to what it tests.
                it.forkEvery = 1
            }
        }
    }
}

/*
 * Repository-level gates read files that are not compiled inputs, so Gradle does not know the
 * test task depends on them. Without this the task is `UP-TO-DATE` after such a file changes and
 * the gate silently does not run — which is the same failure as a gate that cannot fail, and is
 * worse because the build still reports success.
 *
 * `BaselineProfileRuleTest` hit this on `app/src/main/baseline-prof.txt` and
 * `WorkflowRedundancyTest` on `.github/workflows/`. Declaring the inputs here is the structural
 * fix; `--rerun-tasks` only papers over it.
 */
val repositoryFileGates = listOf(
    rootProject.file(".github/workflows"),
    rootProject.file(".github/actions"),
)

tasks.withType<Test>().configureEach {
    repositoryFileGates.forEach { gateDir ->
        if (gateDir.exists()) {
            inputs.dir(gateDir)
                .withPropertyName("repositoryGate:" + gateDir.name)
                .withPathSensitivity(PathSensitivity.RELATIVE)
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=coil3.annotation.ExperimentalCoilApi",
            "-opt-in=kotlinx.coroutines.DelicateCoroutinesApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-Xannotation-default-target=param-property",
        )
    }
}

dependencies {
    implementation(projects.core.archive)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.download)
    implementation(projects.coreMetadata)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.core.domain)
    implementation(projects.presentationCore)
    implementation(projects.presentationWidget)

    // Feature modules
    implementation(projects.feature.browse)
    implementation(projects.feature.category)
    implementation(projects.feature.download)
    implementation(projects.feature.history)
    implementation(projects.feature.library)
    implementation(projects.feature.manga)
    implementation(projects.feature.migration)
    implementation(projects.feature.more)
    implementation(projects.feature.reader)
    implementation(projects.feature.player)
    implementation(projects.feature.security)
    implementation(projects.feature.settings)
    implementation(projects.feature.stats)
    implementation(projects.feature.upcoming)
    implementation(projects.feature.updates)
    implementation(projects.feature.webview)
    implementation(projects.telemetry)

    // Compose
    implementation(compose.activity)
    implementation(compose.foundation)
    implementation(compose.material3.core)
    implementation(compose.material.icons)
    implementation(compose.animation)
    implementation(compose.animation.graphics)
    debugImplementation(compose.ui.tooling)
    implementation(compose.ui.tooling.preview)
    implementation(compose.ui.util)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    implementation(androidx.paging.runtime)
    implementation(androidx.paging.compose)

    implementation(libs.bundles.sqlite)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    implementation(kotlinx.reflect)
    implementation(kotlinx.immutables)

    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)

    // AndroidX libraries
    implementation(androidx.annotation)
    implementation(androidx.appcompat)
    implementation(androidx.biometricktx)
    implementation(androidx.corektx)
    implementation(androidx.splashscreen)
    implementation(androidx.profileinstaller)

    implementation(androidx.bundles.lifecycle)

    // Job scheduling
    implementation(androidx.workmanager)

    // Networking
    implementation(libs.bundles.okhttp)
    implementation(libs.okio)

    // Data serialization (JSON, protobuf, xml)
    implementation(kotlinx.bundles.serialization)

    // HTML parser
    implementation(libs.jsoup)

    // Disk
    implementation(libs.unifile)

    // Preferences
    implementation(libs.preferencektx)

    // Image loading
    implementation(platform(libs.coil.bom))
    implementation(libs.bundles.coil)

    // UI libraries
    implementation(libs.material)

    implementation(libs.aboutLibraries.compose)
    implementation(libs.compose.materialmotion)
    implementation(libs.swipe)
    implementation(libs.compose.webview)
    implementation(libs.compose.grid)
    implementation(libs.reorderable)
    implementation(libs.bundles.markdown)
    implementation(libs.materialKolor)

    // Logging
    implementation(libs.logcat)

    // String similarity
    implementation(libs.stringSimilarity)

    // Tests
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(androidx.work.testing)

    testImplementation(kotlinx.coroutines.test)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:6.1.3")
    testImplementation(compose.ui.test.junit4)
    debugImplementation(compose.ui.test.manifest)
    debugImplementation(libs.leakcanary)

    // Connected instrumentation
    androidTestImplementation(androidx.test.ext)
    androidTestImplementation(androidx.test.core)
    androidTestImplementation(androidx.test.runner)

    // Hilt dependencies
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
}

androidComponents {
    onVariants { variant ->
        // Build metadata and per-build-type version-name suffixes resolve LAZILY: the git
        // commands (CommandValueSource) run when GenerateBuildConfig consumes the providers
        // at execution time, not on every configuration. The eager form shelled out to git
        // during configuration on every build and forced the configuration cache to
        // re-validate those ValueSources on every commit.
        val commitCount = commitCountProvider()
        val gitSha = gitShaProvider()
        val buildTime = buildTimeProvider(useLastCommitTime = true)

        // AGP 9 API notes (the signatures changed versus AGP 8, which is why the first push
        // did not compile): `variant.buildConfigFields` is a nullable property, and
        // `BuildConfigField`'s `value` parameter is a plain `Serializable`, not a `Provider`.
        // The lazy form is therefore a Provider OF a BuildConfigField, registered through
        // `MapProperty.put(key, Provider)`: Gradle wires the provider as a task input, so the
        // git commands and the clock still run only when GenerateBuildConfig executes.
        val buildConfigFields = variant.buildConfigFields
            ?: error("variant.buildConfigFields is null; buildFeatures.buildConfig must be enabled")
        buildConfigFields.put(
            "COMMIT_COUNT",
            commitCount.map { BuildConfigField<Serializable>("String", "\$it\"", null) },
        )
        buildConfigFields.put(
            "COMMIT_SHA",
            gitSha.map { BuildConfigField<Serializable>("String", "\$it\"", null) },
        )
        buildConfigFields.put(
            "BUILD_TIME",
            buildTime.map { BuildConfigField<Serializable>("String", "\$it\"", null) },
        )

        // Same values the removed DSL `versionNameSuffix` lines produced; release keeps the
        // bare version name.
        when (variant.buildType) {
            "debug", "preview" -> variant.outputs.forEach { output ->
                output.versionName.set(commitCount.map { count -> "$defaultVersionName-$count" })
            }
            "nightly" -> variant.outputs.forEach { output ->
                output.versionName.set(gitSha.map { sha -> "$defaultVersionName-nightly-$sha" })
            }
        }
    }

    onVariants(selector().withFlavor("default" to "standard")) {
        // Only excluding in standard flavor because this breaks
        // Layout Inspector's Compose tree
        it.packaging.resources.excludes.add("META-INF/*.version")
    }
    onVariants(selector().withBuildType("nightly")) { variant ->
        // Use the versionCode Gradle property (set to GITHUB_RUN_NUMBER in CI) so that
        // each nightly build gets a unique, monotonically increasing version code that
        // allows in-place upgrades between successive nightly releases.
        val nightlyVersionCodeStr = project.findProperty("versionCode") as String?
        if (nightlyVersionCodeStr != null) {
            val nightlyVersionCode = nightlyVersionCodeStr.toIntOrNull()
                ?: error("Gradle property 'versionCode' must be a valid integer, got: '$nightlyVersionCodeStr'")
            variant.outputs.forEach { output ->
                output.versionCode.set(nightlyVersionCode)
            }
        }
    }
}

/**
 * SEC-001 support: read a signing secret from Gradle properties or the environment.
 *
 * Order is Gradle property first, then `EPHYRA_`-prefixed environment variable, so CI can inject
 * either. Returns null when unset, which lets debug builds and the local unit-test gate run
 * without a keystore while still failing loudly for a real signing task.
 */
fun signingSecret(propertyName: String): String? {
    val fromProperty = providers.gradleProperty(propertyName).orNull
    if (!fromProperty.isNullOrBlank()) return fromProperty
    val envName = "EPHYRA_" + propertyName.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
    val fromEnv = providers.environmentVariable(envName).orNull
    return fromEnv?.takeIf { it.isNotBlank() }
}
