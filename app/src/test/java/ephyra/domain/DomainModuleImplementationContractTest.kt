package ephyra.domain

import ephyra.data.category.CategoryRepositoryImpl
import ephyra.data.chapter.ChapterRepositoryImpl
import ephyra.data.history.HistoryRepositoryImpl
import ephyra.data.manga.ExcludedScanlatorRepositoryImpl
import ephyra.data.manga.MangaRepositoryImpl
import ephyra.data.release.ReleaseServiceImpl
import ephyra.data.repository.ExtensionRepoRepositoryImpl
import ephyra.data.source.SourceRepositoryImpl
import ephyra.data.source.StubSourceRepositoryImpl
import ephyra.data.track.TrackRepositoryImpl
import ephyra.data.updates.UpdatesRepositoryImpl
import ephyra.domain.category.repository.CategoryRepository
import ephyra.domain.chapter.repository.ChapterRepository
import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository
import ephyra.domain.history.repository.HistoryRepository
import ephyra.domain.manga.repository.ExcludedScanlatorRepository
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.release.service.ReleaseService
import ephyra.domain.source.repository.SourceRepository
import ephyra.domain.source.repository.StubSourceRepository
import ephyra.domain.track.repository.TrackRepository
import ephyra.domain.updates.repository.UpdatesRepository
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Structural contract tests for all interface→implementation bindings declared in
 * [koinDomainModule].
 *
 * Each test uses [Class.isAssignableFrom] — a pure JVM reflection check that requires no
 * Android runtime and no Koin context. The assertions will fail at CI time if:
 * - A repository implementation class is refactored away from its interface
 * - An interface is renamed or moved without updating the concrete class declaration
 * - A class is accidentally made abstract or its `implements` clause is removed
 *
 * These checks complement [ephyra.app.di.KoinModuleInterfaceBindingTest] which tests the
 * app-layer bindings. Together they give full pre-compilation coverage of the Koin graph.
 *
 * Tests run concurrently because they are stateless reflection checks.
 */
@Execution(ExecutionMode.CONCURRENT)
class DomainModuleImplementationContractTest {

    // ── CategoryRepository ────────────────────────────────────────────────────

    /**
     * [koinDomainModule] binds [CategoryRepositoryImpl] as [CategoryRepository].
     * Verifies the data implementation still satisfies the domain interface.
     */
    @Test
    fun `CategoryRepositoryImpl implements CategoryRepository`() {
        assertTrue(CategoryRepository::class.java.isAssignableFrom(CategoryRepositoryImpl::class.java)) {
            "ephyra.data.category.CategoryRepositoryImpl must implement ephyra.domain.category.repository.CategoryRepository"
        }
    }

    // ── MangaRepository ───────────────────────────────────────────────────────

    /**
     * [koinDomainModule] binds [MangaRepositoryImpl] as [MangaRepository].
     */
    @Test
    fun `MangaRepositoryImpl implements MangaRepository`() {
        assertTrue(MangaRepository::class.java.isAssignableFrom(MangaRepositoryImpl::class.java)) {
            "ephyra.data.manga.MangaRepositoryImpl must implement ephyra.domain.manga.repository.MangaRepository"
        }
    }

    // ── ChapterRepository ─────────────────────────────────────────────────────

    /**
     * [koinDomainModule] binds [ChapterRepositoryImpl] as [ChapterRepository].
     */
    @Test
    fun `ChapterRepositoryImpl implements ChapterRepository`() {
        assertTrue(ChapterRepository::class.java.isAssignableFrom(ChapterRepositoryImpl::class.java)) {
            "ephyra.data.chapter.ChapterRepositoryImpl must implement ephyra.domain.chapter.repository.ChapterRepository"
        }
    }

    // ── ExcludedScanlatorRepository ───────────────────────────────────────────

    /**
     * [koinDomainModule] binds [ExcludedScanlatorRepositoryImpl] as [ExcludedScanlatorRepository].
     * Verifies the data implementation still satisfies the domain interface so that any
     * screen or interactor that injects [ExcludedScanlatorRepository] does not crash with
     * [org.koin.core.error.NoBeanDefFoundException] at runtime.
     */
    @Test
    fun `ExcludedScanlatorRepositoryImpl implements ExcludedScanlatorRepository`() {
        assertTrue(
            ExcludedScanlatorRepository::class.java.isAssignableFrom(
                ExcludedScanlatorRepositoryImpl::class.java,
            ),
        ) {
            "ephyra.data.manga.ExcludedScanlatorRepositoryImpl must implement " +
                "ephyra.domain.manga.repository.ExcludedScanlatorRepository"
        }
    }

