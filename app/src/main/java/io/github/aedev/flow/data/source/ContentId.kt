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

/**
 * Which backend a piece of content came from.
 *
 * Named *Kind* rather than *Type* on purpose: [io.github.aedev.flow.player.resolver.SourceType]
 * already exists and means something entirely different (how a stream is muxed).
 */
enum class SourceKind {
    YOUTUBE,
    PEERTUBE,
    LOCAL,
}

/**
 * A [io.github.aedev.flow.data.model.Video.id] parsed into its parts.
 *
 * The source is encoded into the id string itself rather than carried in a parallel column,
 * following the `local_` convention this app already uses for on-device media. That keeps the
 * Room primary key, the playlist cross-references, the navigation arguments, the playback queue
 * and the Media3 session all working unchanged — a prefixed id is still just a `String`.
 *
 * Delimiters are `_` because ids are interpolated straight into navigation routes
 * (`navigate("player/$videoId")`) without encoding, and `_` is unreserved in URIs. Host names
 * cannot contain `_`, so the first one after the prefix is an unambiguous separator even when
 * the native id does contain one (PeerTube short UUIDs are base64url and may).
 */
@JvmInline
value class ContentId(val raw: String) {

    val kind: SourceKind
        get() = when {
            raw.startsWith(PEERTUBE_PREFIX) -> SourceKind.PEERTUBE
            raw.startsWith(LOCAL_PREFIX) -> SourceKind.LOCAL
            else -> SourceKind.YOUTUBE
        }

    /** Host of the PeerTube instance ("tilvids.com"). Null for every other kind. */
    val instanceHost: String?
        get() = if (kind == SourceKind.PEERTUBE) {
            raw.removePrefix(PEERTUBE_PREFIX).substringBefore(SEP).takeIf { it.isNotBlank() }
        } else {
            null
        }

    /** The id as the backend itself knows it: YouTube video id, PeerTube UUID, local key. */
    val nativeId: String
        get() = if (kind == SourceKind.PEERTUBE) {
            raw.removePrefix(PEERTUBE_PREFIX).substringAfter(SEP, missingDelimiterValue = "")
        } else {
            raw
        }

    override fun toString(): String = raw

    companion object {
        const val PEERTUBE_PREFIX = "peertube_"
        const val LOCAL_PREFIX = "local_"
        private const val SEP = '_'

        fun peerTube(host: String, nativeId: String): ContentId =
            ContentId("$PEERTUBE_PREFIX${host.lowercase().trim('/')}$SEP$nativeId")

        fun youTube(videoId: String): ContentId = ContentId(videoId)
    }
}

/** Convenience so call sites read `videoId.contentId.kind` instead of `ContentId(videoId).kind`. */
val String.contentId: ContentId get() = ContentId(this)
