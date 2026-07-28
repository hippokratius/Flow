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

import kotlinx.serialization.Serializable

/*
 * Wire types for the PeerTube REST API: /api/v1/videos, /api/v1/search/videos, /api/v1/videos/{uuid}.
 * Ported from the standalone TubeHub PeerTube client.
 *
 * Every field is defaulted because instances run a wide spread of PeerTube versions and older ones
 * simply omit newer fields.
 */

@Serializable
data class PTVideoListDto(
    val total: Int = 0,
    val data: List<PTVideoDto> = emptyList(),
)

@Serializable
data class PTVideoDto(
    val uuid: String,
    val name: String,
    val description: String? = null,
    val thumbnailPath: String? = null,
    val previewPath: String? = null,
    val duration: Long = 0,
    val publishedAt: String? = null,
    val views: Long = 0,
    val channel: PTChannelDto? = null,
    val tags: List<String> = emptyList(),
    val streamingPlaylists: List<PTStreamingPlaylistDto> = emptyList(),
    val files: List<PTFileDto> = emptyList(),
)

@Serializable
data class PTChannelDto(
    val displayName: String = "",
    /** Channel handle, e.g. "news". Stable per instance; used to build the channel id. */
    val name: String = "",
    /** The channel's ActivityPub actor URL, e.g. https://host/video-channels/name. */
    val url: String? = null,
    val avatar: PTAvatarDto? = null,
    val avatars: List<PTAvatarDto> = emptyList(),
)

@Serializable
data class PTAvatarDto(
    val path: String = "",
    val width: Int = 0,
)

@Serializable
data class PTStreamingPlaylistDto(
    val playlistUrl: String,
)

@Serializable
data class PTFileDto(
    val fileUrl: String,
    val resolution: PTResolutionDto = PTResolutionDto(),
)

@Serializable
data class PTResolutionDto(
    val id: Int = 0,
)

/** Single-video detail response, used to resolve stream URLs lazily. */
@Serializable
data class PTVideoDetailDto(
    val uuid: String = "",
    val name: String = "",
    val duration: Long = 0,
    val streamingPlaylists: List<PTStreamingPlaylistDto> = emptyList(),
    val files: List<PTFileDto> = emptyList(),
)
