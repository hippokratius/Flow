/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.fediverse.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Wire types for the Misskey API. Ported from the standalone TubeHub client.
 * Everything is defaulted — Misskey versions differ in what they return.
 */

@Serializable
data class MiAuthCheckDto(
    val ok: Boolean = false,
    val token: String? = null,
)

@Serializable
data class MiUserDto(
    val id: String? = null,
    val username: String? = null,
)

@Serializable
data class ApShowDto(
    val type: String? = null,
    @SerialName("object") val obj: ApObjectDto? = null,
)

@Serializable
data class ApObjectDto(
    val id: String? = null,
)

@Serializable
data class NoteShowDto(
    val reactions: Map<String, Int> = emptyMap(),
    val renoteCount: Int = 0,
)

@Serializable
data class ReplyDto(
    val id: String,
    val user: ReplyUserDto,
    val text: String? = null,
    val createdAt: String,
)

@Serializable
data class ReplyUserDto(
    val username: String,
    val host: String? = null,
)

/** A note from a Misskey timeline; only the fields needed to spot a video. */
@Serializable
data class MiNoteDto(
    val id: String,
    val createdAt: String? = null,
    val text: String? = null,
    /** ActivityPub id for federated notes. */
    val uri: String? = null,
    /** Canonical URL for federated notes. */
    val url: String? = null,
    val user: MiUserSummaryDto? = null,
    val files: List<MiDriveFileDto> = emptyList(),
    val renote: MiNoteDto? = null,
)

@Serializable
data class MiUserSummaryDto(
    val username: String? = null,
    val host: String? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
)

/** One entry of `users/following`; [id] is the relation id used for paging. */
@Serializable
data class MiFollowingEntryDto(
    val id: String,
    val followee: MiUserSummaryDto? = null,
)

/** Response of `federation/show-instance` — only the software matters here. */
@Serializable
data class MiInstanceInfoDto(
    val softwareName: String? = null,
)

/** Detailed user info (`users/show`), used to check the follow relationship. */
@Serializable
data class MiUserDetailDto(
    val id: String? = null,
    val isFollowing: Boolean = false,
)

@Serializable
data class MiDriveFileDto(
    /** Mime type, e.g. "video/mp4". */
    val type: String? = null,
    val url: String? = null,
    val thumbnailUrl: String? = null,
)
