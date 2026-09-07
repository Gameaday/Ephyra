package ephyra.domain.source.interactor

import app.cash.turbine.test
import ephyra.domain.source.model.Pin
import ephyra.domain.source.model.Pins
import ephyra.domain.source.model.Source
import ephyra.domain.source.repository.SourceRepository
import ephyra.domain.source.service.SourcePreferences
import ephyra.testutil.FakePreferenceStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetEnabledSourcesTest {

    private val repository: SourceRepository = mockk()
    private val preferenceStore = FakePreferenceStore()
    private val preferences = SourcePreferences(preferenceStore)
    private val getEnabledSources = GetEnabledSources(repository, preferences)

    private val englishSource = Source(
        id = 1L,
        lang = "en",
        name = "MangaDex (EN)",
        supportsLatest = true,
        isStub = false,
    )

    private val spanishSource = Source(
        id = 2L,
        lang = "es",
        name = "MangaDex (ES)",
        supportsLatest = true,
        isStub = false,
    )

    private val japaneseSource = Source(
        id = 3L,
        lang = "ja",
        name = "MangaDex (JA)",
        supportsLatest = true,
        isStub = false,
    )

    private val localSource = Source(
        id = 0L,
        lang = "",
        name = "Local source",
        supportsLatest = false,
        isStub = false,
    )

    @BeforeEach
    fun setUp() {
        preferences.enabledLanguages().set(setOf("en"))
        preferences.disabledSources().set(emptySet())
        preferences.pinnedSources().set(emptySet())
        preferences.lastUsedSource().set(-1L)

        every { repository.getSources() } returns flowOf(
            listOf(englishSource, spanishSource, japaneseSource, localSource),
        )
    }

    @Test
    fun `filters out non-enabled language sources when only en is enabled`() = runTest {
        getEnabledSources.subscribe().test {
            val list = awaitItem()
            // Should contain englishSource and localSource, but NOT spanishSource or japaneseSource
            val sourceIds = list.map { it.id }
            assertEquals(listOf(localSource.id, englishSource.id), sourceIds)
        }
    }

    @Test
    fun `includes sources matching newly enabled languages`() = runTest {
        preferences.enabledLanguages().set(setOf("en", "ja"))

        getEnabledSources.subscribe().test {
            val list = awaitItem()
            val sourceIds = list.map { it.id }
            assertTrue(sourceIds.contains(englishSource.id))
            assertTrue(sourceIds.contains(japaneseSource.id))
            assertTrue(sourceIds.contains(localSource.id))
            assertTrue(!sourceIds.contains(spanishSource.id))
        }
    }

    @Test
    fun `filters out disabled sources even if language is enabled`() = runTest {
        preferences.disabledSources().set(setOf("1"))

        getEnabledSources.subscribe().test {
            val list = awaitItem()
            val sourceIds = list.map { it.id }
            assertEquals(listOf(localSource.id), sourceIds)
        }
    }

    @Test
    fun `marks pinned sources correctly`() = runTest {
        preferences.pinnedSources().set(setOf("1"))

        getEnabledSources.subscribe().test {
            val list = awaitItem()
            val en = list.first { it.id == 1L }
            assertEquals(Pins.pinned, en.pin)
        }
    }

    @Test
    fun `duplicates last used source with isUsedLast flag`() = runTest {
        preferences.lastUsedSource().set(1L)

        getEnabledSources.subscribe().test {
            val list = awaitItem()
            val lastUsed = list.find { it.isUsedLast }
            assertEquals(1L, lastUsed?.id)
            assertTrue(list.any { it.id == 1L && !it.isUsedLast })
        }
    }
}
