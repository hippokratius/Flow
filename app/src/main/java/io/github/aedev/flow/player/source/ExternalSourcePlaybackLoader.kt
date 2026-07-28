/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.player.source

import android.content.Context
import android.util.Log
import io.github.aedev.flow.data.source.ContentId
import io.github.aedev.flow.data.source.ContentSourceRegistry
import io.github.aedev.flow.data.source.PlaybackSpec
import io.github.aedev.flow.player.EnhancedPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts playback for content that does not go through the YouTube extraction pipeline.
 *
 * Lives outside `VideoPlayerViewModel` deliberately: that file is 3300+ lines and is one of the
 * most upstream-churned in the app, so it only gets a short dispatch branch and this class holds
 * the actual work.
 *
 * The player itself needs no changes. `setStreams` feeds `MediaLoader` and then
 * `VideoPlaybackResolver`, which dispatches on `VideoStream.deliveryMethod` — so an HLS-tagged
 * stream reaches `createHlsSource` and everything downstream (mini-player, background playback,
 * picture-in-picture, sleep timer) works unchanged.
 */
@Singleton
class ExternalSourcePlaybackLoader @Inject constructor(
    private val registry: ContentSourceRegistry,
) {
    /**
     * Resolves and starts [id]. Returns the spec on success, or null when the source could not
     * produce a playable stream — the caller surfaces that as a playback error.
     */
    suspend fun start(
        context: Context,
        id: ContentId,
        startPositionMs: Long = 0L,
    ): PlaybackSpec? {
        val source = registry.forId(id)
        if (source == null) {
            Log.w(TAG, "No content source registered for ${id.kind}")
            return null
        }

        val spec = runCatching { source.resolvePlayback(id) }
            .onFailure { Log.w(TAG, "Stream resolution failed for ${id.raw}", it) }
            .getOrNull()
            ?: return null

        return withContext(Dispatchers.Main) {
            val manager = EnhancedPlayerManager.getInstance()
            manager.initialize(context)
            manager.setStreams(
                videoId = id.raw,
                videoStream = spec.videoStreams.firstOrNull(),
                audioStream = spec.audioStreams.firstOrNull(),
                videoStreams = spec.videoStreams,
                audioStreams = spec.audioStreams,
                subtitles = spec.subtitles,
                durationSeconds = spec.durationSeconds,
                startPosition = startPositionMs,
            )
            manager.play()
            spec
        }
    }

    private companion object {
        const val TAG = "ExternalSourcePlayback"
    }
}
