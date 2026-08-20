/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.player

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.isTruncatedDescription

/**
 * When a federated video needs its detail fetched, and what to keep of it.
 *
 * A YouTube video is filled in by the NewPipe extraction the player runs anyway. A federated one has
 * nothing of the sort: whatever object put it into the player — a feed row, a queue entry, a watch
 * history row after "continue watching" — is what the info panel shows, and each of those carries a
 * different subset of the truth. So the player has to ask the source, and this decides when.
 */
object FederatedMetadataPolicy {

    /**
     * How many failed fetches in a row are worth attempting for one video.
     *
     * The player re-enters the federated load whenever playback is not active, so an unreachable
     * instance would otherwise cost a request per player tick for as long as the video sits there.
     */
    const val MAX_ATTEMPTS = 3

    /**
     * Whether [video] is missing something only the detail endpoint can supply.
     *
     * Deliberately an "or" of what is absent, not an "and" of what is present. The check this
     * replaced asked for a title, a runtime and a channel id — which a watch history row happens to
     * have, so a restored session was declared complete and kept its zero views, its empty date and
     * its empty description for as long as it played.
     */
    fun needsDetail(video: Video): Boolean =
        video.title.isBlank() ||
            video.duration <= 0 ||
            video.channelId.isBlank() ||
            video.viewCount <= 0L ||
            video.uploadDate.isBlank() ||
            video.description.isBlank() ||
            video.description.isTruncatedDescription()

    /**
     * The whole gate, so the player keeps nothing but the bookkeeping.
     *
     * [isEnriched] is what makes this converge. Some videos are legitimately missing what
     * [needsDetail] asks for — a fresh upload really does have no views, and plenty of videos have
     * no description — and for those the field test can never be satisfied. One successful fetch is
     * the answer for that video either way, so the flag ends it.
     */
    fun shouldFetchDetail(
        video: Video?,
        isEnriched: Boolean,
        isInFlight: Boolean,
        failedAttempts: Int,
        maxAttempts: Int = MAX_ATTEMPTS,
    ): Boolean {
        if (video == null || isEnriched || isInFlight) return false
        if (failedAttempts >= maxAttempts) return false
        return needsDetail(video)
    }

    /**
     * [current] with everything the detail endpoint knows better folded in.
     *
     * The split is by who owns the field. Views, publication date, likes, tags and the description
     * are the instance's to report and nothing else has them. Title, channel and artwork are what
     * the user is already looking at, so a second opinion on them would only make the panel flicker.
     *
     * Idempotent, which the caller relies on: it drops the update when the merge changes nothing, so
     * a repeat enrichment has to reach the same fixpoint rather than a new value each time.
     */
    fun merge(current: Video, fetched: Video): Video {
        // Date and timestamp move together or not at all. A blank date on the fetched side means its
        // timestamp is PeerTubeMapper's `System.currentTimeMillis()` fallback rather than a
        // publication time — the same "uploaded just now" that a restored session already shows.
        val takeFetchedDate = fetched.uploadDate.isNotBlank()

        return current.copy(
            title = current.title.ifBlank { fetched.title },
            channelName = current.channelName.ifBlank { fetched.channelName },
            channelId = current.channelId.ifBlank { fetched.channelId },
            channelThumbnailUrl = current.channelThumbnailUrl.ifBlank { fetched.channelThumbnailUrl },
            thumbnailUrl = current.thumbnailUrl.ifBlank { fetched.thumbnailUrl },
            duration = current.duration.takeIf { it > 0 } ?: fetched.duration,
            viewCount = fetched.viewCount.takeIf { it > 0L } ?: current.viewCount,
            likeCount = fetched.likeCount.takeIf { it > 0L } ?: current.likeCount,
            description = fetched.description.ifBlank { current.description },
            tags = current.tags.ifEmpty { fetched.tags },
            uploadDate = if (takeFetchedDate) fetched.uploadDate else current.uploadDate,
            timestamp = if (takeFetchedDate) fetched.timestamp else current.timestamp,
            source = fetched.source,
            instanceHost = current.instanceHost ?: fetched.instanceHost,
        )
    }
}
