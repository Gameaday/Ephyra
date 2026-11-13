package ephyra.app.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import org.junit.jupiter.api.Test

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
     */
    @Test
    fun `feature modules must not depend on each other`() {
        val classes = ClassFileImporter().importPackages("ephyra.feature")

        val rule = noClasses()
            .that().resideInAPackage("ephyra.feature.library..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "ephyra.feature.browse..",
                "ephyra.feature.reader..",
                "ephyra.feature.manga..",
                "ephyra.feature.updates..",
                "ephyra.feature.history..",
                "ephyra.feature.settings..",
                "ephyra.feature.download..",
                "ephyra.feature.migration..",
                "ephyra.feature.stats..",
                "ephyra.feature.upcoming..",
                "ephyra.feature.webview..",
                "ephyra.feature.more..",
                "ephyra.feature.security..",
                "ephyra.feature.player..",
                "ephyra.feature.category..",
            )

        rule.check(classes)
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
}
