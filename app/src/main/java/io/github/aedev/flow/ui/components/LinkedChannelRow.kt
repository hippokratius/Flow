/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.source.SourceKind
import io.github.aedev.flow.data.source.contentId
import io.github.aedev.flow.data.source.link.ChannelLink
import io.github.aedev.flow.data.source.link.channelLinkStore

/**
 * "This creator also publishes on PeerTube" — with a button that goes there.
 *
 * Renders nothing at all when [channelId] has no link, which is what makes it safe to drop into the
 * channel header, the video info sheet and the PeerTube channel page alike without each of those
 * having to ask first.
 *
 * Works in both directions: on a YouTube channel it points at the PeerTube counterpart, on a PeerTube
 * channel at the YouTube one. A one-way link would be a trapdoor.
 */
@Composable
fun LinkedChannelRow(
    channelId: String,
    onOpenLinkedChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val kind = channelId.contentId.kind

    // Read straight from the store rather than through a ViewModel: this appears inside upstream
    // composables that own their own state, and a per-row ViewModel would need one at each site.
    val link by produceState<ChannelLink?>(initialValue = null, channelId) {
        if (channelId.isBlank() || kind == SourceKind.LOCAL) return@produceState
        val store = channelLinkStore(context)
        value = when (kind) {
            SourceKind.YOUTUBE -> store.forYouTube(channelId)
            SourceKind.PEERTUBE -> store.forPeerTube(channelId)
            SourceKind.LOCAL -> null
        }
    }

    val current = link ?: return
    val targetId: String
    val targetName: String
    val label: String
    val icon = if (kind == SourceKind.YOUTUBE) Icons.Outlined.Hub else Icons.Outlined.SmartDisplay
    if (kind == SourceKind.YOUTUBE) {
        targetId = current.peerTubeChannelId
        targetName = current.peerTubeChannelName.ifBlank { targetId.contentId.nativeId }
        label = stringResource(R.string.channel_link_also_on_peertube)
    } else {
        targetId = current.youtubeChannelId
        targetName = current.youtubeChannelName.ifBlank { targetId }
        label = stringResource(R.string.channel_link_also_on_youtube)
    }
    if (targetId.isBlank()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = targetName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = { onOpenLinkedChannel(targetId) }) {
            Text(stringResource(R.string.channel_link_open))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(18.dp),
            )
        }
    }
}
