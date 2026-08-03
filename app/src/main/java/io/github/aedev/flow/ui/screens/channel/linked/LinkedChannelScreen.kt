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

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.ChannelAvatarImage
import io.github.aedev.flow.ui.components.ChannelBanner
import io.github.aedev.flow.ui.components.VideoCardHorizontal
import io.github.aedev.flow.ui.screens.channel.ChannelRequestErrorState
import io.github.aedev.flow.ui.screens.channel.SubscribeButton
import io.github.aedev.flow.utils.formatSubscriberCount

/**
 * One page for a creator who publishes on both YouTube and PeerTube.
 *
 * The uploads of both channels in a single list, newest first, with a video that exists on both sides
 * appearing once — as the PeerTube copy. That is the feature made visible: the same creator, the
 * decentralised source preferred, without the user having to know there are two channels.
 *
 * The source badge on each card (from `VideoCard`) is what keeps this honest — the list is mixed, so
 * the user can always see which platform a given video is coming from.
 *
 * The YouTube channel page's six tabs (Shorts, Live, playlists, posts, in-channel search) are not
 * reproduced here; they would mean rebuilding a 1400-line upstream screen. "Watch on YouTube only"
 * leads to that page untouched.
 */
@Composable
fun LinkedChannelScreen(
    channelId: String,
    onVideoClick: (Video) -> Unit,
    onBackClick: () -> Unit,
    onOpenYoutubeChannel: (String) -> Unit,
    viewModel: LinkedChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(channelId) { viewModel.load(channelId) }

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
                        text = state.displayName,
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    state.channel?.bannerUrl?.takeIf { it.isNotBlank() }?.let { banner ->
                        item("banner") { ChannelBanner(imageUrl = banner) }
                    }

                    item("header") {
                        LinkedChannelHeader(
                            state = state,
                            onSubscribeClick = viewModel::toggleSubscription,
                            onUnsubscribeClick = viewModel::unsubscribe,
                            onOpenYoutubeChannel = {
                                state.link?.youtubeChannelId?.let(onOpenYoutubeChannel)
                            }
                        )
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
                }
            }
        }
    }
}

@Composable
private fun LinkedChannelHeader(
    state: LinkedChannelUiState,
    onSubscribeClick: () -> Unit,
    onUnsubscribeClick: () -> Unit,
    onOpenYoutubeChannel: () -> Unit,
) {
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
                url = state.channel?.thumbnailUrl,
                contentDescription = state.displayName,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = state.displayName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.linked_channel_both_sources),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.channel?.instanceHost?.takeIf { it.isNotBlank() }?.let { host ->
                    Text(
                        text = stringResource(R.string.peertube_channel_instance, host),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.channel?.subscriberCount
                    ?.let(::formatSubscriberCount)
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        Text(
                            text = stringResource(R.string.peertube_channel_subscribers, it),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
            }
        }

        SubscribeButton(
            isSubscribed = state.isSubscribed,
            // Upload notifications are a YouTube-RSS feature and do not reach federated channels.
            isNotificationsEnabled = false,
            onSubscribeClick = onSubscribeClick,
            onUnsubscribeClick = onUnsubscribeClick,
            onNotificationChange = {}
        )

        // The escape hatch to the untouched YouTube page, which still has the tabs this one lacks.
        TextButton(onClick = onOpenYoutubeChannel) {
            Icon(
                imageVector = Icons.Outlined.SmartDisplay,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(18.dp)
            )
            Text(stringResource(R.string.linked_channel_youtube_only))
        }
    }
}
