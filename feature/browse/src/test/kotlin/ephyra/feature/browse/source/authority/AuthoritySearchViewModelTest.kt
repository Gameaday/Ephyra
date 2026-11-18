package ephyra.feature.browse.source.authority

import ephyra.domain.chapter.interactor.GenerateAuthorityChapters
import ephyra.domain.manga.interactor.FindContentSource
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetFavoritesByCanonicalId
import ephyra.domain.manga.interactor.GetMangaByUrlAndSourceId
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.model.TrackSearch
import ephyra.domain.track.service.Tracker
import ephyra.domain.track.service.TrackerManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Behavioural cover for [AuthoritySearchViewModel].
 *
 * **Why this class exists.** The ViewModel was made injectable-dispatcher-clean (see
 * `InjectableViewModelDispatcherTest`) but had no test, so that refactor was compile-verified only.
 * These tests cover the parts that decide what the user sees: which trackers are offered, how a
 * search resolves, and the three outcomes of "add to library" (added, already present, merge).
 *
 * Like its sibling `MatchResultsViewModelTest`, these drive a [StandardTestDispatcher] through
 * `advanceUntilIdle()`. That is the point of the injectable dispatcher: a test that passes only
 * because `UnconfinedTestDispatcher` runs eagerly would keep passing if the code used
 * `Dispatchers.IO`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthoritySearchViewModelTest {

    private val trackerManager: TrackerManager = mockk(relaxed = true)
    private val getFavoritesByCanonicalId: GetFavoritesByCanonicalId = mockk(relaxed = true)
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = mockk(relaxed = true)
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId = mockk(relaxed = true)
    private val networkToLocalManga: NetworkToLocalManga = mockk(relaxed = true)
    private val updateManga: UpdateManga = mockk(relaxed = true)
    private val insertTrack: InsertTrack = mockk(relaxed = true)
    private val generateAuthorityChapters: GenerateAuthorityChapters = mockk(relaxed = true)
    private val findContentSource: FindContentSource = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    /**
     * Tracker ids used as fixtures.
     *
     * These mirror `AddTracks`' private `MYANIMELIST_ID` (1) and `MANGAUPDATES_ID` (7). They are
     * duplicated rather than referenced because those constants are `private` in a companion object;
     * the mapping is asserted by `init offers only trackers that can actually serve a canonical id`,
     * which fails if the ids ever drift apart.
     */
    private val myAnimeList = 1L
    private val mangaUpdates = 7L

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Default: no duplicate and no existing canonical entry, so "add" takes the create path
        // unless a test overrides it.
        coEvery { getFavoritesByCanonicalId.await(any(), any()) } returns emptyList()
        coEvery { getDuplicateLibraryManga.invoke(any<String>()) } returns emptyList()
        coEvery { getMangaByUrlAndSourceId.await(any(), any()) } returns null
        coEvery { networkToLocalManga.invoke(any<List<Manga>>()) } answers { firstArg<List<Manga>>() }
        coEvery {
            findContentSource.findSources(any(), any(), any(), any())
        } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun tracker(
        id: Long,
        loggedIn: Boolean = false,
        search: suspend () -> List<TrackSearch> = { emptyList() },
    ): Tracker {
        val t: Tracker = mockk(relaxed = true)
        every { t.id } returns id
        every { t.name } returns "Tracker $id"
        // `Tracker.isLoggedIn()` is suspend, so it needs coEvery -- `every` would not intercept it.
        coEvery { t.isLoggedIn() } returns loggedIn
        coEvery { t.search(any()) } coAnswers { search() }
        return t
    }

    private fun result(
        remoteId: Long,
        title: String = "Result $remoteId",
        publishingType: String = "",
    ) = TrackSearch(
        remote_id = remoteId,
        title = title,
        publishing_type = publishingType,
    )

    private fun manga(id: Long, title: String = "Manga $id"): Manga =
        Manga.create().copy(id = id, title = title, favorite = true)

    private fun withTrackers(vararg trackers: Tracker) {
        coEvery { trackerManager.getAll(any()) } returns trackers.toList()
    }

    private fun createViewModel(): AuthoritySearchViewModel =
        AuthoritySearchViewModel(
            trackerManager = trackerManager,
            getFavoritesByCanonicalId = getFavoritesByCanonicalId,
            getDuplicateLibraryManga = getDuplicateLibraryManga,
            getMangaByUrlAndSourceId = getMangaByUrlAndSourceId,
            networkToLocalManga = networkToLocalManga,
            updateManga = updateManga,
            insertTrack = insertTrack,
            generateAuthorityChapters = generateAuthorityChapters,
            findContentSource = findContentSource,
            ioDispatcher = testDispatcher,
        )

    @Test
    fun `init offers only trackers that can actually serve a canonical id`() = runTest(testDispatcher) {
        val al = tracker(TrackerManager.ANILIST, loggedIn = false)
        val mal = tracker(myAnimeList, loggedIn = false)
        val mu = tracker(mangaUpdates, loggedIn = false)
        val unknown = tracker(999L, loggedIn = true)
        withTrackers(al, mal, mu, unknown)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val offered = viewModel.state.value.availableTrackers.map { it.id }
        // AniList has a canonical prefix but is not in TRACKERS_WITH_PUBLIC_SEARCH and is not
        // logged in, so it must be excluded -- offering it would produce a dead search.
        assertTrue(al.id !in offered, "a logged-out tracker with no public search must be excluded")
        assertTrue(mal.id !in offered, "MyAnimeList is not logged in and has no public search")
        assertTrue(mu.id in offered, "MangaUpdates supports unauthenticated search and must be kept")
        assertTrue(
            unknown.id !in offered,
            "a tracker with no canonical prefix cannot produce an id, so it must be excluded",
        )
    }

    @Test
    fun `init selects the first offered tracker`() = runTest(testDispatcher) {
        val first = tracker(mangaUpdates)
        val second = tracker(TrackerManager.ANILIST, loggedIn = true)
        withTrackers(first, second)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            mangaUpdates,
            viewModel.state.value.selectedTracker?.id,
            "a default selection is required; the search screen has nothing to search with otherwise",
        )
    }

    @Test
    fun `search stores results and clears the searching flag`() = runTest(testDispatcher) {
        val tracker = tracker(TrackerManager.ANILIST, loggedIn = true) {
            listOf(result(1, "Alpha"), result(2, "Beta"))
        }
        withTrackers(tracker)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AuthoritySearchScreenEvent.Search("alpha"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isSearching, "the searching flag must clear once results arrive")
        assertNull(state.searchError)
        assertEquals(listOf("Alpha", "Beta"), state.results.map { it.title })
    }

    @Test
    fun `search records the error message when the tracker fails`() = runTest(testDispatcher) {
        val tracker = tracker(TrackerManager.ANILIST, loggedIn = true) {
            throw RuntimeException("rate limited")
        }
        withTrackers(tracker)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AuthoritySearchScreenEvent.Search("alpha"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isSearching, "a failed search must not leave the spinner up")
        assertEquals("rate limited", state.searchError)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun `searching again replaces the previous results rather than appending`() = runTest(testDispatcher) {
        var query = ""
        val tracker = tracker(TrackerManager.ANILIST, loggedIn = true) {
            if (query == "first") listOf(result(1, "One")) else listOf(result(2, "Two"))
        }
        withTrackers(tracker)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AuthoritySearchScreenEvent.Search("first"))
        advanceUntilIdle()
        query = "second"
        viewModel.onEvent(AuthoritySearchScreenEvent.Search("second"))
        advanceUntilIdle()

        assertEquals(
            listOf("Two"),
            viewModel.state.value.results.map { it.title },
            "stale results from the previous query must be cleared, not merged",
        )
    }

    @Test
    fun `addToLibrary creates a new authority entry when nothing matches`() = runTest(testDispatcher) {
        val tracker = tracker(mangaUpdates, loggedIn = false)
        withTrackers(tracker)
        // Defaults from setUp: no canonical entry, no duplicate, insert echoes the input.

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AuthoritySearchScreenEvent.AddToLibrary(result(42, "Fresh")))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(
            setOf("mu:42"),
            state.addedCanonicalIds,
            "a successful add must record the canonical id so the UI can show it as added",
        )
        assertTrue(
            state.addingCanonicalIds.isEmpty(),
            "the in-flight marker must be cleared once the add finishes",
        )
    }

    @Test
    fun `addToLibrary records the canonical id without inserting when it already exists`() =
        runTest(testDispatcher) {
            val tracker = tracker(mangaUpdates, loggedIn = false)
            withTrackers(tracker)
            coEvery { getFavoritesByCanonicalId.await("mu:42", any()) } returns listOf(manga(1, "Already"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AuthoritySearchScreenEvent.AddToLibrary(result(42, "Already")))
            advanceUntilIdle()

            val state = viewModel.state.value
            // The early-return path marks it added (so the UI greys the button) but must NOT insert:
            // a second row for the same series is exactly the duplicate this guard prevents.
            assertEquals(setOf("mu:42"), state.addedCanonicalIds)
            assertTrue(state.addingCanonicalIds.isEmpty())
            coVerify(exactly = 0) { networkToLocalManga.invoke(any<List<Manga>>()) }
            assertNull(state.mergePrompt, "an existing canonical entry is not a merge candidate")
        }

    @Test
    fun `addToLibrary raises a merge prompt when unpaired duplicates exist`() = runTest(testDispatcher) {
        val tracker = tracker(mangaUpdates, loggedIn = false)
        withTrackers(tracker)
        val candidate = manga(7, "Same Series")
        coEvery { getDuplicateLibraryManga.invoke("Same Series") } returns
            listOf(MangaWithChapterCount(candidate, 0))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(AuthoritySearchScreenEvent.AddToLibrary(result(42, "Same Series")))
        advanceUntilIdle()

        val state = viewModel.state.value
        val prompt = state.mergePrompt
        assertTrue(prompt != null, "an unpaired duplicate must raise a merge prompt, not silently add")
        assertEquals("mu:42", prompt?.canonicalId)
        assertEquals(listOf(7L), prompt?.candidates?.map { it.manga.id })
        assertTrue(
            "mu:42" !in state.addedCanonicalIds,
            "nothing is added while the user is still deciding whether to merge",
        )
        coVerify(exactly = 0) { networkToLocalManga.invoke(any<List<Manga>>()) }
    }

    @Test
    fun `addToLibrary ignores duplicates that are already linked to a canonical id`() =
        runTest(testDispatcher) {
            val tracker = tracker(mangaUpdates, loggedIn = false)
            withTrackers(tracker)
            // A duplicate that already carries a canonical id is not a merge candidate: it is already
            // linked elsewhere, so prompting would offer to overwrite a real binding.
            val linked = manga(8, "Linked Elsewhere").copy(canonicalId = "al:8")
            coEvery { getDuplicateLibraryManga.invoke("Linked Elsewhere") } returns
                listOf(MangaWithChapterCount(linked, 0))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(AuthoritySearchScreenEvent.AddToLibrary(result(42, "Linked Elsewhere")))
            advanceUntilIdle()

            assertNull(
                viewModel.state.value.mergePrompt,
                "an already-linked duplicate must not raise a merge prompt",
            )
        }
}
