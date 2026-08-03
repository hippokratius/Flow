/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.ui.screens.subscriptions

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.ContentSourceRegistry
import io.github.aedev.flow.data.source.SourceKind
import io.github.aedev.flow.data.source.contentId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/** Channels whose latest uploads the YouTube RSS endpoint cannot deliver. */
fun federatedSubscriptionChannelIds(channelIds: List<String>): List<String> =
    channelIds.filter { it.contentId.kind == SourceKind.PEERTUBE }

fun youtubeSubscriptionChannelIds(channelIds: List<String>): List<String> =
    channelIds.filter { it.contentId.kind == SourceKind.YOUTUBE }

/**
 * Latest uploads of the federated channels among [channelIds].
 *
 * All-settled like the home feed: an instance that is down contributes nothing instead of failing
 * the whole refresh. Only the first page per channel is fetched — the subscription feed shows what
 * is new, and the channel page is where the full back catalogue lives.
 */
suspend fun fetchFederatedSubscriptionUploads(
    registry: ContentSourceRegistry,
    channelIds: List<String>,
): List<Video> = supervisorScope {
    val federated = federatedSubscriptionChannelIds(channelIds)
    if (federated.isEmpty()) return@supervisorScope emptyList()

    federated
        .map { rawId ->
            async {
                val id = rawId.contentId
                registry.forId(id)
                    ?.let { source -> runCatching { source.channelUploads(id) }.getOrNull() }
                    ?.items
                    .orEmpty()
            }
        }
        .awaitAll()
        .flatten()
        .distinctBy { it.id }
        .sortedByDescending { it.timestamp }
}
