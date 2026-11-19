package ephyra.app.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import ephyra.app.security.TrackedFileNames
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ArchitectureTest {

    /**
     * Domain Layer Purity Rule:
     * The core domain layer (business logic and interactors) must remain completely pure
     * and must not depend on the database implementation (data), launchers (app),
     * or presentation details (presentation/features).
     */
    @Test
    fun `domain layer must not depend on data, app, or presentation layers`() {
        val classes = ClassFileImporter().importPackages("ephyra.core.domain")

        val rule = noClasses()
            .that().resideInAPackage("ephyra.core.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "ephyra.core.data..",
                "ephyra.data..",
                "ephyra.app..",
                "ephyra.presentation..",
                "ephyra.feature..",
            )

        rule.check(classes)
    }

    /**
     * ViewModels Hilt Compliance Rule:
     * Every concrete, instantiable ViewModel implementation in the codebase must
     * be annotated with @HiltViewModel to guarantee compile-time dependency injection and
     * prevent runtime lookup/instantiation crashes.
     */
    @Test
    fun `viewmodels must be annotated with HiltViewModel`() {
        val classes = ClassFileImporter().importPackages(
            "ephyra.app",
            "ephyra.feature",
        )

        val rule = classes()
            .that().areAssignableTo("androidx.lifecycle.ViewModel")
            .and().areNotInterfaces()
            .and().doNotHaveModifier(JavaModifier.ABSTRACT)
            .should().beAnnotatedWith("dagger.hilt.android.lifecycle.HiltViewModel")

        rule.check(classes)
    }

    /**
     * Feature Module Isolation Rule:
     * Feature modules must not directly depend on each other.
     * All cross-feature communication must go through core/domain interfaces.
     *
     * **This rule used to check exactly one feature, so it guarded nothing.** It read
     * `.that().resideInAPackage("ephyra.feature.library..")` and listed fifteen other features as
     * forbidden targets -- but `feature:library` has **zero** cross-feature imports, so the rule was
     * vacuously green while twelve declared edges elsewhere went unchecked. A gate whose source set
     * is empty is worse than no gate: it reports success for a property it never tested.
     *
     * It now checks **every** `ephyra.feature..` class as a potential source, and the sanctioned
     * exceptions live in [DECLARED_CROSS_FEATURE_PACKAGES] instead of being implicitly
     * `everything except library`. The list is the same twelve edges `ModuleDependencyGraphTest`
     * declares, so the two gates cannot disagree -- `the declared cross-feature edges match the
     * module build files` below fails if they drift apart.
     */
    @Test
    fun `feature modules must not depend on each other`() {
        val classes = ClassFileImporter().importPackages("ephyra.feature")

        // ArchUnit's package syntax cannot express "any feature, but only from these sources", so
        // the rule is asserted once per declared source: that pairing is permitted and every other
        // pairing is still forbidden. A feature with no declared edges gets the full set.
        for (source in ALL_FEATURE_PACKAGES) {
            val allowed = DECLARED_CROSS_FEATURE_PACKAGES[source].orEmpty()
            val forbidden = ALL_FEATURE_PACKAGES - source - allowed
            if (forbidden.isEmpty()) continue

            noClasses()
                .that().resideInAPackage("$source..")
                .should().dependOnClassesThat().resideInAnyPackage(
                    *forbidden.map { "$it.." }.toTypedArray(),
                )
                .check(classes)
        }
    }

    /**
     * Package-Root Rule:
     * Every source file under `feature/X` must declare `package ephyra.feature.X...`.
     *
     * **Why this gate exists.** `feature:manga` had 8 files declaring `ephyra.presentation.manga`
     * while living under `feature/manga/src/main/kotlin/ephyra/presentation/manga/` — and 19
     * others in the same tree correctly declaring `ephyra.feature.manga.presentation.components`.
     * Both compile, because Kotlin does not require the directory to match the package, so the drift
     * was invisible to every other gate: `feature modules must not depend on each other` checks
     * *edges*, not roots.
     *
     * The cost was not cosmetic. `ephyra.presentation.manga` reads as part of `presentation-core`,
     * so any package-grouped tooling (a UI audit, a dependency report, an IDE refactor) silently
     * misattributes those files — which is how a structural audit this cycle counted `feature:manga`
     * as having two identities.
     *
     * **Why a source check rather than ArchUnit.** ArchUnit reasons about compiled classes and their
     * packages; it cannot see that a *file* sits in the wrong directory. The defect is a
     * source-layout property, so the assertion has to read source.
     */
    @Test
    fun `every feature source file declares its own module's package root`() {
        val offenders = mutableListOf<String>()

        for (module in FEATURE_MODULES) {
            val moduleRoot = File(TrackedFileNames.repositoryRoot(), "feature/$module/src")
            if (!moduleRoot.isDirectory) continue

            moduleRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val declared = file.readText()
                        .lineSequence()
                        .firstOrNull { it.startsWith("package ") }
                        ?.removePrefix("package ")
                        ?.trim()
                    val expected = "ephyra.feature.$module"
                    if (declared != null && declared != expected && !declared.startsWith("$expected.")) {
                        offenders += "feature/$module: ${file.name} declares `$declared`, expected `$expected...`"
                    }
                }
        }

        assertTrue(
            offenders.isEmpty(),
            "These files sit in feature/<module> but declare a package outside it. Kotlin allows the " +
                "mismatch, so nothing else catches it, and package-grouped tooling misattributes the " +
                "file to whatever the declaration says:\n" +
                offenders.joinToString("\n") { "  $it" },
        )
    }

    /**
     * Domain Layer Android Framework Purity Rule:
     * The domain layer must be pure Kotlin with no Android framework dependencies.
     * This enforces compile-time determinism and testability.
     */
    @Test
    fun `domain layer must not depend on Android framework`() {
        val classes = ClassFileImporter().importPackages("ephyra.core.domain")

        val rule = noClasses()
            .that().resideInAPackage("ephyra.core.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "android..",
                "androidx.workmanager..",
                "androidx.compose..",
                "androidx.paging.runtime..",
            )

        rule.check(classes)
    }

    /**
     * CoreContainer Isolation Rule:
     * Internal code in features must not depend on CoreContainer.
     * CoreContainer is strictly a bridge for legacy extension compatibility.
     */
    @Test
    fun `internal feature code must not depend on CoreContainer directly`() {
        val classes = ClassFileImporter().importPackages("ephyra.feature")

        val rule = noClasses()
            .that().resideInAPackage("ephyra.feature..")
            .should().dependOnClassesThat().haveFullyQualifiedName("ephyra.core.common.di.CoreContainer")

        rule.check(classes)
    }

    /**
     * ViewModels Data Layer Isolation Rule:
     * ViewModels must not directly depend on data layer implementations.
     * All data access must be mediated through domain interactors / repositories.
     */
    @Test
    fun `viewmodels must not depend on data layer directly`() {
        val classes = ClassFileImporter().importPackages("ephyra.feature")

        val rule = noClasses()
            .that().areAssignableTo("androidx.lifecycle.ViewModel")
            .should().dependOnClassesThat().resideInAnyPackage(
                "ephyra.core.data..",
                "ephyra.data..",
            )

        rule.check(classes)
    }

    /**
     * Immutable UI State Rule:
     * ViewModels must only expose read-only state (StateFlow, SharedFlow),
     * never mutable types (MutableStateFlow, MutableSharedFlow) publicly.
     */
    @Test
    fun `viewmodels must not expose mutable state publicly`() {
        val classes = ClassFileImporter().importPackages(
            "ephyra.app",
            "ephyra.feature",
            "ephyra.presentation",
        )

        val methodsRule = noMethods()
            .that().areDeclaredInClassesThat().areAssignableTo("androidx.lifecycle.ViewModel")
            .and().haveModifier(JavaModifier.PUBLIC)
            .and().doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should().haveRawReturnType("kotlinx.coroutines.flow.MutableStateFlow")
            .orShould().haveRawReturnType("kotlinx.coroutines.flow.MutableSharedFlow")

        val fieldsRule = noFields()
            .that().areDeclaredInClassesThat().areAssignableTo("androidx.lifecycle.ViewModel")
            .and().haveModifier(JavaModifier.PUBLIC)
            .should().haveRawType("kotlinx.coroutines.flow.MutableStateFlow")
            .orShould().haveRawType("kotlinx.coroutines.flow.MutableSharedFlow")

        methodsRule.check(classes)
        fieldsRule.check(classes)
    }

    /**
     * Strict UDF Rule — No Cross-ViewModel Dependencies:
     * A ViewModel must never depend on another concrete ViewModel. Cross-ViewModel writes bypass
     * the owning ViewModel's single event entry-point and create hidden bidirectional state
     * coupling; shared state belongs in the domain layer, and cross-screen commands belong in
     * effects or navigation. Abstract shared base ViewModels (e.g. SearchViewModel) are exempt:
     * extending one is inheritance, not peer coupling.
     */
    @Test
    fun `viewmodels must not depend on other concrete viewmodels`() {
        val classes = ClassFileImporter().importPackages(
            "ephyra.app",
            "ephyra.feature",
            "ephyra.presentation",
        )

        val concreteViewModel: DescribedPredicate<JavaClass> = DescribedPredicate.describe(
            "a concrete ViewModel in app or feature code",
            java.util.function.Predicate<JavaClass> { candidate ->
                candidate.isAssignableTo("androidx.lifecycle.ViewModel") &&
                    !candidate.modifiers.contains(JavaModifier.ABSTRACT) &&
                    (
                        candidate.packageName.startsWith("ephyra.app.") ||
                            candidate.packageName.startsWith("ephyra.feature.")
                        )
            },
        )

        val rule = noClasses()
            .that().areAssignableTo("androidx.lifecycle.ViewModel")
            .should().dependOnClassesThat(concreteViewModel)

        rule.check(classes)
    }

    /**
     * Strict UDF Rule — No Compose UI Toolkit in ViewModels:
     * ViewModels must not depend on the Compose UI toolkit (animation, foundation, material,
     * material icons, material3, ui). A ViewModel may keep `androidx.compose.runtime`
     * annotations (`@Immutable`) — that is the state-definition vocabulary — and the pure
     * collection helpers in `androidx.compose.ui.util` (`fastAny`, `fastFilter`, `fastMap`, …)
     * are exempt too: they are allocation-lean `List` operations, not rendering vocabulary.
     * The moment a ViewModel imports grid, unit, or widget types it has grown rendering
     * concerns and the state layer is no longer UI-toolkit-agnostic. UI vocabulary
     * (`GridCells`, `Dp`, …) is mapped from plain data at the Compose call site.
     */
    @Test
    fun `viewmodels must not depend on the Compose UI toolkit`() {
        val classes = ClassFileImporter().importPackages(
            "ephyra.app",
            "ephyra.feature",
            "ephyra.presentation",
        )

        val composeToolkit: DescribedPredicate<JavaClass> = DescribedPredicate.describe(
            "a Compose UI toolkit class (androidx.compose.ui.util collection helpers exempt)",
            java.util.function.Predicate<JavaClass> { candidate ->
                val p = candidate.packageName
                p.startsWith("androidx.compose.animation.") ||
                    p.startsWith("androidx.compose.foundation.") ||
                    p.startsWith("androidx.compose.material.") ||
                    p.startsWith("androidx.compose.material3.") ||
                    (p.startsWith("androidx.compose.ui.") && !p.startsWith("androidx.compose.ui.util."))
            },
        )

        val rule = noClasses()
            .that().areAssignableTo("androidx.lifecycle.ViewModel")
            .should().dependOnClassesThat(composeToolkit)

        rule.check(classes)
    }

    /**
     * Strict UDF Rule — No Public Setters on ViewModels:
     * A public `set…` method on a ViewModel is a UI-writable mutation channel that bypasses
     * `onEvent` — including the compiler-generated setter of a public `var` property. All
     * mutations enter through the event entry-point; read-only exposure uses `val` or
     * `var … private set`.
     */
    @Test
    fun `viewmodels must not expose public setters`() {
        val classes = ClassFileImporter().importPackages(
            "ephyra.app",
            "ephyra.feature",
            "ephyra.presentation",
        )

        val rule = noMethods()
            .that().areDeclaredInClassesThat().areAssignableTo("androidx.lifecycle.ViewModel")
            .and().haveModifier(JavaModifier.PUBLIC)
            .and().doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should().haveNameMatching("^set[A-Z].*$")

        rule.check(classes)
    }

    /**
     * Strict UDF Rule — Effects Are Flows, Not Exposed Channels:
     * One-shot effects must be surfaced as a Flow (the UDF base already exposes
     * `receiveAsFlow()`); a publicly reachable `Channel` hands the UI a hot mutable handle it
     * can consume competitively or close, breaking single-consumer delivery.
     */
    @Test
    fun `viewmodels must not expose channels publicly`() {
        val classes = ClassFileImporter().importPackages(
            "ephyra.app",
            "ephyra.feature",
            "ephyra.presentation",
        )

        val methodsRule = noMethods()
            .that().areDeclaredInClassesThat().areAssignableTo("androidx.lifecycle.ViewModel")
            .and().haveModifier(JavaModifier.PUBLIC)
            .and().doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should().haveRawReturnType("kotlinx.coroutines.channels.Channel")

        val fieldsRule = noFields()
            .that().areDeclaredInClassesThat().areAssignableTo("androidx.lifecycle.ViewModel")
            .and().haveModifier(JavaModifier.PUBLIC)
            .should().haveRawType("kotlinx.coroutines.channels.Channel")

        methodsRule.check(classes)
        fieldsRule.check(classes)
    }

    private fun crossFeatureEdgesFromBuildFiles(): Set<Pair<String, String>> {
        // The test working directory is the module (`app/`), not the repository root, so walk up
        // until the settings file is found. `generateSequence(...).first { }` returns a nullable
        // `File?` and poisons type inference for the whole file, so this is written as a plain loop.
        var root = File("").absoluteFile
        while (!File(root, "settings.gradle.kts").exists() && root.parentFile != null) {
            root = root.parentFile!!
        }
        val edges = mutableSetOf<Pair<String, String>>()
        val featureDir = File(root, "feature")
        if (!featureDir.isDirectory) return edges
        featureDir.listFiles()?.forEach { module ->
            val buildFile = File(module, "build.gradle.kts")
            if (!buildFile.isFile) return@forEach
            val source = "ephyra.feature.${module.name}"
            PROJECT_EDGE.findAll(buildFile.readText()).forEach { match ->
                // The accessor is dotted (`projects.feature.manga`), so a Gradle coordinate
                // `feature:manga` becomes the package `ephyra.feature.manga`.
                val coordinate = match.groupValues[1]
                if (coordinate.startsWith("feature.")) {
                    val leaf = coordinate.removePrefix("feature.")
                    if (!leaf.contains('.')) edges += source to "ephyra.feature.$leaf"
                }
            }
        }
        return edges
    }

    /**
     * The two declarations of "which features may import which" must agree.
     *
     * [DECLARED_CROSS_FEATURE_PACKAGES] restates `ModuleDependencyGraphTest.DECLARED_FEATURE_EDGES`
     * as packages so the isolation rule above can consume them. Two copies of one fact will drift,
     * so the drift is itself a failure: an edge added to the build files without a permission here
     * makes a legitimate import fail, and a permission left behind after an edge is removed is
     * invisible coupling.
     *
     * It reads the build files rather than the other test's constant because that constant is
     * `private`; widening its visibility to serve a test would invert the dependency. The graph of
     * record is `settings.gradle.kts` plus each module's build file, and this asserts against it.
     */
    @Test
    fun `the declared cross-feature edges match the module build files`() {
        val fromBuildFiles = crossFeatureEdgesFromBuildFiles()
        // `flatMap` over a Map iterates Map.Entry, so read key/value explicitly. Destructuring as
        // `(source, targets)` binds Entry.component1/component2, which is not what this needs.
        val fromRule = DECLARED_CROSS_FEATURE_PACKAGES
            .flatMap { entry -> entry.value.map { entry.key to it } }
            .toSet()

        assertEquals(
            fromBuildFiles,
            fromRule,
            "DECLARED_CROSS_FEATURE_PACKAGES must describe exactly the feature-to-feature project " +
                "edges the build files declare. Add or remove an edge in both places together: a " +
                "permission with no edge behind it is invisible coupling, and an edge with no " +
                "permission makes a legitimate import fail.",
        )
    }

    private companion object {
        val ALL_FEATURE_PACKAGES = listOf(
            "ephyra.feature.browse",
            "ephyra.feature.category",
            "ephyra.feature.download",
            "ephyra.feature.history",
            "ephyra.feature.library",
            "ephyra.feature.manga",
            "ephyra.feature.migration",
            "ephyra.feature.more",
            "ephyra.feature.player",
            "ephyra.feature.reader",
            "ephyra.feature.security",
            "ephyra.feature.settings",
            "ephyra.feature.stats",
            "ephyra.feature.upcoming",
            "ephyra.feature.updates",
            "ephyra.feature.webview",
        )

        /**
         * Module directory names, derived from [ALL_FEATURE_PACKAGES] so the two cannot drift.
         *
         * The package list is the authority because the cross-feature rule needs full package names;
         * this is that list with the `ephyra.feature.` prefix removed. Deriving rather than repeating
         * means adding a feature in one place keeps both gates correct.
         *
         * Declared *after* `ALL_FEATURE_PACKAGES` deliberately: Kotlin initialises companion
         * properties in declaration order, so referencing it from above is a compile error
         * ("must be initialized"), not a subtle bug.
         */
        val FEATURE_MODULES: List<String> =
            ALL_FEATURE_PACKAGES.map { it.removePrefix("ephyra.feature.") }

        val DECLARED_CROSS_FEATURE_PACKAGES: Map<String, Set<String>> = mapOf(
            "ephyra.feature.browse" to setOf("ephyra.feature.manga", "ephyra.feature.migration"),
            "ephyra.feature.history" to setOf(
                "ephyra.feature.manga",
                "ephyra.feature.migration",
                "ephyra.feature.reader",
            ),
            "ephyra.feature.manga" to setOf(
                "ephyra.feature.migration",
                "ephyra.feature.reader",
            ),
            "ephyra.feature.more" to setOf("ephyra.feature.manga", "ephyra.feature.settings"),
            "ephyra.feature.settings" to setOf("ephyra.feature.category"),
            "ephyra.feature.updates" to setOf("ephyra.feature.manga", "ephyra.feature.reader"),
        )

        val PROJECT_EDGE = Regex(
            """^\s*(?:implementation|api)\s*\(\s*projects\.((?:[a-zA-Z][a-zA-Z0-9_]*\.)*[a-zA-Z][a-zA-Z0-9_]*)\s*\)""",
            RegexOption.MULTILINE,
        )
    }
}
