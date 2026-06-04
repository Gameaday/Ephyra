package ephyra.core.common.util

fun <E> HashSet<E>.addOrRemove(value: E, shouldAdd: Boolean) {
    if (shouldAdd) {
        add(value)
    } else {
        remove(value)
    }
}

fun <E> MutableList<E>.addOrRemove(value: E, shouldAdd: Boolean) {
    if (shouldAdd) {
        add(value)
    } else {
        remove(value)
    }
}

fun <T : R, R : Any> List<T>.insertSeparators(
    generator: (before: T?, after: T?) -> R?,
): List<R> {
    if (isEmpty()) return emptyList()
    val newList = mutableListOf<R>()
    for (i in -1..lastIndex) {
        val before = getOrNull(i)
        before?.let(newList::add)
        val after = getOrNull(i + 1)
        val separator = generator.invoke(before, after)
        separator?.let(newList::add)
    }
    return newList
}

/**
 * Similar to [insertSeparators] but iterates from last to first element.
 */
fun <T : R, R : Any> List<T>.insertSeparatorsReversed(
    generator: (before: T?, after: T?) -> R?,
): List<R> {
    if (isEmpty()) return emptyList()
    val newList = mutableListOf<R>()
    for (i in size downTo 0) {
        val after = getOrNull(i)
        after?.let(newList::add)
        val before = getOrNull(i - 1)
        val separator = generator.invoke(before, after)
        separator?.let(newList::add)
    }
    return newList.asReversed()
}
