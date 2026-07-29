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
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.ContentId
import io.github.aedev.flow.data.source.SourceKind
import io.github.aedev.flow.data.source.federatedChannelUrl
import io.github.aedev.flow.utils.parseToTimestamp
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream
import java.net.URI

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
        // The value is also the handle the channel page looks up — see [handleOn].
        channelId = ContentId.peerTube(host, channel.handleOn(host)).raw,
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

/**
 * Maps a PeerTube channel onto Flow's domain model.
 *
 * The banner is the reason [io.github.aedev.flow.data.model.Channel] carries a `bannerUrl`: PeerTube
 * serves one per channel and the existing [io.github.aedev.flow.ui.components.ChannelBanner] renders
 * it unchanged.
 */
fun PTChannelDetailDto.toChannel(instance: PeerTubeInstance): Channel {
    val id = ContentId.peerTube(instance.host, handleOn(instance.host))
    return Channel(
        id = id.raw,
        name = displayName.takeIf { it.isNotBlank() } ?: name,
        thumbnailUrl = avatars.pickWidest(avatar, instance.url).orEmpty(),
        subscriberCount = followersCount,
        description = description.orEmpty(),
        url = federatedChannelUrl(id).orEmpty(),
        bannerUrl = banners.pickWidest(banner, instance.url).orEmpty(),
        source = SourceKind.PEERTUBE,
        instanceHost = instance.host,
    )
}

/**
 * The handle this channel is addressed by *on [instanceHost]*.
 *
 * A channel hosted by the queried instance is addressed by its bare name. One that the instance only
 * mirrors is a remote actor, and PeerTube's API then requires `name@originhost` — a bare name either
 * 404s or, worse, resolves to a local channel that happens to share the name. The origin host comes
 * from the ActivityPub actor URL (`https://originhost/video-channels/name`), which is the only place
 * the video listing reports it.
 */
internal fun PTChannelDto?.handleOn(instanceHost: String): String =
    peerTubeHandle(this?.name, actorHost(this?.url), instanceHost)

internal fun PTChannelDetailDto.handleOn(instanceHost: String): String =
    peerTubeHandle(name, host.takeIf { it.isNotBlank() } ?: actorHost(url), instanceHost)

private fun peerTubeHandle(name: String?, originHost: String?, instanceHost: String): String {
    // Falls back to the host so the id is never blank; see the call site in toVideo.
    val local = name?.trim()?.takeIf { it.isNotBlank() } ?: return instanceHost
    if (local.contains('@')) return local
    val origin = originHost?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return local
    return if (origin == instanceHost.trim().lowercase()) local else "$local@$origin"
}

private fun actorHost(url: String?): String? =
    url?.takeIf { it.isNotBlank() }
        ?.let { runCatching { URI(it).host }.getOrNull() }
        ?.takeIf { it.isNotBlank() }

private fun PTChannelDto.bestAvatarUrl(instanceUrl: String): String? =
    avatars.pickWidest(avatar, instanceUrl)

/** Widest artwork available, falling back to the pre-4.2 single-image field. */
private fun List<PTAvatarDto>.pickWidest(legacy: PTAvatarDto?, instanceUrl: String): String? {
    val path = maxByOrNull { it.width }?.path?.takeIf { it.isNotBlank() }
        ?: legacy?.path?.takeIf { it.isNotBlank() }
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
