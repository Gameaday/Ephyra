package ephyra.feature.common.utils

import ephyra.domain.migration.models.MigrationFlag

fun MigrationFlag.getLabel(): Int {
    return when (this) {
        MigrationFlag.CHAPTER -> ephyra.app.core.common.R.string.chapters
        MigrationFlag.CATEGORY -> ephyra.app.core.common.R.string.categories
        MigrationFlag.CUSTOM_COVER -> ephyra.app.core.common.R.string.custom_cover
        MigrationFlag.NOTES -> ephyra.app.core.common.R.string.action_notes
        MigrationFlag.REMOVE_DOWNLOAD -> ephyra.app.core.common.R.string.delete_downloaded
    }
}
