package ephyra.feature.browse.source.globalsearch

import ephyra.domain.content.model.ContentType
import ephyra.source.api.ContentReference
import ephyra.source.api.SourceCapability
import ephyra.source.api.SourceContentItem
import ephyra.source.api.SourceId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TargetSearchCommandTest {
    private val item = SourceContentItem(
        sourceId = SourceId("source-a"),
        externalId = "ext-1",
        url = "/series/1",
        title = "Series",
        contentType = ContentType.BOOK,
    )

    @Test
    fun `details command preserves source identity as a reference`() {
        val result = TargetSearchCommandFactory.create(
            item = item,
            action = TargetSearchAction.OPEN_DETAILS,
            capabilities = setOf(SourceCapability.DETAILS),
        )

        val ready = (result as TargetSearchCommandResult.Ready).command
        assertEquals(ContentReference("/series/1", "ext-1"), ready.reference)
        assertEquals(SourceId("source-a"), ready.item.sourceId)
    }

    @Test
    fun `unsupported action is not converted into a no-op`() {
        val result = TargetSearchCommandFactory.create(
            item = item,
            action = TargetSearchAction.ADD_TO_LIBRARY,
            capabilities = setOf(SourceCapability.SEARCH),
        )

        assertEquals(
            TargetSearchCommandResult.Unsupported(SourceCapability.DETAILS),
            result,
        )
    }
}
