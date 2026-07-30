/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.data.source.peertube

import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.SearchFilter
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.ContentId
import io.github.aedev.flow.data.source.ContentSource
import io.github.aedev.flow.data.source.ContentSourceRegistry
import io.github.aedev.flow.data.source.PlaybackSpec
import io.github.aedev.flow.data.source.SourceCursor
import io.github.aedev.flow.data.source.SourceError
import io.github.aedev.flow.data.source.SourceKind
import io.github.aedev.flow.data.source.SourcePage
import io.github.aedev.flow.data.source.watchUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PeerTube as a content source, aggregating over every instance the user has enabled.
 *
 * Aggregation is all-settled, not all-or-nothing: instances are queried concurrently and a failing
 * one contributes a [SourceError] instead of throwing. One unreachable server must never blank the
 * feed — that behaviour is ported from the standalone TubeHub client, where it was the difference
 * between a usable feed and an empty one.
 */
@Singleton
class PeerTubeContentSource @Inject constructor(
    private val api: PeerTubeApi,
    private val preferences: PeerTubePreferences,
) : ContentSource {

    override val key: String = ContentSourceRegistry.KEY_PEERTUBE
    override val kind: SourceKind = SourceKind.PEERTUBE

    override suspend fun feed(cursor: SourceCursor?, limit: Int): SourcePage<Video> =
        aggregate(limit = limit, start = cursor.offset(), search = null)

    override suspend fun search(
        query: String,
        filter: SearchFilter,
        cursor: SourceCursor?,
    ): SourcePage<Video> {
        // PeerTube's search endpoint only returns videos, so a channel- or playlist-only filter has
        // nothing to contribute rather than something wrong to contribute.
        if (filter == SearchFilter.CHANNELS || filter == SearchFilter.PLAYLISTS) {
            return SourcePage(emptyList(), null)
        }
        return aggregate(limit = DEFAULT_SEARCH_LIMIT, start = cursor.offset(), search = query)
    }

    override suspend fun video(id: ContentId): Video? {
        val host = id.instanceHost ?: return null
        val instance = instanceFor(host)
        val detail = runCatching { api.videoDetail(instance.url, id.nativeId) }.getOrNull()
            ?: return null
        return PTVideoDto(
            uuid = detail.uuid.ifBlank { id.nativeId },
            name = detail.name,
            duration = detail.duration,
            streamingPlaylists = detail.streamingPlaylists,
            files = detail.files,
        ).toVideo(instance)
    }

    override suspend fun channel(id: ContentId): Channel? {
        val host = id.instanceHost ?: return null
        val instance = instanceFor(host)
        val handle = id.nativeId.takeIf { it.isNotBlank() } ?: return null
        return runCatching { api.channelDetail(instance.url, handle) }.getOrNull()
            ?.toChannel(instance)
    }

    /**
     * Channels matching [query], from the user's instances *and* a network-wide search index.
     *
     * The index — SepiaSearch by default — is what makes this useful at all: the configured instances
     * are a handful out of thousands, and many restrict their index to local content. It is one more
     * lane in the same all-settled fan-out, so if it is down or switched off the instance hits still
     * arrive.
     *
     * Every hit, whichever lane found it, is mapped by [toDiscoveredChannel], which addresses a channel
     * by its own host rather than by whoever answered. That normalises mirrors onto their origin, so
     * `distinctBy` below collapses one creator found twice into one row.
     */
    override suspend fun searchChannels(query: String): SourcePage<Channel> = supervisorScope {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@supervisorScope SourcePage(emptyList(), null)

        val sources = buildList {
            preferences.currentEnabledInstances().forEach { add(it.host to it.url) }
            preferences.currentDiscoveryIndexUrl()?.let { indexUrl ->
                add((hostOf(indexUrl) ?: indexUrl) to indexUrl)
            }
        }
        if (sources.isEmpty()) return@supervisorScope SourcePage(emptyList(), null)

        val results = sources.map { (host, url) ->
            async {
                host to runCatching {
                    api.searchChannels(url, trimmed).data.mapNotNull { it.toDiscoveredChannel(url) }
                }
            }
        }.awaitAll()

        SourcePage(
            items = results.flatMap { (_, result) -> result.getOrNull().orEmpty() }
                .distinctBy { it.id }
                .sortedByDescending { it.subscriberCount },
            next = null,
            errors = results.mapNotNull { (host, result) ->
                result.exceptionOrNull()?.let { failure ->
                    SourceError(
                        sourceKey = key,
                        instanceHost = host,
                        message = failure.message ?: failure::class.java.simpleName,
                    )
                }
            },
        )
    }

    override suspend fun channelUploads(id: ContentId, cursor: SourceCursor?): SourcePage<Video> {
        val host = id.instanceHost ?: return SourcePage(emptyList(), null)
        val handle = id.nativeId.takeIf { it.isNotBlank() }
            ?: return SourcePage(emptyList(), null)
        val instance = instanceFor(host)
        val start = cursor.offset()

        val page = runCatching {
            api.channelVideos(instance.url, handle, count = CHANNEL_PAGE_SIZE, start = start)
        }.getOrElse { failure ->
            return SourcePage(
                items = emptyList(),
                next = null,
                errors = listOf(
                    SourceError(
                        sourceKey = key,
                        instanceHost = host,
                        message = failure.message ?: failure::class.java.simpleName,
                    )
                ),
            )
        }

        val videos = page.data.map { it.toVideo(instance) }
        return SourcePage(
            items = videos,
            // A short page is the end of the channel. PeerTube reports a `total`, but instances
            // disagree on whether it counts videos the caller may actually see, so the page size is
            // the more reliable signal.
            next = SourceCursor.Offset(start + CHANNEL_PAGE_SIZE)
                .takeIf { videos.size >= CHANNEL_PAGE_SIZE },
        )
    }

    override suspend fun resolvePlayback(id: ContentId): PlaybackSpec? {
        val host = id.instanceHost ?: return null
        val instance = instanceFor(host)
        val detail = runCatching { api.videoDetail(instance.url, id.nativeId) }.getOrNull()
            ?: return null
        val stream = buildPeerTubeVideoStream(detail.streamingPlaylists, detail.files) ?: return null

        return PlaybackSpec(
            id = id,
            title = detail.name,
            durationSeconds = detail.duration,
            videoStreams = listOf(stream),
            // PeerTube muxes audio into the stream, so there is never a separate audio track.
            audioStreams = emptyList(),
            isLive = false,
            // The watch page URL doubles as the ActivityPub object URL, which is what a Fediverse
            // account needs in order to like or boost this video later on.
            apUrl = watchUrl(id),
        )
    }

    /**
     * Fans out across the enabled instances, merges, and sorts newest first.
     *
     * [limit] is the total wanted, so each instance is asked for its share plus a little slack —
     * otherwise a single prolific instance would crowd out all the others after sorting.
     */
    private suspend fun aggregate(
        limit: Int,
        start: Int,
        search: String?,
    ): SourcePage<Video> = supervisorScope {
        val instances = preferences.currentEnabledInstances()
        if (instances.isEmpty()) return@supervisorScope SourcePage(emptyList(), null)

        val perInstance = ((limit / instances.size) + PER_INSTANCE_SLACK)
            .coerceIn(MIN_PER_INSTANCE, MAX_PER_INSTANCE)

        val results = instances.map { instance ->
            async {
                instance to runCatching {
                    api.videos(instance.url, count = perInstance, start = start, search = search)
                        .data
                        .map { it.toVideo(instance) }
                }
            }
        }.awaitAll()

        val videos = results
            .flatMap { (_, result) -> result.getOrNull().orEmpty() }
            .sortedByDescending { it.timestamp }

        val errors = results.mapNotNull { (instance, result) ->
            result.exceptionOrNull()?.let { failure ->
                SourceError(
                    sourceKey = key,
                    instanceHost = instance.host,
                    message = failure.message ?: failure::class.java.simpleName,
                )
            }
        }

        SourcePage(
            items = videos,
            next = SourceCursor.Offset(start + perInstance).takeIf { videos.isNotEmpty() },
            errors = errors,
        )
    }

    /**
     * The configured instance for [host], or a throwaway stand-in built from the host itself.
     *
     * Never null on purpose: a video can outlive the instance entry that produced it — it may sit
     * in the watch history or the feed cache after the user disabled or deleted that instance — and
     * it should still play. The host is embedded in the video id, which is all that is needed.
     */
    private suspend fun instanceFor(host: String): PeerTubeInstance =
        preferences.currentEnabledInstances().firstOrNull { it.host.equals(host, ignoreCase = true) }
            ?: PeerTubeInstance(id = "transient-$host", name = host, url = "https://$host")

    private fun SourceCursor?.offset(): Int = (this as? SourceCursor.Offset)?.start ?: 0

    internal companion object {
        const val PER_INSTANCE_SLACK = 4
        const val MIN_PER_INSTANCE = 6
        const val MAX_PER_INSTANCE = 25
        const val DEFAULT_SEARCH_LIMIT = 20
        const val CHANNEL_PAGE_SIZE = 20
    }
}
