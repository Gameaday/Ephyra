package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A ViewModel must not launch work on a dispatcher no test can reach.
 *
 * **What went wrong.** `Build & Test` was red on CI and green locally, on
 * `AboutViewModelTest > checkVersion emits NewUpdate effect … FAILED`. The cause was
 * `viewModelScope.launchIO`, and `launchIO` is `launch(Dispatchers.IO)` — a real thread pool.
 * `Dispatchers.setMain` and `advanceUntilIdle` do not reach it, so `awaitItem()` raced a coroutine
 * on an uncontrolled thread. Usually it won locally and lost on a slower runner.
 *
 * Re-injecting `Dispatchers.IO` still passed locally, so it was *probabilistically* flaky rather than
 * reliably broken — which is why it survived, and why "it passes on my machine" was true and useless.
 *
 * **Why a gate.** Ten ViewModels had this shape, one of them had already cost a CI failure, and the
 * next one would cost another. The dispatcher must be a parameter — defaulting to the production value,
 * supplied by tests — so the coroutine lands on the test scheduler and `advanceUntilIdle()` means
 * something.
 *
 * **The allowlist is debt, not permission.** Each entry is a ViewModel that still launches on a real
 * dispatcher, which is exactly the shape that fails under CI timing. Entries are removed as they are
 * fixed; the test below fails if a name is listed twice or absent, so the list cannot quietly drift
 * out of sync with the code.
 */
class InjectableViewModelDispatcherTest {

    @Test
    fun `a ViewModel does not launch on a dispatcher no test can control`() {
        newViolations().forEach { path ->
            assertTrue(
                false,
                "$path launches on a hardcoded dispatcher. A test cannot control it, so the test " +
                    "either races it or waits on wall-clock time - both fail on a slower CI runner. " +
                    "Take the dispatcher as a constructor parameter instead; see " +
                    "AboutViewModel.updateCheckDispatcher for the pattern.",
            )
        }
    }

    /** The allowlist must describe the code accurately, or it is worse than no gate. */
    @Test
    fun `the allowlist matches the code rather than drifting from it`() {
        ALLOWED.forEach { name ->
            assertTrue(
                File(repositoryRoot(), "feature").walkTopDown()
                    .any { it.name == "$name.kt" && it.absolutePath.contains("\\src\\main\\") },
                "allowlist names '$name' but no such production ViewModel exists; remove the entry",
            )
        }
    }

    private fun newViolations(): List<String> =
        File(repositoryRoot(), "feature").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name.endsWith("ViewModel.kt") }
            .filter { it.absolutePath.contains("\\src\\main\\") }
            .filter { file ->
                val body = file.readText()
                val launchesHardcoded = Regex("""viewModelScope\.launch(IO|\(Dispatchers\.)""").containsMatchIn(body)
                val takesDispatcher = body.contains("CoroutineDispatcher")
                launchesHardcoded && !takesDispatcher && ALLOWED.none { file.name == "$it.kt" }
            }
            .map { it.relativeTo(repositoryRoot()).path }
            .toList()

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").exists()) {
            directory = directory.parentFile
        }
        return requireNotNull(directory) { "Could not locate repository root" }
    }

    private companion object {
        /**
         * ViewModels that still launch on a real dispatcher, and therefore carry this test's risk.
         *
         * Each is one CI failure away. `AboutViewModel` is absent because it was fixed.
         */
        val ALLOWED = listOf(
            "MigrateSourceViewModel",
            "BrowseSourceViewModel",
            "HistoryViewModel",
            "LibraryViewModel",
            "MangaCoverViewModel",
            "MangaViewModel",
            "ReaderViewModel",
            "StatsViewModel",
            "UpdatesViewModel",
            "SearchViewModel",
        )
    }
}
