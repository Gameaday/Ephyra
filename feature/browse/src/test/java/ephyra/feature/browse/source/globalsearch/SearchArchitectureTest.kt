package ephyra.feature.browse.source.globalsearch

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * Architectural guardrails for the search feature, enforced at test time so
 * regressions fail the build instead of surfacing as runtime coupling.
 */
class SearchArchitectureTest {

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("ephyra.feature.browse.source.globalsearch")

    @Test
    fun `search logic must not depend on the data layer`() {
        // ViewModels and pure search logic must go through domain interactors only;
        // direct data-layer coupling would break testability and module boundaries.
        noClasses()
            .that().resideInAPackage("ephyra.feature.browse.source.globalsearch..")
            .should().dependOnClassesThat().resideInAPackage("ephyra.data..")
            .check(classes)
    }

    @Test
    fun `search merger must stay pure JVM`() {
        // The merger/cache are the testable core of search dedup; they must not
        // import Android framework classes so they keep running on plain JUnit.
        noClasses()
            .that().haveSimpleName("SearchResultMerger")
            .or().haveSimpleName("GlobalSearchCache")
            .should().dependOnClassesThat().resideInAPackage("android..")
            .check(classes)
    }
}
