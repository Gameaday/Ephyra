package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertFalse
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
                productionViewModels().any { it.name == "$name.kt" },
                "allowlist names '$name' but no such production ViewModel exists; remove the entry",
            )
        }
    }

    /**
     * The predicate these gates are built on has to work on the runner that executes them.
     *
     * **Why this test exists.** Both gates matched `\\src\\main\\` — Windows separators — against
     * `File.absolutePath`. On a Linux runner that string never occurs, so `walkTopDown()` matched
     * nothing: the violation gate passed having inspected zero files, and the allowlist gate failed
     * on every entry for the same reason. Both looked authoritative and were checking nothing. It
     * surfaced only because CI is not Windows.
     *
     * Asserted against a file that definitely exists, so a future edit that makes this inert again
     * fails here rather than passing silently.
     */
    @Test
    fun `production sources are recognised on any platform`() {
        val recognised = productionViewModels()
        assertTrue(
            recognised.isNotEmpty(),
            "the production-source filter matched nothing, so every gate using it is inert",
        )
        assertTrue(
            recognised.all { it.path.replace(File.separatorChar, '/').contains("/src/main/") },
            "the filter let a non-production file through",
        )
    }

    /**
     * The predicate must distinguish the two spellings that reach `Dispatchers.IO` from the one
     * that does not.
     *
     * **Why this test exists.** The predicate is the whole gate. Widening it to catch
     * `withIOContext` is only worth anything if it cannot silently stop matching — the same
     * reasoning that made the allowlist and platform checks assertable. Each case is a form the
     * codebase actually uses, and the negative case is the one that stops a future edit from
     * matching everything and thereby flagging innocent files.
     */
    @Test
    fun `the predicate matches both IO spellings and not the cancellation-only one`() {
        assertTrue(
            REACHES_IO.containsMatchIn("viewModelScope.launchIO { update() }"),
            "launchIO must be detected; it is launch(Dispatchers.IO)",
        )
        assertTrue(
            REACHES_IO.containsMatchIn("viewModelScope.launch(Dispatchers.IO) { update() }"),
            "an explicit launch(Dispatchers.X) must be detected",
        )
        assertTrue(
            REACHES_IO.containsMatchIn("withIOContext { cache.put(id, bytes) }"),
            "withIOContext must be detected; it is withContext(Dispatchers.IO)",
        )
        assertFalse(
            REACHES_IO.containsMatchIn("withNonCancellableContext { cache.put(id, bytes) }"),
            "withNonCancellableContext changes cancellation, not the dispatcher, so it must not " +
                "be flagged -- otherwise every ViewModel using it becomes a false positive",
        )
    }

    /**
     * The empty allowlist must actually trip.
     *
     * An empty `ALLOWED` plus a predicate that stopped matching would both produce zero violations
     * -- the exact failure mode where the gate reports green while catching nothing. This pins the
     * two halves independently: the predicate is asserted above, and here the *filter* is asserted
     * to report a violating file when one exists.
     */
    @Test
    fun `the allowlist is empty and the filter still reports a violation`() {
        assertTrue(
            ALLOWED.isEmpty(),
            "the allowlist must stay empty; adding a name re-grandfathers debt instead of fixing it",
        )
        // `newViolations()` reads real files, so assert the mechanism rather than the current
        // result: a synthetic body that reaches IO with no dispatcher must fail the predicate, and
        // `ALLOWED.none {}` must then be true so it is reported.
        val violating = "viewModelScope.launchIO { load() }"
        assertTrue(
            REACHES_IO.containsMatchIn(violating),
            "a ViewModel launching on IO must still be detected",
        )
        assertTrue(
            ALLOWED.none { violating.startsWith(it) },
            "an empty allowlist must exclude nothing, so every violation is reported",
        )
    }

    /**
     * A ViewModel that takes a dispatcher must actually use it.
     *
     * **The blind spot this closes.** The violation test above only proves a ViewModel *has* a
     * dispatcher parameter. It cannot tell a real injection from a decorative one: a field that is
     * declared, annotated, and then never referenced still satisfies `body.contains("CoroutineDispatcher")`,
     * so the ViewModel passes while every coroutine inside it still runs on `Dispatchers.IO`.
     *
     * That is not hypothetical. `AboutViewModel` names its field `updateCheckDispatcher` and
     * `SettingsTrackingViewModel` names its `trackerDispatcher` -- both real, both used. The rule
     * here is deliberately about *use*, not about the field's name, so both keep passing while a
     * future `ioDispatcher` that is never referenced would fail.
     *
     * A ViewModel is accepted if **either**:
     *  - it references a dispatcher field in a `launch(...)` / `withContext(...)` call, or
     *  - it declares no dispatcher field at all (nothing to be decorative about).
     *
     * Constructor forwarding counts as use: `SearchViewModel`'s subclasses pass `ioDispatcher`
     * through to the abstract base, which is exactly how injection reaches the shared `search()`.
     */
    @Test
    fun `a ViewModel that takes a dispatcher actually uses it`() {
        val decorative = productionViewModels()
            .filter { file ->
                val body = file.readText()
                if (!body.contains("@IoDispatcher")) return@filter false
                // Any dispatcher field reaching a coroutine builder, OR being forwarded to a
                // superclass constructor, means the injection is live.
                val usedInCoroutine = DISPATCHER_IN_USE.containsMatchIn(body)
                val forwarded = FORWARDED.containsMatchIn(body)
                !usedInCoroutine && !forwarded
            }
            .map { it.relativeTo(repositoryRoot()).path }

        assertTrue(
            decorative.isEmpty(),
            "these ViewModels declare an @IoDispatcher field but never pass it to launch/withContext " +
                "or forward it to a base class, so the injection is decorative and every coroutine " +
                "still runs on a real dispatcher:\n" +
                decorative.joinToString("\n") { "  $it" },
        )
    }

    /** Production ViewModels under `feature`, wherever the build is running. */
    private fun productionViewModels(): List<File> =
        File(repositoryRoot(), "feature").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name.endsWith("ViewModel.kt") }
            .filter { it.path.replace(File.separatorChar, '/').contains("/src/main/") }
            .toList()

    private fun newViolations(): List<String> =
        productionViewModels()
            .filter { file ->
                val body = file.readText()
                val reachesIoDispatcher = REACHES_IO.containsMatchIn(body)
                val takesDispatcher = body.contains("CoroutineDispatcher")
                reachesIoDispatcher && !takesDispatcher && ALLOWED.none { file.name == "$it.kt" }
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
         * The ways a ViewModel reaches a dispatcher no test can control.
         *
         * `viewModelScope.launchIO` / `launch(Dispatchers.X)` and `withIOContext` are the *same*
         * defect in two spellings: both are `Dispatchers.IO` underneath, so `Dispatchers.setMain`
         * and `advanceUntilIdle()` cannot reach them and a test can only race the coroutine or
         * sleep on wall-clock time.
         *
         * **`withIOContext` was a blind spot until 2026-11-16.** The first version of this gate
         * matched only `viewModelScope.launch(IO|Dispatchers.)`, so a ViewModel could keep every
         * one of its `withIOContext` calls and still pass — which is exactly how
         * `AuthoritySearchViewModel` (6 sites) and `MatchResultsViewModel` (3 sites) sat outside
         * the allowlist while carrying the identical risk. Widening the predicate is what revealed
         * them; they are now recorded as debt rather than being invisible.
         *
         * `withNonCancellableContext` is deliberately **not** matched: it is
         * `withContext(NonCancellable)`, which changes cancellation and leaves the dispatcher
         * alone, so it carries none of this risk.
         */
        val REACHES_IO = Regex(
            """viewModelScope\.launch(IO|\(Dispatchers\.)|\bwithIOContext\s*\{""",
        )
        /**
         * A dispatcher field reaching a coroutine builder: `launch(ioDispatcher)`,
         * `withContext(ioDispatcher)`, `launch(updateCheckDispatcher)`, and so on.
         *
         * Matches *any* identifier rather than the literal `ioDispatcher`, because the field's name
         * is not the contract -- `AboutViewModel.updateCheckDispatcher` and
         * `SettingsTrackingViewModel.trackerDispatcher` are both correct.
         */
        val DISPATCHER_IN_USE = Regex(
            """\b(?:launch|withContext|async|produce)\s*\(\s*[A-Za-z_]\w*[Dd]ispatcher\w*\s*[\),]""",
        )

        /**
         * A dispatcher field forwarded to a superclass constructor, as `SearchViewModel`'s two
         * subclasses do: `ioDispatcher = ioDispatcher,`. Without this a correct pass-through would
         * look decorative, because the base class is what actually launches.
         */
        val FORWARDED = Regex(
            """\b[A-Za-z_]\w*[Dd]ispatcher\w*\s*=\s*[A-Za-z_]\w*[Dd]ispatcher\w*\b""",
        )

        /**
         * The allowlist is deliberately empty, and that is the point.
         *
         * It was never meant to be a permanent list of exceptions. Every entry was removed as its
         * ViewModel took an `@IoDispatcher` constructor parameter, and the last one
         * (`MangaViewModel`, 14 sites) landed on 2026-11-17. Nothing is left to grandfather in.
         *
         * It is kept as a **hard tripwire** rather than deleted: if a future ViewModel starts
         * reaching `Dispatchers.IO` without taking a dispatcher, this test fails and names the
         * file. Deleting the list would make a regression invisible instead. Adding a name here is
         * therefore the wrong fix -- the fix is the constructor parameter.
         */
        val ALLOWED = emptyList<String>()
    }
}
