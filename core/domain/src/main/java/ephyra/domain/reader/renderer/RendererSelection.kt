package ephyra.domain.reader.renderer

/**
 * What kind of content an entry holds, independent of how it will be drawn.
 *
 * This exists because the project intends to carry four kinds in one library -- paged image
 * (manga), continuous image (webtoon), text document (light novel / EPUB) and video (anime) --
 * and today the only axis that exists is [ephyra.domain.reader.model.ReadingMode], which is a
 * *presentation* preference chosen by the user and persisted per title. That is the wrong axis to
 * hang content dispatch on: it lets a document that must be read as text be opened in an image
 * viewer because a stale preference says so.
 *
 * Deliberately separate from `ReadingMode`, and never merged with it. A mode is a user choice
 * within a renderer; a kind is a property of the content. Conflating them is how a reader ends
 * up with a `when` over both in the same place.
 */
enum class ContentKind {
    /** Discrete images, one or two at a time. Manga, and the existing pager. */
    PAGED_IMAGE,

    /** One tall image read by scrolling. Webtoon and long strips. */
    CONTINUOUS_IMAGE,

    /** Reflowing text. Light novels and EPUB. Selection, search and font scaling are required. */
    TEXT_DOCUMENT,

    /** Playback. Already isolated behind `feature/player`. */
    VIDEO,
}

/**
 * The rendering capability a [ContentKind] needs.
 *
 * These are requirements, not implementations. A renderer declares what it can do; the policy
 * matches a kind to a renderer that satisfies it. That ordering matters: putting the requirement
 * first means a renderer can be swapped without re-deriving what it had to provide.
 */
enum class RendererCapability {
    /** Draws discrete pages and can show two at once on a wide viewport. */
    PAGED_IMAGE,

    /** Draws one tall surface with a scrollable viewport, slice-decoded under a texture ceiling. */
    CONTINUOUS_IMAGE,

    /** Lays out real text: selection, copy, search, font scaling and a screen reader. */
    REFLOWABLE_TEXT,

    /** Decodes and plays a media stream with transport controls. */
    VIDEO_PLAYBACK,
}

/** What a renderer offers, and which kinds it can therefore serve. */
data class RendererOffer(
    val id: String,
    val capabilities: Set<RendererCapability>,
) {
    init {
        require(id.isNotBlank()) { "a renderer must have an id" }
    }

    /** True when this renderer satisfies [required] outright. */
    fun satisfies(required: RendererCapability): Boolean = required in capabilities
}

/**
 * Chooses a renderer for a content kind, or refuses.
 *
 * # Why this refuses rather than approximates
 *
 * The obvious wrong design is one "flexible document renderer" that handles images and text
 * together. Image slicing and text reflow share almost nothing: layout is fixed pixels versus
 * re-broken lines, and text drawn to a canvas has no selection, no search, no font scaling and no
 * screen reader. A single abstraction over both produces the worst of both and is the single
 * biggest architectural risk in the four-kind plan.
 *
 * So the policy is a narrow match, not an adapter. When no renderer satisfies the requirement it
 * returns [RendererSelection.Unsupported] and names what was missing. Callers are expected to show
 * that rather than fall back to a renderer that would silently degrade the content.
 *
 * # What this deliberately does not do
 *
 * It does not choose *between* two capable renderers, does not read preferences, and does not
 * know about Android. Those belong to a later layer; putting them here would make the dispatch
 * untestable for the same reason the reader's 1439-line ViewModel is hard to reason about.
 */
object RendererDispatch {

    /** The requirement a kind places on a renderer. */
    fun requirementFor(kind: ContentKind): RendererCapability = when (kind) {
        ContentKind.PAGED_IMAGE -> RendererCapability.PAGED_IMAGE
        ContentKind.CONTINUOUS_IMAGE -> RendererCapability.CONTINUOUS_IMAGE
        ContentKind.TEXT_DOCUMENT -> RendererCapability.REFLOWABLE_TEXT
        ContentKind.VIDEO -> RendererCapability.VIDEO_PLAYBACK
    }

    /**
     * Picks the first offer in [offers] that satisfies [kind]'s requirement.
     *
     * @param offers candidates in preference order. Order is the caller's, not this policy's.
     */
    fun select(kind: ContentKind, offers: List<RendererOffer>): RendererSelection {
        if (offers.isEmpty()) {
            return RendererSelection.Unsupported(kind, emptySet(), reason = NO_RENDERERS)
        }
        val required = requirementFor(kind)
        return offers.firstOrNull { it.satisfies(required) }
            ?.let { RendererSelection.Selected(kind, it.id) }
            ?: RendererSelection.Unsupported(kind, offers.map { it.id }.toSet())
    }

    private const val NO_RENDERERS = "no renderers were offered"
}

/** Outcome of a [RendererDispatch.select] call. */
sealed interface RendererSelection {

    val kind: ContentKind

    /** [rendererId] satisfies [kind]. */
    data class Selected(
        override val kind: ContentKind,
        val rendererId: String,
    ) : RendererSelection

    /**
     * Nothing can render [kind].
     *
     * [considered] is the set of renderer ids that were offered, and [reason] explains the empty
     * case. This is a result, not an error: the caller shows "this content type is not supported
     * yet" rather than opening a reader that would mangle it.
     */
    data class Unsupported(
        override val kind: ContentKind,
        val considered: Set<String>,
        val reason: String = "no offer satisfies ${RendererDispatch.requirementFor(kind)}",
    ) : RendererSelection
}
