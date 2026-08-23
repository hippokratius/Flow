package io.github.aedev.flow.data.model

internal fun <T> Iterable<T>.distinctByNonBlankKey(
    keySelector: (T) -> String
): List<T> {
    val seenKeys = HashSet<String>()
    return filter { item ->
        val key = keySelector(item)
        key.isNotBlank() && seenKeys.add(key)
    }
}

internal fun <T> Iterable<T>.mergeDistinctByNonBlankKey(
    incoming: Iterable<T>,
    keySelector: (T) -> String
): List<T> = (this + incoming).distinctByNonBlankKey(keySelector)

/**
 * The list itself when every key is present and unique, a filtered copy otherwise.
 *
 * Scans before it builds. Duplicates are the rare case — this runs on every home-feed state
 * emission, and the old version allocated a full result list every time only to find nothing to
 * remove and throw the copy away.
 */
internal fun <T> List<T>.distinctByNonBlankKeyOrSelf(
    keySelector: (T) -> String
): List<T> {
    val seenKeys = HashSet<String>(size)
    for (item in this) {
        val key = keySelector(item)
        if (key.isBlank() || !seenKeys.add(key)) return distinctByNonBlankKey(keySelector)
    }
    return this
}

internal class DistinctKeyTracker {
    private val seenKeys = HashSet<String>()

    fun <T> filter(
        items: Iterable<T>,
        keySelector: (T) -> String
    ): List<T> = synchronized(seenKeys) {
        items.filter { item ->
            val key = keySelector(item)
            key.isNotBlank() && seenKeys.add(key)
        }
    }
}
