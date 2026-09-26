package ephyra.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ephyra.domain.storage.service.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-only trigger for [E4LabFixtureSeeder], driven over adb:
 *
 * ```
 * adb shell am broadcast -a ephyra.app.debug.SEED_E4LAB_FIXTURES \
 *     -n app.ephyra.dev/ephyra.app.debug.E4LabFixtureReceiver
 * ```
 *
 * Declared in `app/src/debug/AndroidManifest.xml`, so it exists in no shipping build. A receiver
 * is used rather than a startup hook because seeding is idempotent and should happen on demand,
 * only once the storage location has been configured — a hook fired from `MainActivity.onCreate`
 * would run before the user has ever chosen a storage directory and would log a failure every
 * cold start.
 *
 * `goAsync()` is used with an explicit scope rather than a blocking copy so a broadcast cannot be
 * killed mid-write; the fixtures are ~1.5 MB and a SAF tree is slow.
 */
class E4LabFixtureReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SeederEntryPoint {
        fun storageManager(): StorageManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        // A manifest-declared receiver cannot use field injection unless it extends a Hilt base
        // class, and adding one would tie this file to generated debug-only code. An @EntryPoint
        // is the supported way to pull a singleton into a plain component, so the receiver stays a
        // plain BroadcastReceiver and the debug source set stays self-contained.
        val storageManager = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SeederEntryPoint::class.java,
        ).storageManager()

        // goAsync() keeps the process alive for the copy; the fixtures are ~1.5 MB and a SAF tree
        // is slow, so finishing the broadcast immediately would truncate the write.
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                E4LabFixtureSeeder.seedNow(context.applicationContext, storageManager)
            } finally {
                pending.finish()
            }
        }
    }
}
