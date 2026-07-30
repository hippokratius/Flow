/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.ui.screens.channel.peertube

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.ContentSourceRegistry
import io.github.aedev.flow.data.source.SourceCursor
import io.github.aedev.flow.data.source.contentId
import io.github.aedev.flow.data.source.link.ChannelLinkStore
import io.github.aedev.flow.data.source.link.ChannelLinkSubscriptionMirror
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Channel page for a federated (PeerTube) channel.
 *
 * Separate from [io.github.aedev.flow.ui.screens.channel.ChannelViewModel], which holds a raw
 * NewPipe `ChannelInfo` in its state and drives six YouTube-only tabs. Four of those tabs have no
 * PeerTube equivalent, so sharing the state would mean carrying four permanently empty ones.
 *
 * No Paging 3 either: [io.github.aedev.flow.data.source.ContentSource.channelUploads] already
 * returns a page plus a cursor, so an explicit [loadMore] at the end of the list is the whole
 * mechanism — a `PagingSource` would only re-wrap it.
 */
@HiltViewModel
class PeerTubeChannelViewModel @Inject constructor(
    private val registry: ContentSourceRegistry,
    private val links: ChannelLinkStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val subscriptions = SubscriptionRepository.getInstance(context)

    /** Carries a subscription over to the linked YouTube channel, when the user set one up. */
    private val mirror = ChannelLinkSubscriptionMirror(links, subscriptions)

    private val _uiState = MutableStateFlow(PeerTubeChannelUiState())
    val uiState: StateFlow<PeerTubeChannelUiState> = _uiState.asStateFlow()

    private var nextCursor: SourceCursor? = null
    private var loadJob: Job? = null
    private var subscriptionJob: Job? = null

    fun load(channelId: String) {
        if (channelId.isBlank()) return
        if (_uiState.value.channelId == channelId && loadJob?.isActive == true) return

        loadJob?.cancel()
        nextCursor = null
        _uiState.value = PeerTubeChannelUiState(channelId = channelId, isLoading = true)
        observeSubscription(channelId)

        loadJob = viewModelScope.launch {
            val id = channelId.contentId
            val source = registry.forId(id)
            if (source == null) {
                _uiState.update { it.copy(isLoading = false, errorLog = NO_SOURCE, hasError = true) }
                return@launch
            }

            // Metadata and the first page are independent requests; the header should not wait for
            // the videos, nor the videos for the header.
            val channel = runCatching { source.channel(id) }
            val page = runCatching { source.channelUploads(id) }

            val loaded = channel.getOrNull()
            // Deduplicated because the list is keyed by video id and a duplicate key crashes a
            // LazyColumn rather than merely looking odd.
            val videos = page.getOrNull()?.items.orEmpty().distinctBy { it.id }
            nextCursor = page.getOrNull()?.next

            val failure = listOfNotNull(
                channel.exceptionOrNull()?.messageOrType(),
                page.exceptionOrNull()?.messageOrType(),
                page.getOrNull()?.errors?.joinToString("; ") { it.message }?.takeIf { it.isNotBlank() },
            ).joinToString("; ")

            _uiState.update {
                it.copy(
                    channel = loaded,
                    videos = videos,
                    isLoading = false,
                    // Only a total miss is an error. A reachable channel with no uploads yet, or one
                    // whose metadata endpoint 404s while its videos load, still has a usable page.
                    hasError = loaded == null && videos.isEmpty(),
                    errorLog = failure,
                    hasMore = nextCursor != null,
                )
            }
        }
    }

    fun loadMore() {
        val cursor = nextCursor ?: return
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore) return

        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val id = state.channelId.contentId
            val source = registry.forId(id)
            val page = source?.let { runCatching { it.channelUploads(id, cursor) }.getOrNull() }
            nextCursor = page?.next

            _uiState.update { current ->
                val known = current.videos.mapTo(mutableSetOf()) { video -> video.id }
                current.copy(
                    videos = current.videos +
                        page?.items.orEmpty().distinctBy { it.id }.filterNot { it.id in known },
                    isLoadingMore = false,
                    hasMore = nextCursor != null,
                )
            }
        }
    }

    fun retry() = load(_uiState.value.channelId)

    fun toggleSubscription() {
        val state = _uiState.value
        val channel = state.channel ?: return
        viewModelScope.launch {
            if (state.isSubscribed) {
                subscriptions.unsubscribe(state.channelId)
            } else {
                subscriptions.subscribe(
                    ChannelSubscription(
                        channelId = state.channelId,
                        channelName = channel.name,
                        channelThumbnail = channel.thumbnailUrl,
                    )
                )
            }
            mirror.mirror(state.channelId, subscribed = !state.isSubscribed)
        }
    }

    fun unsubscribe() {
        val channelId = _uiState.value.channelId.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            subscriptions.unsubscribe(channelId)
            mirror.mirror(channelId, subscribed = false)
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
        const val NO_SOURCE = "No content source handles this channel id"
    }
}

data class PeerTubeChannelUiState(
    val channelId: String = "",
    val channel: Channel? = null,
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val hasError: Boolean = false,
    /** Technical detail, shown only behind "copy logs" — never as the user-facing message. */
    val errorLog: String = "",
    val isSubscribed: Boolean = false,
)
