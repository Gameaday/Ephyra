package ephyra.app.di

import ephyra.app.data.backup.BackupNotifier
import ephyra.app.data.library.LibraryUpdateNotifier
import ephyra.app.data.notification.NotificationManagerImpl
import ephyra.app.data.updater.AppUpdateDownloaderImpl
import ephyra.app.data.updater.AppUpdateNotifier
import ephyra.app.ui.base.delegate.SecureActivityDelegateImpl
import ephyra.app.ui.base.delegate.ThemingDelegateImpl
import ephyra.core.common.notification.NotificationManager
import ephyra.core.download.DownloadManager as CoreDownloadManager
import ephyra.data.track.TrackerManagerImpl
import ephyra.domain.backup.service.BackupNotifier as DomainBackupNotifier
import ephyra.domain.download.service.DownloadManager as DomainDownloadManager
import ephyra.domain.library.service.LibraryUpdateNotifier as DomainLibraryUpdateNotifier
import ephyra.domain.release.service.AppUpdateDownloader
import ephyra.domain.release.service.AppUpdateNotifier as DomainAppUpdateNotifier
import ephyra.domain.track.service.TrackerManager
import ephyra.presentation.core.ui.delegate.SecureActivityDelegate as CoreSecureActivityDelegate
import ephyra.presentation.core.ui.delegate.ThemingDelegate as CoreThemingDelegate
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Structural contract tests for every remaining interface→implementation binding declared in
 * [koinAppModule] that was not covered by [KoinModuleInterfaceBindingTest].
 *
 * Each test uses [Class.isAssignableFrom] — a pure JVM reflection check that requires no
 * Android runtime and no Koin context. The assertions will fail at CI time if:
 * - An implementation class is refactored away from an interface without updating the module
 * - An interface is renamed or moved without updating the concrete class declaration
 * - A class is accidentally made abstract or its `implements` clause is removed
 *
 * Tests run concurrently because they are stateless reflection checks.
 */
@Execution(ExecutionMode.CONCURRENT)
class AppModuleImplementationContractTest {

    // ── NotificationManager ───────────────────────────────────────────────────

    /**
     * [koinAppModule] binds [NotificationManagerImpl] as [NotificationManager].
     * Verifies the concrete class still implements the core interface.
     */
    @Test
    fun `NotificationManagerImpl implements NotificationManager`() {
        assertTrue(NotificationManager::class.java.isAssignableFrom(NotificationManagerImpl::class.java)) {
            "NotificationManagerImpl must implement ephyra.core.common.notification.NotificationManager"
        }
    }

    // ── ThemingDelegate ───────────────────────────────────────────────────────

    /**
     * [koinAppModule] binds [ThemingDelegateImpl] as [CoreThemingDelegate].
     * Verifies the concrete delegate still implements the presentation-core interface.
     */
    @Test
    fun `ThemingDelegateImpl implements CoreThemingDelegate`() {
        assertTrue(CoreThemingDelegate::class.java.isAssignableFrom(ThemingDelegateImpl::class.java)) {
            "ThemingDelegateImpl must implement ephyra.presentation.core.ui.delegate.ThemingDelegate"
        }
    }

    // ── SecureActivityDelegate ────────────────────────────────────────────────

    /**
     * [koinAppModule] binds [SecureActivityDelegateImpl] as [CoreSecureActivityDelegate].
     * Verifies the concrete delegate still implements the presentation-core interface.
     */
    @Test
    fun `SecureActivityDelegateImpl implements CoreSecureActivityDelegate`() {
        assertTrue(CoreSecureActivityDelegate::class.java.isAssignableFrom(SecureActivityDelegateImpl::class.java)) {
            "SecureActivityDelegateImpl must implement ephyra.presentation.core.ui.delegate.SecureActivityDelegate"
        }
    }

    // ── AppUpdateDownloader ───────────────────────────────────────────────────

    /**
     * [koinAppModule] binds [AppUpdateDownloaderImpl] as [AppUpdateDownloader].
     * Verifies the concrete class still implements the domain interface.
     */
    @Test
    fun `AppUpdateDownloaderImpl implements AppUpdateDownloader`() {
        assertTrue(AppUpdateDownloader::class.java.isAssignableFrom(AppUpdateDownloaderImpl::class.java)) {
            "AppUpdateDownloaderImpl must implement ephyra.domain.release.service.AppUpdateDownloader"
        }
    }

    // ── BackupNotifier ────────────────────────────────────────────────────────

    /**
     * [koinAppModule] binds the app-level [BackupNotifier] as [DomainBackupNotifier].
     * Verifies the concrete class still implements the domain interface.
     */
    @Test
    fun `app BackupNotifier implements domain BackupNotifier`() {
        assertTrue(DomainBackupNotifier::class.java.isAssignableFrom(BackupNotifier::class.java)) {
            "ephyra.app.data.backup.BackupNotifier must implement ephyra.domain.backup.service.BackupNotifier"
        }
    }

    // ── AppUpdateNotifier ─────────────────────────────────────────────────────

    /**
     * [koinAppModule] binds the app-level [AppUpdateNotifier] as [DomainAppUpdateNotifier].
     * Verifies the concrete class still implements the domain interface.
     */
    @Test
    fun `app AppUpdateNotifier implements domain AppUpdateNotifier`() {
        assertTrue(DomainAppUpdateNotifier::class.java.isAssignableFrom(AppUpdateNotifier::class.java)) {
            "ephyra.app.data.updater.AppUpdateNotifier must implement ephyra.domain.release.service.AppUpdateNotifier"
        }
    }

    // ── LibraryUpdateNotifier ─────────────────────────────────────────────────

    /**
     * [koinAppModule] binds the app-level [LibraryUpdateNotifier] as [DomainLibraryUpdateNotifier].
     * Verifies the concrete class still implements the domain interface.
     */
    @Test
    fun `app LibraryUpdateNotifier implements domain LibraryUpdateNotifier`() {
        assertTrue(DomainLibraryUpdateNotifier::class.java.isAssignableFrom(LibraryUpdateNotifier::class.java)) {
            "ephyra.app.data.library.LibraryUpdateNotifier must implement ephyra.domain.library.service.LibraryUpdateNotifier"
        }
    }

    // ── DownloadManager ───────────────────────────────────────────────────────

    /**
     * [koinAppModule] binds [CoreDownloadManager] as the domain [DomainDownloadManager] interface.
     * Verifies the core implementation still satisfies the domain contract.
     */
    @Test
    fun `core DownloadManager implements domain DownloadManager`() {
        assertTrue(DomainDownloadManager::class.java.isAssignableFrom(CoreDownloadManager::class.java)) {
            "ephyra.core.download.DownloadManager must implement ephyra.domain.download.service.DownloadManager"
        }
    }

    // ── TrackerManager ────────────────────────────────────────────────────────

    /**
     * [koinAppModule] binds [TrackerManagerImpl] as [TrackerManager].
     * Verifies the data-layer implementation still satisfies the domain interface.
     */
    @Test
    fun `TrackerManagerImpl implements TrackerManager`() {
        assertTrue(TrackerManager::class.java.isAssignableFrom(TrackerManagerImpl::class.java)) {
            "ephyra.data.track.TrackerManagerImpl must implement ephyra.domain.track.service.TrackerManager"
        }
    }
}