    // ── TrackRepository ───────────────────────────────────────────────────────

    /**
     * [koinDomainModule] binds [TrackRepositoryImpl] as [TrackRepository].
     */
    @Test
    fun `TrackRepositoryImpl implements TrackRepository`() {
        assertTrue(TrackRepository::class.java.isAssignableFrom(TrackRepositoryImpl::class.java)) {
            "ephyra.data.track.TrackRepositoryImpl must implement ephyra.domain.track.repository.TrackRepository"
        }
    }

    // ── HistoryRepository ─────────────────────────────────────────────────────

    /**
     * [koinDomainModule] binds [HistoryRepositoryImpl] as [HistoryRepository].
     */
    @Test
    fun `HistoryRepositoryImpl implements HistoryRepository`() {
        assertTrue(HistoryRepository::class.java.isAssignableFrom(HistoryRepositoryImpl::class.java)) {
            "ephyra.data.history.HistoryRepositoryImpl must implement ephyra.domain.history.repository.HistoryRepository"
        }
    }

    // ── UpdatesRepository ─────────────────────────────────────────────────────

    /**
     * [koinDomainModule] binds [UpdatesRepositoryImpl] as [UpdatesRepository].
     */
    @Test
    fun `UpdatesRepositoryImpl implements UpdatesRepository`() {
        assertTrue(UpdatesRepository::class.java.isAssignableFrom(UpdatesRepositoryImpl::class.java)) {
            "ephyra.data.updates.UpdatesRepositoryImpl must implement ephyra.domain.updates.repository.UpdatesRepository"
        }
    }

    // ── SourceRepository ─────────────────────────────────────────────────────

    /**
     * [koinDomainModule] binds [SourceRepositoryImpl] as [SourceRepository].
     */
    @Test
    fun `SourceRepositoryImpl implements SourceRepository`() {
        assertTrue(SourceRepository::class.java.isAssignableFrom(SourceRepositoryImpl::class.java)) {
            "ephyra.data.source.SourceRepositoryImpl must implement ephyra.domain.source.repository.SourceRepository"
        }
    }

    // ── StubSourceRepository ─────────────────────────────────────────────────

    /**
     * [koinDomainModule] binds [StubSourceRepositoryImpl] as [StubSourceRepository].
     */
    @Test
    fun `StubSourceRepositoryImpl implements StubSourceRepository`() {
        assertTrue(StubSourceRepository::class.java.isAssignableFrom(StubSourceRepositoryImpl::class.java)) {
            "ephyra.data.source.StubSourceRepositoryImpl must implement ephyra.domain.source.repository.StubSourceRepository"
        }
    }

    // ── ReleaseService ────────────────────────────────────────────────────────

    /**
     * [koinDomainModule] binds [ReleaseServiceImpl] as [ReleaseService].
     * Verifies the data implementation still satisfies the domain service interface.
     */
    @Test
    fun `ReleaseServiceImpl implements ReleaseService`() {
        assertTrue(ReleaseService::class.java.isAssignableFrom(ReleaseServiceImpl::class.java)) {
            "ephyra.data.release.ReleaseServiceImpl must implement ephyra.domain.release.service.ReleaseService"
        }
    }

    // ── ExtensionRepoRepository ───────────────────────────────────────────────

    /**
     * [koinDomainModule] binds [ExtensionRepoRepositoryImpl] as [ExtensionRepoRepository].
     */
    @Test
    fun `ExtensionRepoRepositoryImpl implements ExtensionRepoRepository`() {
        assertTrue(ExtensionRepoRepository::class.java.isAssignableFrom(ExtensionRepoRepositoryImpl::class.java)) {
            "ephyra.data.repository.ExtensionRepoRepositoryImpl must implement ephyra.domain.extensionrepo.repository.ExtensionRepoRepository"
        }
    }
}
