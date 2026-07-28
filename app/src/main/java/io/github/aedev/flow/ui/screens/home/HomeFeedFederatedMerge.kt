/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.model.Video

/** Result of interleaving federated videos into the feed. */
internal data class MergedFeed(
    val videos: List<Video>,
    /** Federated videos that did not fit, kept for the next page. */
    val leftover: List<Video>,
)

/** One federated video after every this many YouTube videos. */
internal const val DEFAULT_FEDERATED_CADENCE = 4

/**
 * Interleaves [federated] videos into [base] at a fixed cadence.
 *
 * A positional quota rather than score-based competition, on purpose. `FlowNeuroEngine.rank` scores
 * candidates from channel topic profiles, subscription membership and watch history — a newly added
 * backend has none of those, so its videos would score at the bottom and be squeezed out entirely.
 * Interleaving happens after ranking and blending, so federated content keeps a guaranteed share
 * without disturbing how the YouTube lanes are ordered among themselves.
 *
 * Order within each list is preserved. Videos already present in [base] (by id) are dropped, so a
 * refresh cannot double up. Surplus goes to [MergedFeed.leftover] for the paging path instead of
 * being clumped onto the end, which would look like a separate section.
 */
internal fun mergeFederatedVideos(
    base: List<Video>,
    federated: List<Video>,
    everyNth: Int = DEFAULT_FEDERATED_CADENCE,
): MergedFeed {
    if (federated.isEmpty()) return MergedFeed(base, emptyList())

    val existingIds = base.mapTo(HashSet()) { it.id }
    val fresh = federated.filter { existingIds.add(it.id) }
    if (fresh.isEmpty()) return MergedFeed(base, emptyList())

    // With nothing to interleave into, the federated videos are the feed. Returning them rather
    // than an empty list matters: an empty feed makes HomeViewModel fall back to trending.
    if (base.isEmpty()) return MergedFeed(fresh, emptyList())

    val cadence = everyNth.coerceAtLeast(1)
    val merged = ArrayList<Video>(base.size + fresh.size)
    val pending = ArrayDeque(fresh)

    base.forEachIndexed { index, video ->
        merged += video
        if ((index + 1) % cadence == 0 && pending.isNotEmpty()) {
            merged += pending.removeFirst()
        }
    }

    return MergedFeed(merged, pending.toList())
}
