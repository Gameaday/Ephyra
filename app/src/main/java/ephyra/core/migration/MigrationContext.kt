package ephyra.core.migration

import ephyra.core.common.di.CoreContainer

class MigrationContext(val dryrun: Boolean) {

    inline fun <reified T : Any> get(): T? {
        return try {
            CoreContainer.get<T>()
        } catch (e: Exception) {
            null
        }
    }
}
