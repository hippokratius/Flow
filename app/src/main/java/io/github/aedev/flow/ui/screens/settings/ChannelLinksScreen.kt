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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.source.ContentSourceRegistry
import io.github.aedev.flow.data.source.contentId
import io.github.aedev.flow.data.source.link.ChannelLink
import io.github.aedev.flow.data.source.link.ChannelLinkStore
import io.github.aedev.flow.data.source.peertube.PeerTubeChannelReference
import io.github.aedev.flow.ui.components.ChannelCardHorizontal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Manage which YouTube channel corresponds to which PeerTube channel.
 *
 * The screen exists because the linking is manual by design — see [ChannelLink]. It can be opened
 * cold from settings, or from a YouTube channel page with [prefilledYoutubeChannelId] already set, in
 * which case the app knows who the user means and only the PeerTube half is left to find.
 */
@Composable
fun ChannelLinksScreen(
    onNavigateBack: () -> Unit,
    prefilledYoutubeChannelId: String = "",
    prefilledYoutubeChannelName: String = "",
    viewModel: ChannelLinksViewModel = hiltViewModel(),
) {
    val links by viewModel.links.collectAsState()
    val mirrorSubscriptions by viewModel.mirrorSubscriptions.collectAsState()
    val search by viewModel.searchState.collectAsState()
    val subscribedChannels by viewModel.subscribedChannels.collectAsState()
    val subscriptionNames by viewModel.subscriptionNames.collectAsState()

    // A picked channel, never a typed id. The id has to match what the channel page reports, and only
    // a subscription or a prefill from that very page is guaranteed to.
    var selectedYoutube by remember(prefilledYoutubeChannelId) {
        mutableStateOf(
            prefilledYoutubeChannelId.trim().takeIf { it.isNotEmpty() }?.let { id ->
                Channel(
                    id = id,
                    name = prefilledYoutubeChannelName.ifBlank { id },
                    thumbnailUrl = "",
                    subscriberCount = 0L,
                )
            }
        )
    }
    var subscriptionQuery by remember { mutableStateOf("") }
    var peerTubeQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.clearSearch() }

    fun linkTo(channelId: String, channelName: String) {
        val youtube = selectedYoutube ?: return
        viewModel.link(
            youtubeChannelId = youtube.id,
            youtubeChannelName = youtube.name,
            peerTubeChannelId = channelId,
            peerTubeChannelName = channelName,
        )
        peerTubeQuery = ""
        subscriptionQuery = ""
        selectedYoutube = null
        viewModel.clearSearch()
    }

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
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.channel_links_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.channel_links_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.channel_links_youtube_label),
                            style = MaterialTheme.typography.titleSmall
                        )
                        val selected = selectedYoutube
                        if (selected != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ChannelCardHorizontal(
                                    channel = selected,
                                    modifier = Modifier.weight(1f),
                                    onClick = {}
                                )
                                IconButton(onClick = { selectedYoutube = null }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(
                                            R.string.channel_links_clear_selection
                                        )
                                    )
                                }
                            }
                        } else if (subscribedChannels.isEmpty()) {
                            Text(
                                text = stringResource(R.string.channel_links_no_subscriptions),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            OutlinedTextField(
                                value = subscriptionQuery,
                                onValueChange = { subscriptionQuery = it },
                                label = {
                                    Text(stringResource(R.string.channel_links_youtube_filter))
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                            )
                            val matches = remember(subscribedChannels, subscriptionQuery) {
                                filterChannelsByName(subscribedChannels, subscriptionQuery)
                                    .take(MAX_PICKER_ROWS)
                            }
                            if (matches.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.channel_links_no_match),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            matches.forEach { channel ->
                                ChannelCardHorizontal(
                                    channel = channel,
                                    onClick = {
                                        selectedYoutube = channel
                                        subscriptionQuery = ""
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = peerTubeQuery,
                            onValueChange = { peerTubeQuery = it },
                            label = { Text(stringResource(R.string.channel_links_peertube_label)) },
                            supportingText = {
                                Text(stringResource(R.string.channel_links_peertube_hint))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { viewModel.search(peerTubeQuery) },
                                    enabled = peerTubeQuery.isNotBlank() && !search.isSearching
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = stringResource(
                                            R.string.channel_links_search
                                        )
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { viewModel.search(peerTubeQuery) }
                            )
                        )

                        // A pasted address is unambiguous, so it skips the search entirely.
                        val pasted = remember(peerTubeQuery) {
                            PeerTubeChannelReference.parse(peerTubeQuery)
                        }
                        if (pasted != null) {
                            Button(
                                onClick = { linkTo(pasted.raw, pasted.nativeId) },
                                enabled = selectedYoutube != null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(
                                        R.string.channel_links_link_pasted,
                                        pasted.nativeId
                                    )
                                )
                            }
                        }

                        if (search.isSearching) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }

                        if (search.hasSearched && search.results.isEmpty() && !search.isSearching) {
                            Text(
                                text = stringResource(R.string.channel_links_no_results),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        search.results.forEach { candidate ->
                            SearchResultRow(
                                channel = candidate,
                                canLink = selectedYoutube != null,
                                onLink = { linkTo(candidate.id, candidate.name) }
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroup {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.channel_links_mirror_title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.channel_links_mirror_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = mirrorSubscriptions,
                            onCheckedChange = viewModel::setMirrorSubscriptions
                        )
                    }
                }
            }

            if (links.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.channel_links_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    SettingsGroup {
                        Column {
                            links.forEachIndexed { index, link ->
                                if (index > 0) HorizontalDivider()
                                LinkRow(
                                    link = link,
                                    youtubeName = link.youtubeDisplayName(subscriptionNames),
                                    onRemove = { viewModel.unlink(link) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    channel: Channel,
    canLink: Boolean,
    onLink: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = channel.instanceHost.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onLink, enabled = canLink) {
            Text(stringResource(R.string.channel_links_link))
        }
    }
}

@Composable
private fun LinkRow(link: ChannelLink, youtubeName: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = youtubeName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(
                    R.string.channel_links_pair,
                    link.peerTubeChannelName.ifBlank { link.peerTubeChannelId.contentId.nativeId },
                    link.peerTubeInstanceHost.orEmpty()
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.channel_links_remove)
            )
        }
    }
}

@HiltViewModel
class ChannelLinksViewModel @Inject constructor(
    private val store: ChannelLinkStore,
    private val registry: ContentSourceRegistry,
    subscriptions: SubscriptionRepository,
) : ViewModel() {

    val links: StateFlow<List<ChannelLink>> = store.links
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val allSubscriptions = subscriptions.getAllSubscriptions()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The YouTube half is picked from here — see [youtubeSubscriptionChannels] for why filtered. */
    val subscribedChannels: StateFlow<List<Channel>> = allSubscriptions
        .map(::youtubeSubscriptionChannels)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Names for links stored before the picker existed, so the list never shows a bare id. */
    val subscriptionNames: StateFlow<Map<String, String>> = allSubscriptions
        .map(::subscriptionNamesById)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val mirrorSubscriptions: StateFlow<Boolean> = store.mirrorSubscriptions
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _searchState = MutableStateFlow(ChannelSearchState())
    val searchState: StateFlow<ChannelSearchState> = _searchState.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) return
        _searchState.value = ChannelSearchState(isSearching = true)
        viewModelScope.launch {
            val results = registry.byKey(ContentSourceRegistry.KEY_PEERTUBE)
                ?.let { source -> runCatching { source.searchChannels(query) }.getOrNull() }
                ?.items
                .orEmpty()
            _searchState.value = ChannelSearchState(
                results = results,
                isSearching = false,
                hasSearched = true,
            )
        }
    }

    fun clearSearch() {
        _searchState.value = ChannelSearchState()
    }

    fun link(
        youtubeChannelId: String,
        youtubeChannelName: String,
        peerTubeChannelId: String,
        peerTubeChannelName: String,
    ) {
        val candidate = ChannelLink(
            youtubeChannelId = youtubeChannelId.trim(),
            youtubeChannelName = youtubeChannelName.trim(),
            peerTubeChannelId = peerTubeChannelId.trim(),
            peerTubeChannelName = peerTubeChannelName.trim(),
            createdAt = System.currentTimeMillis(),
        )
        if (!candidate.isValid) return
        viewModelScope.launch { store.link(candidate) }
    }

    fun unlink(link: ChannelLink) {
        viewModelScope.launch { store.unlink(link.youtubeChannelId) }
    }

    fun setMirrorSubscriptions(enabled: Boolean) {
        viewModelScope.launch { store.setMirrorSubscriptions(enabled) }
    }
}

data class ChannelSearchState(
    val results: List<Channel> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
)

private const val MAX_PICKER_ROWS = 30
