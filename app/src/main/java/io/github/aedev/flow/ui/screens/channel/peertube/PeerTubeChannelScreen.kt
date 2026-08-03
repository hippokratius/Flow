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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.contentId
import io.github.aedev.flow.ui.components.ChannelAvatarImage
import io.github.aedev.flow.ui.components.ChannelBanner
import io.github.aedev.flow.ui.components.LinkedChannelRow
import io.github.aedev.flow.ui.components.VideoCardHorizontal
import io.github.aedev.flow.ui.screens.channel.ChannelRequestErrorState
import io.github.aedev.flow.ui.screens.channel.SubscribeButton
import io.github.aedev.flow.utils.formatSubscriberCount

/**
 * Channel page for a PeerTube channel.
 *
 * One list, no tabs: PeerTube has no Shorts, no live tab and no community posts, so the six-tab
 * layout of the YouTube channel page would be five empty tabs. The building blocks are shared
 * though — banner, subscribe button, video cards and the error state all come from the existing
 * screen rather than being rebuilt here.
 */
@Composable
fun PeerTubeChannelScreen(
    channelId: String,
    onVideoClick: (Video) -> Unit,
    onBackClick: () -> Unit,
    onOpenLinkedChannel: (String) -> Unit = {},
    viewModel: PeerTubeChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(channelId) { viewModel.load(channelId) }

    val listState = rememberLazyListState()
    val shouldLoadMore by remember(state.videos.size, state.hasMore) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            state.hasMore && last >= state.videos.size - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                    Text(
                        text = state.channel?.name.orEmpty(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()

                state.hasError -> ChannelRequestErrorState(
                    message = stringResource(R.string.peertube_channel_error),
                    errorLog = state.errorLog,
                    onRetry = viewModel::retry
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    state.channel?.let { channel ->
                        if (channel.bannerUrl.isNotBlank()) {
                            item("banner") { ChannelBanner(imageUrl = channel.bannerUrl) }
                        }
                        item("header") {
                            PeerTubeChannelHeader(
                                channel = channel,
                                isSubscribed = state.isSubscribed,
                                onSubscribeClick = viewModel::toggleSubscription,
                                onUnsubscribeClick = viewModel::unsubscribe
                            )
                        }
                        // The link is bidirectional; without this the YouTube side would be a
                        // one-way door into PeerTube.
                        item("link") {
                            LinkedChannelRow(
                                channelId = channel.id,
                                onOpenLinkedChannel = onOpenLinkedChannel
                            )
                        }
                    }

                    if (state.videos.isEmpty()) {
                        item("empty") {
                            Text(
                                text = stringResource(R.string.no_videos_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    }

                    items(state.videos, key = { it.id }) { video ->
                        VideoCardHorizontal(video = video, onClick = { onVideoClick(video) })
                    }

                    if (state.isLoadingMore) {
                        item("loading-more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerTubeChannelHeader(
    channel: Channel,
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit,
    onUnsubscribeClick: () -> Unit,
) {
    var descriptionExpanded by remember { mutableStateOf(false) }
    // The handle is what the instance itself calls this channel, and for a mirrored channel it
    // carries the origin host — which is the only place the user can tell the two apart.
    val handle = channel.id.contentId.nativeId

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelAvatarImage(
                url = channel.thumbnailUrl,
                contentDescription = channel.name,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@$handle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                channel.instanceHost?.takeIf { it.isNotBlank() }?.let { host ->
                    Text(
                        text = stringResource(R.string.peertube_channel_instance, host),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                formatSubscriberCount(channel.subscriberCount).takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = stringResource(R.string.peertube_channel_subscribers, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SubscribeButton(
            isSubscribed = isSubscribed,
            // PeerTube upload notifications are not wired up, so offering the bell would promise
            // something that never arrives.
            isNotificationsEnabled = false,
            onSubscribeClick = onSubscribeClick,
            onUnsubscribeClick = onUnsubscribeClick,
            onNotificationChange = {}
        )

        channel.description.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (descriptionExpanded) Int.MAX_VALUE else COLLAPSED_DESCRIPTION_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .clickable { descriptionExpanded = !descriptionExpanded }
            )
        }
    }
}

private const val COLLAPSED_DESCRIPTION_LINES = 3
private const val LOAD_MORE_THRESHOLD = 4
