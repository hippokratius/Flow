/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.ui.screens.settings

import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.source.SourceKind
import io.github.aedev.flow.data.source.contentId
import io.github.aedev.flow.data.source.link.ChannelLink
import io.github.aedev.flow.data.source.link.normalizePeerTubeChannelId
import io.github.aedev.flow.utils.ThumbnailUrlResolver

/*
 * Turning the user's subscriptions into a channel picker. Pure, so the filtering and — more
 * importantly — the source filtering below are unit testable.
 */

/**
 * The subscribed channels that can be the YouTube half of a link.
 *
 * The source filter is load-bearing, not defensive: `ChannelLinkSubscriptionMirror` writes PeerTube
 * subscriptions into the very same store, so an unfiltered list would offer PeerTube channels as
 * YouTube ones and produce links with two federated halves — which `ChannelLink.isValid` then rejects
 * silently, leaving a picker that appears to do nothing.
 *
 * Mapped to the domain [Channel] following `SubscriptionsViewModel`'s own conversion, so the picker
 * rows and the PeerTube search results can share one composable.
 */
fun youtubeSubscriptionChannels(subscriptions: List<ChannelSubscription>): List<Channel> =
    subscriptions
        .filter { it.channelId.contentId.kind == SourceKind.YOUTUBE }
        .map { subscription ->
            Channel(
                id = subscription.channelId,
                name = subscription.channelName.ifBlank { subscription.channelId },
                thumbnailUrl = ThumbnailUrlResolver.resolveChannelAvatar(subscription.channelThumbnail),
                subscriberCount = 0L,
                isSubscribed = true,
                isMusic = subscription.isMusic,
            )
        }

/**
 * The subscribed PeerTube channels, offered as the counterpart half.
 *
 * The app already knows these; making the user search for a channel they are subscribed to was the
 * same asymmetry the YouTube half just lost. Ids are normalised so that picking a channel here and
 * finding the same channel through search produce one link, not two — see
 * [io.github.aedev.flow.data.source.link.normalizePeerTubeChannelId].
 */
fun peerTubeSubscriptionChannels(subscriptions: List<ChannelSubscription>): List<Channel> =
    subscriptions
        .filter { it.channelId.contentId.kind == SourceKind.PEERTUBE }
        .map { subscription ->
            val id = normalizePeerTubeChannelId(subscription.channelId)
            Channel(
                id = id,
                name = subscription.channelName.ifBlank { id.contentId.nativeId },
                thumbnailUrl = subscription.channelThumbnail,
                subscriberCount = 0L,
                isSubscribed = true,
                source = SourceKind.PEERTUBE,
                instanceHost = id.contentId.instanceHost,
            )
        }
        .distinctBy { it.id }

/**
 * Drops the channels that already have a counterpart.
 *
 * A suggestion list is a list of things still to do; leaving finished entries in it means searching
 * past them every time. Changing an existing pairing still works — the links are listed right below
 * and removing one puts both halves back in the suggestions.
 */
fun withoutLinkedChannels(channels: List<Channel>, links: List<ChannelLink>): List<Channel> {
    if (links.isEmpty()) return channels
    val linkedYoutube = links.mapTo(mutableSetOf()) { it.youtubeChannelId.lowercase() }
    val linkedPeerTube = links.mapTo(mutableSetOf()) {
        normalizePeerTubeChannelId(it.peerTubeChannelId).lowercase()
    }
    return channels.filterNot { channel ->
        when (channel.id.contentId.kind) {
            SourceKind.PEERTUBE -> normalizePeerTubeChannelId(channel.id).lowercase() in linkedPeerTube
            SourceKind.YOUTUBE -> channel.id.lowercase() in linkedYoutube
            SourceKind.LOCAL -> false
        }
    }
}

/** Substring match on the name, which is what someone typing a creator's name expects. */
fun filterChannelsByName(channels: List<Channel>, query: String): List<Channel> {
    val needle = query.trim()
    if (needle.isEmpty()) return channels
    return channels.filter { it.name.contains(needle, ignoreCase = true) }
}

/**
 * Channel id to display name, built from the subscriptions.
 *
 * Used to show a name for links stored before the picker existed, whose `youtubeChannelName` is
 * blank. A linked channel is almost always subscribed — mirroring is on by default — so this covers
 * the realistic cases without a single network call, which is the alternative: resolving a name from
 * a `UC…` id means a full channel-page extraction.
 */
fun subscriptionNamesById(subscriptions: List<ChannelSubscription>): Map<String, String> =
    subscriptions
        .filter { it.channelName.isNotBlank() }
        .associate { it.channelId to it.channelName }

/** Best name available for the YouTube half, falling back to the raw id. */
fun ChannelLink.youtubeDisplayName(names: Map<String, String>): String =
    youtubeChannelName.takeIf { it.isNotBlank() }
        ?: names[youtubeChannelId]
        ?: youtubeChannelId
