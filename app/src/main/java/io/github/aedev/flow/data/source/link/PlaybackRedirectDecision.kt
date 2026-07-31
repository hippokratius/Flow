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

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.SourceKind
import io.github.aedev.flow.data.source.contentId

/*
 * Should this tap open the PeerTube copy instead?
 *
 * Split out of the ViewModel as a pure function on purpose. Inside `VideoPlayerViewModel` the answer
 * would only be observable by starting playback — the one thing that cannot be unit tested here — and
 * this is the decision that silently changes what the user gets to watch.
 */

/**
 * The PeerTube channel to look for a counterpart in, or null when this video plays as tapped.
 *
 * Returning a channel id rather than a boolean keeps the expensive half — one network round trip to
 * that channel's uploads — out of the decision entirely: everything that can rule the redirect out is
 * answered from memory first, so an unlinked video pays nothing at all.
 *
 * Ruled out, in order of how often it applies:
 * - the video is not a YouTube one (this moves *towards* PeerTube, never away from it, and local
 *   media has nothing to move to); read off the id, which is authoritative, rather than
 *   [Video.source], which is a cached hint,
 * - the redirect is switched off globally,
 * - the user chose YouTube for this very video,
 * - the video carries no title or duration, so [findCounterpart] could never match it anyway — this
 *   covers the placeholder `Video`s that deep links and "previous video" construct from a bare id,
 * - the channel has no counterpart.
 */
fun peerTubeCounterpartChannelId(
    video: Video,
    links: List<ChannelLink>,
    redirectEnabled: Boolean,
    optedOutVideoIds: Set<String>,
): String? {
    if (video.id.contentId.kind != SourceKind.YOUTUBE) return null
    if (!redirectEnabled) return null
    if (video.id in optedOutVideoIds) return null
    if (video.title.isBlank() || video.duration <= 0) return null
    if (video.channelId.isBlank()) return null

    return ChannelLinks.forYouTube(links, video.channelId)
        ?.peerTubeChannelId
        ?.takeIf { it.isNotBlank() }
}
