package ephyra.data.manga

import ephyra.data.room.daos.ExcludedScanlatorDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ExcludedScanlatorRepositoryImplTest {

    private val dao = mockk<ExcludedScanlatorDao>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val repo = ExcludedScanlatorRepositoryImpl(dao, testDispatcher)

    @Test
    fun `getExcludedScanlators returns set of scanlators`() = runTest(testDispatcher) {
        coEvery { dao.getExcludedScanlators(10L) } returns listOf("Group A", "Group B")

        val result = repo.getExcludedScanlators(10L)

        assertEquals(setOf("Group A", "Group B"), result)
        coVerify(exactly = 1) { dao.getExcludedScanlators(10L) }
    }

    @Test
    fun `setExcludedScanlators delegates directly to dao transaction method`() = runTest(testDispatcher) {
        val scanlators = setOf("Group A", "Group C")

        repo.setExcludedScanlators(10L, scanlators)

        coVerify(exactly = 1) { dao.setExcludedScanlators(10L, scanlators) }
    }

    @Test
    fun `subscribeExcludedScanlators emits set from dao flow`() = runTest(testDispatcher) {
        coEvery { dao.getExcludedScanlatorsAsFlow(10L) } returns flowOf(listOf("Group X", "Group Y"))

        val result = repo.subscribeExcludedScanlators(10L).first()

        assertEquals(setOf("Group X", "Group Y"), result)
    }
}
