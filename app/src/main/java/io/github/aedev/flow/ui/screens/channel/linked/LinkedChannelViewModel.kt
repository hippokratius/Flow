/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.ui.screens.channel.linked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.ContentSourceRegistry
import io.github.aedev.flow.data.source.contentId
import io.github.aedev.flow.data.source.link.ChannelLink
import io.github.aedev.flow.data.source.link.ChannelLinkStore
import io.github.aedev.flow.data.source.link.ChannelLinkSubscriptionMirror
import io.github.aedev.flow.data.source.link.mergeLinkedChannelVideos
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

/**
 * One channel page for a creator who publishes on both platforms.
 *
 * Reached with either half's id — the link is looked up in both directions, so tapping the creator
 * anywhere lands on the same page.
 *
 * The header comes from the **PeerTube** side. Not an oversight: PeerTube serves name, description,
 * follower count, avatar and banner in a single cheap REST call, while the equivalent for YouTube is
 * a full channel-page extraction through NewPipe. The stored link already carries the YouTube display
 * name for the cases where PeerTube's metadata is thin.
 */
@HiltViewModel
class LinkedChannelViewModel @Inject constructor(
    private val registry: ContentSourceRegistry,
    private val links: ChannelLinkStore,
    private val subscriptions: SubscriptionRepository,
) : ViewModel() {

    private val mirror = ChannelLinkSubscriptionMirror(links, subscriptions)

    private val _uiState = MutableStateFlow(LinkedChannelUiState())
    val uiState: StateFlow<LinkedChannelUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var subscriptionJob: Job? = null

    fun load(channelId: String) {
        if (channelId.isBlank()) return
        if (_uiState.value.requestedChannelId == channelId && loadJob?.isActive == true) return

        loadJob?.cancel()
        _uiState.value = LinkedChannelUiState(requestedChannelId = channelId, isLoading = true)

        loadJob = viewModelScope.launch {
            val link = links.forYouTube(channelId) ?: links.forPeerTube(channelId)
            if (link == null) {
                // Nothing to merge. The caller should not have routed here, but a link can be removed
                // while the page is open, so this must not be a crash.
                _uiState.update { it.copy(isLoading = false, hasError = true, errorLog = NO_LINK) }
                return@launch
            }

            observeSubscription(link.peerTubeChannelId)
            val loaded = loadBothSides(link)
            _uiState.value = loaded.copy(
                requestedChannelId = channelId,
                isSubscribed = _uiState.value.isSubscribed,
            )
        }
    }

    private suspend fun loadBothSides(link: ChannelLink): LinkedChannelUiState = supervisorScope {
        val peerTubeId = link.peerTubeChannelId.contentId
        val youtubeId = link.youtubeChannelId.contentId

        val metadata = async {
            runCatching { registry.forId(peerTubeId)?.channel(peerTubeId) }
        }
        val peerTubeUploads = async {
            runCatching { registry.forId(peerTubeId)?.channelUploads(peerTubeId)?.items.orEmpty() }
        }
        val youtubeUploads = async {
            runCatching { registry.forId(youtubeId)?.channelUploads(youtubeId)?.items.orEmpty() }
        }

        val channel = metadata.await().getOrNull()
        val fromPeerTube = peerTubeUploads.await()
        val fromYouTube = youtubeUploads.await()

        val videos = mergeLinkedChannelVideos(
            youtube = fromYouTube.getOrNull().orEmpty(),
            peerTube = fromPeerTube.getOrNull().orEmpty(),
        )

        LinkedChannelUiState(
            link = link,
            channel = channel,
            videos = videos,
            isLoading = false,
            // One side failing is not an error — half a page is still a usable page, and saying so
            // would hide the half that loaded.
            hasError = channel == null && videos.isEmpty(),
            errorLog = listOfNotNull(
                fromPeerTube.exceptionOrNull()?.messageOrType(),
                fromYouTube.exceptionOrNull()?.messageOrType(),
            ).joinToString("; "),
        )
    }

    fun retry() = load(_uiState.value.requestedChannelId)

    /** Subscribes the PeerTube half; the mirror carries it to YouTube when that is switched on. */
    fun toggleSubscription() {
        val state = _uiState.value
        val link = state.link ?: return
        viewModelScope.launch {
            if (state.isSubscribed) {
                subscriptions.unsubscribe(link.peerTubeChannelId)
            } else {
                subscriptions.subscribe(
                    ChannelSubscription(
                        channelId = link.peerTubeChannelId,
                        channelName = state.channel?.name ?: link.peerTubeChannelName,
                        channelThumbnail = state.channel?.thumbnailUrl.orEmpty(),
                    )
                )
            }
            mirror.mirror(link.peerTubeChannelId, subscribed = !state.isSubscribed)
        }
    }

    fun unsubscribe() {
        val link = _uiState.value.link ?: return
        viewModelScope.launch {
            subscriptions.unsubscribe(link.peerTubeChannelId)
            mirror.mirror(link.peerTubeChannelId, subscribed = false)
        }
    }

    private fun observeSubscription(channelId: String) {
        subscriptionJob?.cancel()
        subscriptionJob = viewModelScope.launch {
            subscriptions.isSubscribed(channelId).collect { subscribed ->
                _uiState.update { it.copy(isSubscribed = subscribed) }
            }
        }
    }

    private fun Throwable.messageOrType(): String = message ?: this::class.java.simpleName

    private companion object {
        const val NO_LINK = "This channel has no linked counterpart"
    }
}

data class LinkedChannelUiState(
    /** The id the page was opened with — either half of the pair. */
    val requestedChannelId: String = "",
    val link: ChannelLink? = null,
    /** PeerTube-side metadata; the header's source. */
    val channel: Channel? = null,
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val errorLog: String = "",
    val isSubscribed: Boolean = false,
) {
    val displayName: String
        get() = channel?.name?.takeIf { it.isNotBlank() }
            ?: link?.youtubeChannelName?.takeIf { it.isNotBlank() }
            ?: link?.peerTubeChannelName.orEmpty()
}
