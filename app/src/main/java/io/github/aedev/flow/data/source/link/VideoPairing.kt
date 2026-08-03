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

/*
 * How far the two runtimes may differ.
 *
 * The first version allowed five seconds, on the assumption that a mirror is a re-encode of the same
 * file. Real channels do not work that way: c't 3003 publishes the same episode as 14:43 on
 * peertube.heise.de and 15:48 on YouTube — a minute apart, because the platform versions carry
 * different intros and outros. Five seconds rejected every real pair, and since one predicate feeds
 * the shared channel page, the source switch and the redirect alike, the whole feature stayed
 * invisible.
 *
 * So the runtime stops being the deciding criterion and becomes a sanity check: an exactly equal
 * normalised title already carries most of the evidence, and this only has to rule out the case
 * where a channel reuses a title for something of a wholly different length.
 */
const val MIN_DURATION_TOLERANCE_SECONDS = 90
const val DURATION_TOLERANCE_FRACTION = 0.2

/*
 * The second, stricter set: what counts as the same upload when the titles say nothing.
 *
 * Creators who translate their titles for the other platform — "I prefer Claude Opus 5 over Fable"
 * against "Claude Opus 5 gefällt mir besser als Fable" — leave the runtime as the only evidence, so
 * it has to be near-identical rather than merely plausible, and it only counts when no second
 * candidate could claim the same slot. See [findCounterpartByRuntime].
 */
const val RUNTIME_ONLY_TOLERANCE_SECONDS = 2
const val RUNTIME_ONLY_TOLERANCE_FRACTION = 0.01
const val RUNTIME_ONLY_WINDOW_DAYS = 30

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
 * would happily equate "Part 1" with "Part 1 Reaction". That equality carries the match; the runtime
 * only has to be in the same ballpark.
 *
 * An unknown runtime — PeerTube reports 0 while a video is still transcoding — no longer rejects the
 * pair. It used to, which meant a freshly imported video was unpairable for exactly as long as it was
 * new, and new is when someone looks at it.
 */
fun isSameUpload(a: Video, b: Video): Boolean {
    val titleA = normalizeTitle(a.title)
    val titleB = normalizeTitle(b.title)
    if (titleA.isEmpty() || titleA != titleB) return false

    return durationsArePlausible(a.duration, b.duration)
}

/** True when the two runtimes are close enough, or when one of them is not known yet. */
private fun durationsArePlausible(a: Int, b: Int): Boolean {
    if (a <= 0 || b <= 0) return true
    val tolerance = maxOf(
        MIN_DURATION_TOLERANCE_SECONDS,
        (maxOf(a, b) * DURATION_TOLERANCE_FRACTION).toInt(),
    )
    return kotlin.math.abs(a - b) <= tolerance
}

/**
 * The entry in [candidates] that is the same upload as [video], or null when there is none.
 *
 * Takes the **closest** match rather than the first one. With the runtime relaxed to a sanity check,
 * a channel that reuses a title across episodes can produce several candidates, and "whichever came
 * back first from the API" is not an answer — the nearest runtime, and then the nearest publication
 * date, is.
 */
fun findCounterpart(video: Video, candidates: List<Video>): Video? =
    candidates
        .filter { isSameUpload(video, it) }
        .minWithOrNull(
            compareBy(
                { if (it.duration > 0 && video.duration > 0) kotlin.math.abs(it.duration - video.duration) else Int.MAX_VALUE },
                { kotlin.math.abs(it.timestamp - video.timestamp) },
            )
        )
        ?: findCounterpartByRuntime(video, candidates)

/**
 * The counterpart of a video whose title was **translated** for the other platform.
 *
 * The Morpheus Tutorials publish "I prefer Claude Opus 5 over Fable" on YouTube and "Claude Opus 5
 * gefällt mir besser als Fable" on their own instance — same 22:06, same day, nothing in common as
 * text. No amount of title normalising reaches that, so when the title finds nothing, the runtime
 * has to answer on its own.
 *
 * Which is only safe under three conditions at once, because there is no second signal left to
 * check:
 * - both runtimes known and within [RUNTIME_ONLY_TOLERANCE_SECONDS] or one percent — not the
 *   generous window [isSameUpload] allows, but "the same file, re-encoded",
 * - published within [RUNTIME_ONLY_WINDOW_DAYS] of each other,
 * - and **exactly one** candidate qualifies.
 *
 * That last one is what makes this defensible. The moment a channel has two videos of the same
 * length in the same month, this refuses to answer rather than guess — the failure mode is a missing
 * switch, never a wrong video.
 */
private fun findCounterpartByRuntime(video: Video, candidates: List<Video>): Video? {
    if (video.duration <= 0) return null

    val window = RUNTIME_ONLY_WINDOW_DAYS * 24L * 60L * 60L * 1000L
    val tolerance = maxOf(
        RUNTIME_ONLY_TOLERANCE_SECONDS,
        (video.duration * RUNTIME_ONLY_TOLERANCE_FRACTION).toInt(),
    )

    val plausible = candidates.filter { candidate ->
        candidate.duration > 0 &&
            kotlin.math.abs(candidate.duration - video.duration) <= tolerance &&
            // A timestamp of 0 means "unknown", and unknown is not evidence of closeness.
            candidate.timestamp > 0L && video.timestamp > 0L &&
            kotlin.math.abs(candidate.timestamp - video.timestamp) <= window
    }
    return plausible.singleOrNull()
}

/**
 * One chronological list for a linked pair of channels, newest first.
 *
 * A video that exists on both sides appears **once, as the PeerTube copy** — which is the whole point
 * of the feature: the same content, from the decentralised source. Everything unpaired from either
 * side is kept, so nothing disappears just because the other platform does not have it.
 */
fun mergeLinkedChannelVideos(youtube: List<Video>, peerTube: List<Video>): List<Video> {
    if (peerTube.isEmpty()) return youtube.sortedByDescending { it.timestamp }
    if (youtube.isEmpty()) return peerTube.sortedByDescending { it.timestamp }

    val youtubeOnly = youtube.filter { candidate ->
        findCounterpart(candidate, peerTube) == null
    }
    return (peerTube + youtubeOnly)
        .distinctBy { it.id }
        .sortedByDescending { it.timestamp }
}
