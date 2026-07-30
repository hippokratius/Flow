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

import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.source.SourceKind
import io.github.aedev.flow.data.source.contentId
import kotlinx.coroutines.flow.first

/**
 * Carries a subscription across a [ChannelLink].
 *
 * The point of the whole feature is to move viewing habits off YouTube, and a subscription is the
 * habit. Subscribing to a creator's YouTube channel while their PeerTube channel stays unsubscribed
 * leaves the decentralised copy invisible in the feed, which is the opposite of the intent.
 *
 * Unsubscribing mirrors too. Anything else leaves half a link subscribed, and the user would have to
 * remember which half — a subscription they did not knowingly create is worse than none.
 *
 * Names come from the link itself rather than from the network: this runs on a button tap, and a
 * lookup would either block the tap or fail silently. A missing avatar is filled in later by
 * `SubscriptionsViewModel.withSubscriptionAvatars`.
 */
class ChannelLinkSubscriptionMirror(
    private val links: ChannelLinkStore,
    private val subscriptions: SubscriptionRepository,
) {

    /**
     * Applies [subscribed] to the counterpart of [channelId], if there is one.
     *
     * Idempotent and safe to call for any id: an unlinked channel, a local id, or a disabled mirror
     * setting all result in no action.
     */
    suspend fun mirror(channelId: String, subscribed: Boolean) {
        if (channelId.isBlank()) return
        if (!links.mirrorSubscriptions.first()) return

        val link = when (channelId.trim().contentId.kind) {
            SourceKind.YOUTUBE -> links.forYouTube(channelId)
            SourceKind.PEERTUBE -> links.forPeerTube(channelId)
            SourceKind.LOCAL -> null
        } ?: return

        val counterpartId: String
        val counterpartName: String
        if (channelId.trim().contentId.kind == SourceKind.YOUTUBE) {
            counterpartId = link.peerTubeChannelId
            counterpartName = link.peerTubeChannelName
        } else {
            counterpartId = link.youtubeChannelId
            counterpartName = link.youtubeChannelName
        }
        if (counterpartId.isBlank()) return

        val alreadySubscribed = subscriptions.isSubscribed(counterpartId).first()
        when {
            subscribed && !alreadySubscribed -> subscriptions.subscribe(
                ChannelSubscription(
                    channelId = counterpartId,
                    channelName = counterpartName,
                    channelThumbnail = "",
                )
            )
            !subscribed && alreadySubscribed -> subscriptions.unsubscribe(counterpartId)
        }
    }
}
