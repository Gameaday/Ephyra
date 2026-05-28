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
}
