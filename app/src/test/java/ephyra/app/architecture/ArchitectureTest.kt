package ephyra.app.architecture

import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
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
                "androidx.workmanager..",
                "androidx.compose..",
                "androidx.paging.runtime..",
                "android.app..",
                "android.content..",
                "android.view..",
            )

        rule.check(classes)
    }
}
