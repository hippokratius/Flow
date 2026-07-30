/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.data.source.link

import io.github.aedev.flow.data.source.ContentId
import io.github.aedev.flow.data.source.SourceKind
import io.github.aedev.flow.data.source.contentId
import kotlinx.serialization.Serializable

/**
 * A creator's YouTube channel and their PeerTube channel, declared by the user to be the same person.
 *
 * Established by hand, never guessed. Matching channels by name is unreliable in exactly the way that
 * matters — reupload accounts and mirrors routinely carry a creator's name — and a wrong link would
 * quietly send the user to someone else's videos.
 */
@Serializable
data class ChannelLink(
    /** A YouTube channel id (`UC…`) or handle. */
    val youtubeChannelId: String,
    val youtubeChannelName: String = "",
    /** A [ContentId] in raw form: `peertube_{host}_{handle}`. */
    val peerTubeChannelId: String,
    val peerTubeChannelName: String = "",
    val createdAt: Long = 0L,
) {
    /** True when both halves are present and point at the source they claim to. */
    val isValid: Boolean
        get() = youtubeChannelId.isNotBlank() &&
            youtubeChannelId.contentId.kind == SourceKind.YOUTUBE &&
            peerTubeChannelId.contentId.kind == SourceKind.PEERTUBE

    val peerTubeInstanceHost: String? get() = peerTubeChannelId.contentId.instanceHost
}

/**
 * Pure operations on the link list, so ordering and de-duplication can be tested without a DataStore.
 *
 * Mirrors [io.github.aedev.flow.data.source.peertube.PeerTubeInstances], which does the same for the
 * instance list.
 */
object ChannelLinks {

    fun forYouTube(links: List<ChannelLink>, youtubeChannelId: String): ChannelLink? {
        val needle = youtubeChannelId.trim()
        if (needle.isEmpty()) return null
        return links.firstOrNull { it.youtubeChannelId.equals(needle, ignoreCase = true) }
    }

    fun forPeerTube(links: List<ChannelLink>, peerTubeChannelId: String): ChannelLink? {
        val needle = peerTubeChannelId.trim()
        if (needle.isEmpty()) return null
        return links.firstOrNull { it.peerTubeChannelId.equals(needle, ignoreCase = true) }
    }

    /** The counterpart of [channelId] on the other platform, whichever side was passed in. */
    fun counterpart(links: List<ChannelLink>, channelId: String): String? =
        when (channelId.trim().contentId.kind) {
            SourceKind.PEERTUBE -> forPeerTube(links, channelId)?.youtubeChannelId
            SourceKind.YOUTUBE -> forYouTube(links, channelId)?.peerTubeChannelId
            SourceKind.LOCAL -> null
        }

    /**
     * Adds [link], replacing any existing link that shares either half.
     *
     * One channel gets one counterpart. Allowing several would leave every consumer — the hint row,
     * the subscription mirror — to pick one arbitrarily, and re-linking is the far commoner intent
     * than fanning out.
     */
    fun add(links: List<ChannelLink>, link: ChannelLink): List<ChannelLink> {
        if (!link.isValid) return links
        val withoutConflicts = links.filterNot {
            it.youtubeChannelId.equals(link.youtubeChannelId, ignoreCase = true) ||
                it.peerTubeChannelId.equals(link.peerTubeChannelId, ignoreCase = true)
        }
        return listOf(link) + withoutConflicts
    }

    /** Removes by either half, so both the settings list and a channel page can call it. */
    fun remove(links: List<ChannelLink>, channelId: String): List<ChannelLink> {
        val needle = channelId.trim()
        if (needle.isEmpty()) return links
        return links.filterNot {
            it.youtubeChannelId.equals(needle, ignoreCase = true) ||
                it.peerTubeChannelId.equals(needle, ignoreCase = true)
        }
    }
}
