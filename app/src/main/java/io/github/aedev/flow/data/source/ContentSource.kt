/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.data.source

import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.model.SearchFilter
import io.github.aedev.flow.data.model.Video

/**
 * One content backend.
 *
 * Deliberately narrower than [io.github.aedev.flow.data.repository.YouTubeRepository], which has
 * ~30 public methods and leaks NewPipe types on purpose. This covers only what is genuinely
 * source-neutral; YouTube keeps its extra surface where it already lives.
 *
 * In-repo precedent for the shape: [io.github.aedev.flow.data.lyrics.LyricsProvider].
 */
interface ContentSource {

    /** Stable key, persisted in preferences and used as the registry map key. */
    val key: String

    val kind: SourceKind

    fun handles(id: ContentId): Boolean = id.kind == kind

    suspend fun feed(cursor: SourceCursor? = null, limit: Int = 20): SourcePage<Video>

    suspend fun search(
        query: String,
        filter: SearchFilter = SearchFilter.ALL,
        cursor: SourceCursor? = null,
    ): SourcePage<Video>

    suspend fun video(id: ContentId): Video?

    suspend fun channel(id: ContentId): Channel? = null

    /**
     * Channels matching [query].
     *
     * Separate from [search] because that one returns videos, and because only sources that can
     * actually answer it implement this: YouTube's channel search lives in `YouTubeRepository` behind
     * NewPipe types and has no caller that needs it here.
     */
    suspend fun searchChannels(query: String): SourcePage<Channel> = SourcePage(emptyList(), null)

    suspend fun channelUploads(id: ContentId, cursor: SourceCursor? = null): SourcePage<Video> =
        SourcePage(emptyList(), null)

    /**
     * Comments on a video.
     *
     * Empty by default, and YouTube deliberately does not implement it: its comment path is paged
     * through NewPipe `Page` tokens that the player already threads through `loadMoreComments`, and
     * flattening that behind a one-shot list would lose the paging.
     */
    suspend fun comments(id: ContentId): List<Comment> = emptyList()

    /**
     * Everything the player needs to build a MediaSource, or null when this source hands playback
     * off to a bespoke path (YouTube does — see YouTubeContentSource).
     */
    suspend fun resolvePlayback(id: ContentId): PlaybackSpec? = null
}

/**
 * Opaque continuation token. NewPipe hands back a [org.schabi.newpipe.extractor.Page]; PeerTube
 * pages by numeric offset. Neither leaks into calling code.
 */
sealed interface SourceCursor {
    @JvmInline
    value class NewPipePage(val page: org.schabi.newpipe.extractor.Page) : SourceCursor

    @JvmInline
    value class Offset(val start: Int) : SourceCursor
}

/**
 * A page of results that can be *partially* successful.
 *
 * This is the shape aggregation needs: when one PeerTube instance out of five is down, its videos
 * are missing but the other four still render, and the failure is reported rather than thrown.
 */
data class SourcePage<T>(
    val items: List<T>,
    val next: SourceCursor?,
    val errors: List<SourceError> = emptyList(),
)

data class SourceError(
    val sourceKey: String,
    val instanceHost: String?,
    val message: String,
)
