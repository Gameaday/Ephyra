package ephyra.feature.browse.source.globalsearch

import ephyra.source.api.ContentReference
import ephyra.source.api.SourceCapability
import ephyra.source.api.SourceContentItem

/** User actions that can be invoked on a target search result. */
enum class TargetSearchAction {
    OPEN_DETAILS,
    PERSIST,
    ADD_TO_LIBRARY,
}

/** A validated, explicit action that still requires the owning feature to perform the work. */
data class TargetSearchCommand(
    val item: SourceContentItem,
    val action: TargetSearchAction,
    val reference: ContentReference,
)

sealed interface TargetSearchCommandResult {
    data class Ready(val command: TargetSearchCommand) : TargetSearchCommandResult
    data class Unsupported(val capability: SourceCapability) : TargetSearchCommandResult
}

object TargetSearchCommandFactory {
    fun create(
        item: SourceContentItem,
        action: TargetSearchAction,
        capabilities: Set<SourceCapability>,
    ): TargetSearchCommandResult {
        val required = when (action) {
            TargetSearchAction.OPEN_DETAILS,
            TargetSearchAction.PERSIST,
            TargetSearchAction.ADD_TO_LIBRARY,
            -> SourceCapability.DETAILS
        }
        if (required !in capabilities) return TargetSearchCommandResult.Unsupported(required)
        return TargetSearchCommandResult.Ready(
            TargetSearchCommand(
                item = item,
                action = action,
                reference = ContentReference(url = item.url, externalId = item.externalId),
            ),
        )
    }
}
