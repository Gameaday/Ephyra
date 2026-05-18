package ephyra.feature.common.utils

import dev.icerock.moko.resources.StringResource
import ephyra.domain.migration.models.MigrationFlag
import ephyra.i18n.MR

fun MigrationFlag.getLabel(): StringResource {
    return when (this) {
        MigrationFlag.CHAPTER -> ephyra.i18n.R.string.chapters
        MigrationFlag.CATEGORY -> ephyra.i18n.R.string.categories
        MigrationFlag.CUSTOM_COVER -> ephyra.i18n.R.string.custom_cover
        MigrationFlag.NOTES -> ephyra.i18n.R.string.action_notes
        MigrationFlag.REMOVE_DOWNLOAD -> ephyra.i18n.R.string.delete_downloaded
    }
}
