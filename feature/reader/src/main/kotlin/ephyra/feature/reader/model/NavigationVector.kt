package ephyra.feature.reader.model

/**
 * The direction in which the reader moved between pages or chapters, as observed by the
 * reading-completion dispatcher.
 *
 * Completion side-effects (marking a chapter read, tracker sync, delete-after-read) are
 * only valid for [FORWARD] movement onto a chapter's final page. Moving [BACKWARD] across
 * the top/left boundary — for example swiping back from page 0 of Chapter *N* into
 * Chapter *N-1* — must never mutate read state: the user is re-visiting, not finishing.
 */
enum class NavigationVector { FORWARD, BACKWARD }
