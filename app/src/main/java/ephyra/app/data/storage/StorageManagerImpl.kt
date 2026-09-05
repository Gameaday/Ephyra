package ephyra.app.data.storage

import android.content.Context
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import ephyra.core.common.util.storage.DiskUtil
import ephyra.domain.storage.service.StorageManager
import ephyra.domain.storage.service.StoragePreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.SupervisorJob


class StorageManagerImpl(
    private val context: Context,
    storagePreferences: StoragePreferences,
    ioDispatcher: CoroutineDispatcher,
) : StorageManager {

    // SupervisorJob: a failure in one child (e.g. directory init) must not cancel
    // the preference observation. Singleton-scoped, lives for the process lifetime.
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private var baseDir: UniFile? = getBaseDir(storagePreferences.baseStorageDirectory().getSync())

    // Conflated: only the latest change signal matters (shareIn replays 1), so an
    // unbounded buffer is unnecessary — this prevents unbounded queue growth when
    // storage-change signals fire faster than consumers collect.
    private val _changes: Channel<Unit> = Channel(Channel.CONFLATED)
    override val changes = _changes.receiveAsFlow()
        .shareIn(scope, SharingStarted.Lazily, 1)

    init {
        // Ensure directories exist for the current storage location when StorageManager is created.
        baseDir?.let(::initializeDirectories)

        storagePreferences.baseStorageDirectory().changes()
            .drop(1)
            .distinctUntilChanged()
            .onEach { uri ->
                baseDir = getBaseDir(uri)
                baseDir?.let(::initializeDirectories)
                _changes.send(Unit)
            }
            .launchIn(scope)
    }

    private fun getBaseDir(uri: String): UniFile? {
        if (uri.isEmpty()) return null
        return UniFile.fromUri(context, uri.toUri())
            .takeIf { it?.exists() == true }
    }

    private fun initializeDirectories(parent: UniFile) {
        parent.createDirectory(StorageManager.AUTOMATIC_BACKUPS_PATH)
        parent.createDirectory(StorageManager.LOCAL_SOURCE_PATH)
        parent.createDirectory(StorageManager.DOWNLOADS_PATH).also {
            DiskUtil.createNoMediaFile(it, context)
        }
    }

    override fun getAutomaticBackupsDirectory(): UniFile? {
        return baseDir?.createDirectory(StorageManager.AUTOMATIC_BACKUPS_PATH)
    }

    override fun getDownloadsDirectory(): UniFile? {
        return baseDir?.createDirectory(StorageManager.DOWNLOADS_PATH)
    }

    override fun getLocalSourceDirectory(): UniFile? {
        return baseDir?.createDirectory(StorageManager.LOCAL_SOURCE_PATH)
    }
}
