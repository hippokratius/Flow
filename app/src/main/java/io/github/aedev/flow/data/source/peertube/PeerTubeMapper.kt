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

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.ContentId
import io.github.aedev.flow.data.source.SourceKind
import io.github.aedev.flow.utils.parseToTimestamp
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

/** Preferred fallback resolution when an instance offers no HLS playlist. */
private const val PREFERRED_MP4_RESOLUTION = 720

/** PeerTube has no itag equivalent; the builder only needs a non-null id. */
private const val PEERTUBE_STREAM_ID = "peertube"

/**
 * Maps a PeerTube video onto Flow's domain model.
 *
 * Pure and Android-free so it can be unit tested — the id shape and the non-blank channel id are
 * both load-bearing, see below.
 */
fun PTVideoDto.toVideo(instance: PeerTubeInstance): Video {
    val host = instance.host
    val thumbnail = when {
        !previewPath.isNullOrEmpty() -> "${instance.url}$previewPath"
        !thumbnailPath.isNullOrEmpty() -> "${instance.url}$thumbnailPath"
        else -> ""
    }

    return Video(
        id = ContentId.peerTube(host, uuid).raw,
        title = name,
        channelName = channel?.displayName.orEmpty(),
        // Never blank. FlowNeuroEngine.rank filters candidates against brain.blockedChannels, so a
        // blank channel id that a user had ever blocked would silently swallow every PeerTube video
        // at once. Falling back to the host keeps the value meaningful and unique per instance.
        channelId = ContentId.peerTube(host, channel?.name?.takeIf { it.isNotBlank() } ?: host).raw,
        thumbnailUrl = thumbnail,
        duration = duration.toInt(),
        viewCount = views,
        // Handed over raw: DateDisplay.parseToTimestamp already understands ISO 8601, which is what
        // PeerTube emits, so the app's own relative/exact date settings apply unchanged.
        uploadDate = publishedAt.orEmpty(),
        timestamp = parseToTimestamp(publishedAt) ?: System.currentTimeMillis(),
        description = description.orEmpty(),
        channelThumbnailUrl = channel?.bestAvatarUrl(instance.url).orEmpty(),
        tags = tags,
        source = SourceKind.PEERTUBE,
        instanceHost = host,
    )
}

private fun PTChannelDto.bestAvatarUrl(instanceUrl: String): String? {
    val path = avatars.maxByOrNull { it.width }?.path?.takeIf { it.isNotBlank() }
        ?: avatar?.path?.takeIf { it.isNotBlank() }
        ?: return null
    return if (path.startsWith("http")) path else "$instanceUrl$path"
}

/**
 * Picks the stream to play and describes it the way the existing player pipeline expects.
 *
 * The delivery method is the whole point: `VideoPlaybackResolver` dispatches on
 * `stream.deliveryMethod`, and only `HLS` reaches `createHlsSource`. Handing it an `.m3u8` tagged
 * `PROGRESSIVE_HTTP` produces a black screen with no error, which is why this has its own test.
 *
 * `isVideoOnly = false` matters too: the resolver's `preferMuxed` path selects on it when there is
 * no separate audio stream, which is always the case here.
 */
fun buildPeerTubeVideoStream(
    streamingPlaylists: List<PTStreamingPlaylistDto>,
    files: List<PTFileDto>,
): VideoStream? {
    streamingPlaylists.firstOrNull()?.let { playlist ->
        return VideoStream.Builder()
            .setId(PEERTUBE_STREAM_ID)
            .setContent(playlist.playlistUrl, true)
            .setDeliveryMethod(DeliveryMethod.HLS)
            .setIsVideoOnly(false)
            .setResolution("")
            .build()
    }

    val file = files.firstOrNull { it.resolution.id == PREFERRED_MP4_RESOLUTION }
        ?: files.firstOrNull()
        ?: return null

    return VideoStream.Builder()
        .setId(PEERTUBE_STREAM_ID)
        .setContent(file.fileUrl, true)
        .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
        .setMediaFormat(MediaFormat.MPEG_4)
        .setIsVideoOnly(false)
        .setResolution(file.resolution.id.takeIf { it > 0 }?.let { "${it}p" }.orEmpty())
        .build()
}
