package ephyra.app.debug

import android.content.Context
import android.util.Log
import com.hippo.unifile.UniFile
import ephyra.domain.storage.service.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies the reader evidence fixtures out of the APK and into the app's own local-source directory.
 *
 * **Debug builds only.** This file lives in `app/src/debug`, so it is not compiled into `nightly`
 * or `release`; the fixtures under `app/src/debug/assets/ephyra-e4lab` are likewise absent from a
 * release APK. There is no runtime flag to disable and no `BuildConfig.DEBUG` check, because the
 * source set itself is the guarantee — a release build cannot contain this class even by mistake.
 *
 * It exists because `adb push` cannot seed this directory. Files pushed into
 * `Android/data/<pkg>/files` are owned by `shell`, and on API 37 the app cannot read a
 * `shell`-owned tree inside its own package directory, so the local source lists nothing. Writing
 * through [StorageManager] from inside the app produces correctly-owned files.
 *
 * This is the `B-021` unblock. It is device-setup tooling, not a product feature: it is never
 * reachable in a shipping build and must be deleted with the rest of the evidence scaffolding at
 * `CLEAN-001`.
 */
object E4LabFixtureSeeder {

    private const val TAG = "E4LabSeeder"

    /** Series and chapter names the local source will list. Deliberately obvious on screen. */
    const val SERIES_NAME = "E4-Lab Series"
    const val CHAPTER_NAME = "chapter-001"

    /** Asset directory holding the committed `ReaderFixtureCatalog` media. */
    private const val ASSET_DIR = "ephyra-e4lab"

    /**
     * The fixtures to seed, in page order. These are copies of the committed catalog media under
     * `feature/reader/src/test/resources/fixtures/reader`, so a device capture is against the same
     * bytes the `E2` suites use. A test asserts they stay in step.
     */
    private val PAGES = listOf(
        "tall-paged-image-v1.jpg",
        "static-long-webtoon-v1.jpg",
        "noisy-artwork-v1.jpg",
        "source-revision-change-v1.jpg",
        "static-short-v1.jpg",
        "uniform-bordered-page-v1.jpg",
    )

    /**
     * Copies the committed [PAGES] into `<local source>/E4-Lab Series/chapter-001/NNN.jpg` and logs
     * how many were written. Idempotent: a page that already exists with content is left alone, so
     * repeated broadcasts are harmless.
     *
     * Page order is deliberate — a tall paged image first so the reader opens on a realistic page,
     * then a long webtoon page, then noisy and bordered artwork so crop and scaling are visible.
     */
    suspend fun seedNow(context: Context, storageManager: StorageManager) = withContext(Dispatchers.IO) {
        // Nullable: the storage location is user-configurable and stays null until it is set, which
        // is the case on a fresh install before the storage step in onboarding.
        val localDir = storageManager.getLocalSourceDirectory()
        if (localDir == null) {
            Log.e(TAG, "No local source directory. Set the storage location in Settings first.")
            return@withContext
        }
        // `createDirectory` returns a nullable UniFile, so each level is checked rather than
        // chained: an elvis would silently yield null and the next call would dereference it.
        val seriesDir = localDir.findFile(SERIES_NAME)?.takeIf { it.isDirectory }
            ?: localDir.createDirectory(SERIES_NAME)
        if (seriesDir == null) {
            Log.e(TAG, "Could not create $SERIES_NAME")
            return@withContext
        }
        val chapterDir = seriesDir.findFile(CHAPTER_NAME)?.takeIf { it.isDirectory }
            ?: seriesDir.createDirectory(CHAPTER_NAME)
        if (chapterDir == null) {
            Log.e(TAG, "Could not create $CHAPTER_NAME")
            return@withContext
        }

        var written = 0
        PAGES.forEachIndexed { index, assetName ->
            val fileName = "%03d.jpg".format(index + 1)
            val existing = chapterDir.findFile(fileName)
            if (existing != null && existing.length() > 0) return@forEachIndexed
            if (copyAsset(context, "$ASSET_DIR/$assetName", chapterDir, fileName)) written++
        }
        Log.i(TAG, "Seeded $written page(s) into $SERIES_NAME/$CHAPTER_NAME")
    }

    /**
     * Copies one asset into [dir] under [targetName].
     *
     * A [UniFile] output stream is used rather than a raw [File] because the storage location is
     * user-configurable and may be a SAF tree that only exposes `UniFile` — writing with
     * [File] there would fail even though the directory is reachable.
     */
    private fun copyAsset(
        context: Context,
        assetPath: String,
        dir: UniFile,
        targetName: String,
    ): Boolean = try {
        val target = dir.createFile(targetName)
        if (target == null) {
            Log.e(TAG, "Could not create $targetName")
            false
        } else {
            context.assets.open(assetPath).use { input ->
                target.openOutputStream().use { output -> input.copyTo(output) }
            }
            true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to copy $assetPath", e)
        false
    }
}
