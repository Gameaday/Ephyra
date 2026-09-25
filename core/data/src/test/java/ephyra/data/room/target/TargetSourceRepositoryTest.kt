package ephyra.data.room.target

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.content.model.ContentType
import ephyra.source.api.SourceCapability
import ephyra.source.api.SourceCompatibilityLevel
import ephyra.source.api.SourceDescriptor
import ephyra.source.api.SourceId
import ephyra.source.api.SourceInstallationState
import ephyra.source.api.SourceKind
import ephyra.source.api.SourceLifecycleTransition
import ephyra.source.api.SourceTrustLevel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TargetSourceRepositoryTest {
    private lateinit var database: TargetDatabase
    private lateinit var repository: TargetSourceRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TargetDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = TargetSourceRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `discover install disable update and uninstall preserve explicit lifecycle`() = runBlocking {
        assertApplied(repository.discover(descriptor(), 10L))
        assertEquals(SourceInstallationState.UNINSTALLED, repository.get(ID)!!.installationState)

        assertApplied(repository.install(descriptor(), 20L))
        assertApplied(repository.setEnabled(ID, enabled = false, atMillis = 30L))
        assertApplied(
            repository.discover(
                descriptor(
                    revision = 2,
                    name = "Updated",
                    capabilities = setOf(SourceCapability.SEARCH, SourceCapability.DETAILS),
                ),
                40L,
            ),
        )

        val updated = repository.get(ID)!!
        assertEquals(SourceInstallationState.INSTALLED, updated.installationState)
        assertFalse(updated.enabled)
        assertEquals("Updated", updated.descriptor.displayName)
        assertEquals(setOf(SourceCapability.SEARCH, SourceCapability.DETAILS), updated.descriptor.capabilities)
        assertEquals(10L, updated.firstSeenAtMillis)

        assertApplied(repository.uninstall(ID, 50L))
        val uninstalled = repository.get(ID)!!
        assertEquals(SourceInstallationState.UNINSTALLED, uninstalled.installationState)
        assertFalse(uninstalled.enabled)
    }

    @Test
    fun `stale descriptor cannot overwrite newer source metadata`() = runBlocking {
        assertApplied(repository.discover(descriptor(revision = 2), 10L))
        val rejected = repository.discover(descriptor(revision = 1, name = "Stale"), 20L)

        assertTrue(rejected is SourceLifecycleTransition.Rejected)
        assertEquals(2L, repository.get(ID)!!.descriptor.revision)
        assertEquals("Source", repository.get(ID)!!.descriptor.displayName)
    }

    @Test
    fun `uninstalled source cannot be enabled without reinstall`() = runBlocking {
        assertApplied(repository.discover(descriptor(), 10L))
        assertApplied(repository.install(descriptor(), 20L))
        assertApplied(repository.uninstall(ID, 30L))

        assertTrue(
            repository.setEnabled(ID, enabled = true, atMillis = 40L) is SourceLifecycleTransition.Rejected,
        )
        assertFalse(repository.get(ID)!!.enabled)
    }

    @Test
    fun `descriptor and content types round trip through Room`() = runBlocking {
        assertApplied(repository.discover(descriptor(), 10L))
        val record = repository.get(ID)!!

        assertEquals(ID, record.descriptor.id.value)
        assertEquals(SourceKind.REPOSITORY, record.descriptor.kind)
        assertEquals(SourceTrustLevel.VERIFIED, record.descriptor.trustLevel)
        assertEquals(SourceCompatibilityLevel.NATIVE, record.descriptor.compatibilityLevel)
        assertEquals(setOf(ContentType.MANGA, ContentType.NOVEL), record.descriptor.contentTypes)
        assertTrue(repository.getAll().single().descriptor.supports(SourceCapability.SEARCH))
    }

    private fun assertApplied(transition: SourceLifecycleTransition) {
        assertTrue(transition is SourceLifecycleTransition.Applied)
    }

    private fun descriptor(
        revision: Long = 1L,
        name: String = "Source",
        capabilities: Set<SourceCapability> = setOf(SourceCapability.SEARCH),
    ) = SourceDescriptor(
        id = SourceId(ID),
        displayName = name,
        kind = SourceKind.REPOSITORY,
        revision = revision,
        capabilities = capabilities,
        trustLevel = SourceTrustLevel.VERIFIED,
        compatibilityLevel = SourceCompatibilityLevel.NATIVE,
        contentTypes = setOf(ContentType.MANGA, ContentType.NOVEL),
    )

    private companion object {
        const val ID = "repository:test"
    }
}
