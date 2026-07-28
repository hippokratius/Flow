/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.data.source

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Source-agnostic description of what to hand the player.
 *
 * Reuses NewPipe's stream types instead of inventing new ones. `EnhancedPlayerManager.setStreams`,
 * `MediaLoader`, `VideoPlaybackResolver`, `StreamProcessor` and `PlayerQualityManager` all already
 * speak them, so a bespoke type would need an adapter at every one of those boundaries.
 *
 * How a stream is delivered is carried on the [VideoStream] itself, via `deliveryMethod` —
 * `VideoPlaybackResolver` dispatches on exactly that. Note that the `hlsUrl` parameter of
 * `setStreams` is *not* usable here: it is discarded for anything that is not a live stream.
 */
data class PlaybackSpec(
    val id: ContentId,
    val title: String,
    val durationSeconds: Long,
    val videoStreams: List<VideoStream>,
    val audioStreams: List<AudioStream> = emptyList(),
    val subtitles: List<SubtitlesStream> = emptyList(),
    val isLive: Boolean = false,
    /** ActivityPub object URL, for Fediverse like/boost/comment. Null outside the Fediverse. */
    val apUrl: String? = null,
)
