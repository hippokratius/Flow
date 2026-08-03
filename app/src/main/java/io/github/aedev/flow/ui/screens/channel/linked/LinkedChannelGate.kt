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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.aedev.flow.data.source.SourceKind
import io.github.aedev.flow.data.source.contentId
import io.github.aedev.flow.data.source.link.channelLinkStore

/**
 * Sends a linked channel to the shared page and everything else to its own.
 *
 * The decision lives here, inside the destination, rather than in `navigateToYoutubeChannel`. Looking
 * a link up is a suspend call against DataStore, and that function is an ordinary
 * `NavHostController` extension called from eight synchronous places — it cannot await anything. So
 * the route stays one route and only its content differs, which also means the back stack is
 * unchanged and "watch on YouTube only" is a parameter rather than a second destination.
 *
 * [channelId] may be null when the caller cannot determine one — a YouTube channel addressed by
 * `@handle` rather than by `UC…` id. Then there is nothing to look a link up by and the plain page is
 * shown, which is the same behaviour as before this existed.
 */
@Composable
fun LinkedChannelGate(
    channelId: String?,
    forcePlain: Boolean,
    linked: @Composable (linkedChannelId: String) -> Unit,
    plain: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // Tri-state: null means "not looked up yet". Rendering the plain page during the lookup and
    // swapping it out a moment later would flash a whole channel screen.
    val isLinked by produceState<Boolean?>(initialValue = null, channelId, forcePlain) {
        if (forcePlain || channelId.isNullOrBlank() ||
            channelId.contentId.kind == SourceKind.LOCAL
        ) {
            value = false
            return@produceState
        }
        val store = channelLinkStore(context)
        value = when (channelId.contentId.kind) {
            SourceKind.YOUTUBE -> store.forYouTube(channelId) != null
            SourceKind.PEERTUBE -> store.forPeerTube(channelId) != null
            SourceKind.LOCAL -> false
        }
    }

    when (isLinked) {
        null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        true -> linked(channelId.orEmpty())
        false -> plain()
    }
}
