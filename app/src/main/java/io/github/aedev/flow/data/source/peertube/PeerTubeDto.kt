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
    // Defaulted so a response missing one of them degrades rather than failing to parse; the detail
    // DTO this replaced was tolerant that way and the playback path relied on it.
    val uuid: String = "",
    val name: String = "",
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

/**
 * Channel detail response of `GET /api/v1/video-channels/{handle}`.
 *
 * Also used for `GET /api/v1/accounts/{handle}`: an account response carries the same fields minus
 * the banners, and every field here is optional, so one type covers both. That matters because a
 * federated reference can point at either actor — see [PeerTubeApi.channelDetail].
 */
@Serializable
data class PTChannelDetailDto(
    /** Local part of the handle, without the host. */
    val name: String = "",
    val displayName: String = "",
    /** Origin host of the actor, which differs from the queried instance for mirrored channels. */
    val host: String = "",
    val description: String? = null,
    val followersCount: Long = 0,
    val url: String? = null,
    val avatar: PTAvatarDto? = null,
    val avatars: List<PTAvatarDto> = emptyList(),
    val banner: PTAvatarDto? = null,
    val banners: List<PTAvatarDto> = emptyList(),
)

/** Comment threads of `GET /api/v1/videos/{id}/comment-threads`. */
@Serializable
data class PTCommentListDto(
    val total: Int = 0,
    val data: List<PTCommentDto> = emptyList(),
)

@Serializable
data class PTCommentDto(
    val id: Long = 0,
    /** HTML, as PeerTube stores it. */
    val text: String = "",
    val createdAt: String? = null,
    val totalReplies: Int = 0,
    val isDeleted: Boolean = false,
    val account: PTAccountDto? = null,
)

@Serializable
data class PTAccountDto(
    val name: String = "",
    val displayName: String = "",
    val host: String = "",
    val url: String? = null,
    val avatar: PTAvatarDto? = null,
    val avatars: List<PTAvatarDto> = emptyList(),
)

/** Channel search response of `GET /api/v1/search/video-channels`. */
@Serializable
data class PTChannelListDto(
    val total: Int = 0,
    val data: List<PTChannelDetailDto> = emptyList(),
)

/*
 * There is deliberately no separate detail DTO.
 *
 * There was one, with five fields, written when the detail endpoint served only playback. Once the
 * source switch started reading metadata through it, every federated video arrived with no channel,
 * no view count and no publication date — the fields simply were not in the class, so they silently
 * took their defaults. The detail response is a superset of the list form, `ignoreUnknownKeys`
 * covers the difference, and one shape means one place to keep complete.
 */
