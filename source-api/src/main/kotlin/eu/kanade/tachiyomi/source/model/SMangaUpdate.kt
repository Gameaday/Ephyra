package eu.kanade.tachiyomi.source.model

import kotlinx.coroutines.runBlocking

/**
 * The result of an update: a manga and its chapters, each obtainable on demand.
 *
 * **Why both are suspending lambdas.** A source whose details and chapters come from *separate
 * endpoints* does not have to make the app wait for both before showing anything — it hands back two
 * suspending lambdas instead, and the consumer awaits each when it is ready. Upstream records this as
 * "`SMangaUpdate` constructors taking suspend lambdas".
 *
 * **Why that is a compatibility requirement, not a feature.** An extension compiled against a
 * `tachiyomix` that has these constructors calls them. With only `(SManga, List<SChapter>)` such an
 * extension cannot link, and the failure surfaces as the source failing to load rather than as a
 * missing method. This class mirrors upstream's shape exactly — primary constructor takes the two
 * lambdas, the eager form is a secondary constructor — because the *JVM signatures* are the contract.
 * Deviating here for tidiness would break exactly the extensions the shape exists to serve.
 *
 * **Reading is always a suspend.** There is deliberately no blocking accessor: awaiting the lambda is
 * what makes the deferral meaningful, and a non-suspending getter would have to either block a thread
 * or lie about having a value. Consumers that already hold both parts should await once and keep what
 * they need.
 *
 * @see <a href="https://github.com/mihonapp/tachiyomix">tachiyomix</a> for the upstream shape.
 */
@Suppress("Unused")
class SMangaUpdate(
    val manga: suspend () -> SManga,
    val chapters: suspend () -> List<SChapter>,
) {
    /** Both parts already fetched. */
    constructor(manga: SManga, chapters: List<SChapter>) : this({ manga }, { chapters })

    /** Details fetched, chapters deferred to their own endpoint. */
    constructor(manga: SManga, chapters: suspend () -> List<SChapter>) : this({ manga }, chapters)

    /**
     * The blocking accessor older extensions call, hidden from Kotlin but present in the ABI.
     *
     * **Why this must exist.** Upstream keeps `getMangaLegacy()` under
     * `DeprecationLevel.HIDDEN` with `@JvmName("getManga")`. HIDDEN removes it from *Kotlin source*
     * while leaving the method in the compiled API — which is the whole point: an extension compiled
     * against that version emits a call to `SMangaUpdate.getManga()` returning `SManga`, and the
     * runtime must still satisfy it.
     *
     * When the primary constructor changed from `SManga` to `suspend () -> SManga`, that getter's
     * return type changed with it, so an already-compiled extension's `invoke-virtual getManga()`
     * no longer resolved and every source failed with `NoSuchMethodError: No virtual method
     * getManga(...)` the moment it tried to update chapter lists.
     *
     * **Why this blocks rather than throwing.** Upstream's body throws `"Stub!"`, because upstream
     * expects every caller to have migrated to awaiting the property. An already-compiled extension
     * cannot migrate retroactively, so throwing here would turn a signature mismatch into a crash on
     * the same path. Blocking is wrong in principle and right in practice: it runs on the caller's
     * own thread and is bounded by whatever network call the lambda makes.
     */
    @Deprecated("Use the suspend API instead", level = DeprecationLevel.HIDDEN)
    @JvmName("getManga")
    fun getMangaLegacy(): SManga = blockingAwait(manga)

    /** The blocking counterpart of [chapters], for the same ABI reason as [getMangaLegacy]. */
    @Deprecated("Use the suspend API instead", level = DeprecationLevel.HIDDEN)
    @JvmName("getChapters")
    fun getChaptersLegacy(): List<SChapter> = blockingAwait(chapters)

    private fun <T> blockingAwait(block: suspend () -> T): T = runBlocking { block() }
}
