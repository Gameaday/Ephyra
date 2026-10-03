package eu.kanade.tachiyomi.source.model

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
}
