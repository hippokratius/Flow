/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.data.source.link

import io.github.aedev.flow.data.model.Video

/*
 * Deciding whether a YouTube upload and a PeerTube upload are the same video.
 *
 * One module, used by all three things that need the answer: the shared channel page (so a mirrored
 * video is listed once), the player's source tab (so it knows there is another side), and opening a
 * video on PeerTube by default. Three separate heuristics would drift apart, and the drift would show
 * up as the app disagreeing with itself.
 *
 * Matched on normalised title plus duration, not on thumbnails. Comparing two thumbnails means
 * downloading both and perceptually hashing them — expensive on a phone and unreliable anyway,
 * because PeerTube generates its own preview on import rather than copying YouTube's.
 */

/** Duration difference still considered the same upload. Re-encodes shift a second or two. */
const val DEFAULT_DURATION_TOLERANCE_SECONDS = 5

/**
 * A title reduced to what two platforms can agree on.
 *
 * Case, punctuation and repeated spaces differ freely between an upload and its mirror, and bracketed
 * decorations like `[4K]` or `(Official Video)` are routinely dropped when a video is re-posted. What
 * survives is the words.
 */
internal fun normalizeTitle(title: String): String =
    title
        .lowercase()
        // Bracketed decorations first, before the brackets themselves are stripped as punctuation.
        .replace(BRACKETED, " ")
        .map { if (it.isLetterOrDigit() || it.isWhitespace()) it else ' ' }
        .joinToString("")
        .split(' ', '\t', '\n')
        .filter { it.isNotBlank() }
        .joinToString(" ")

private val BRACKETED = Regex("""[\[(][^\])]*[\])]""")

/**
 * Whether [a] and [b] are plausibly the same upload on two platforms.
 *
 * Titles must match **exactly** once normalised — not as substrings. A false positive here plays a
 * different video than the one tapped, which is far worse than missing a pair, and substring matching
 * would happily equate "Part 1" with "Part 1 Reaction". Durations must both be known: PeerTube reports
 * 0 for a video still being transcoded, and 0 == 0 would pair every such video with every other.
 */
fun isSameUpload(
    a: Video,
    b: Video,
    toleranceSeconds: Int = DEFAULT_DURATION_TOLERANCE_SECONDS,
): Boolean {
    if (a.duration <= 0 || b.duration <= 0) return false
    if (kotlin.math.abs(a.duration - b.duration) > toleranceSeconds) return false

    val titleA = normalizeTitle(a.title)
    val titleB = normalizeTitle(b.title)
    return titleA.isNotEmpty() && titleA == titleB
}

/** The entry in [candidates] that is the same upload as [video], or null when there is none. */
fun findCounterpart(
    video: Video,
    candidates: List<Video>,
    toleranceSeconds: Int = DEFAULT_DURATION_TOLERANCE_SECONDS,
): Video? = candidates.firstOrNull { isSameUpload(video, it, toleranceSeconds) }

/**
 * One chronological list for a linked pair of channels, newest first.
 *
 * A video that exists on both sides appears **once, as the PeerTube copy** — which is the whole point
 * of the feature: the same content, from the decentralised source. Everything unpaired from either
 * side is kept, so nothing disappears just because the other platform does not have it.
 */
fun mergeLinkedChannelVideos(
    youtube: List<Video>,
    peerTube: List<Video>,
    toleranceSeconds: Int = DEFAULT_DURATION_TOLERANCE_SECONDS,
): List<Video> {
    if (peerTube.isEmpty()) return youtube.sortedByDescending { it.timestamp }
    if (youtube.isEmpty()) return peerTube.sortedByDescending { it.timestamp }

    val youtubeOnly = youtube.filter { candidate ->
        findCounterpart(candidate, peerTube, toleranceSeconds) == null
    }
    return (peerTube + youtubeOnly)
        .distinctBy { it.id }
        .sortedByDescending { it.timestamp }
}
