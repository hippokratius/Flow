/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.data.source.youtube

import io.github.aedev.flow.data.model.SearchFilter
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.source.ContentId
import io.github.aedev.flow.data.source.ContentSource
import io.github.aedev.flow.data.source.ContentSourceRegistry
import io.github.aedev.flow.data.source.PlaybackSpec
import io.github.aedev.flow.data.source.SourceCursor
import io.github.aedev.flow.data.source.SourceKind
import io.github.aedev.flow.data.source.SourcePage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapts the existing [YouTubeRepository] to [ContentSource].
 *
 * Pure delegation — the repository is not modified. Its public API leaks NewPipe types on purpose
 * and ~30 files depend on that; this only presents the source-neutral subset alongside it.
 */
@Singleton
class YouTubeContentSource @Inject constructor(
    private val repository: YouTubeRepository,
) : ContentSource {

    override val key: String = ContentSourceRegistry.KEY_YOUTUBE
    override val kind: SourceKind = SourceKind.YOUTUBE

    private fun SourceCursor?.asPage() = (this as? SourceCursor.NewPipePage)?.page

    override suspend fun feed(cursor: SourceCursor?, limit: Int): SourcePage<Video> {
        val (videos, next) = repository.getTrendingVideos(nextPage = cursor.asPage())
        return SourcePage(videos, next?.let(SourceCursor::NewPipePage))
    }

    override suspend fun search(
        query: String,
        filter: SearchFilter,
        cursor: SourceCursor?,
    ): SourcePage<Video> {
        val (videos, next) = repository.searchVideos(query, cursor.asPage())
        return SourcePage(videos, next?.let(SourceCursor::NewPipePage))
    }

    override suspend fun video(id: ContentId): Video? = repository.getVideo(id.nativeId)

    /**
     * The default limit of six exists for the subscription feed, which asks many channels for a
     * handful each. Here a single channel is asked, to find one specific upload among its recent
     * ones — six would miss anything older than a couple of weeks. The page is fetched whole either
     * way, so asking for more costs no extra request.
     */
    override suspend fun channelUploads(id: ContentId, cursor: SourceCursor?): SourcePage<Video> =
        SourcePage(repository.getChannelUploads(id.nativeId, limitPerChannel = 20), null)

    /**
     * Null on purpose. YouTube playback keeps going through the existing InnerTube-versus-NewPipe
     * race in `VideoPlayerViewModel` — PoToken, cipher deobfuscation, SABR and all. Squeezing that
     * state machine behind a one-shot suspend function would be a rewrite of the most fragile and
     * most upstream-churned code in the app, for no gain.
     *
     * The abstraction is asymmetric by design: YouTube keeps its bespoke path, new sources use the
     * simple one.
     */
    override suspend fun resolvePlayback(id: ContentId): PlaybackSpec? = null
}
