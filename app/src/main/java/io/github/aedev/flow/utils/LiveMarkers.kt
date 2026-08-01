/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.utils

import java.util.Locale

/**
 * Whether a view-count line describes people watching *now* rather than total views.
 *
 * "1.2K watching" marks a live video; "1.2K views" does not. The words come from YouTube in whatever
 * language was asked for, so an English-only check quietly stopped recognising live videos for every
 * other language. That was already true of the InnerTube paths, which have followed the user's
 * setting all along, and became true of the extractor when it stopped being pinned to English.
 */
fun String?.isLiveViewerText(): Boolean {
    if (isNullOrBlank()) return false
    val lower = lowercase(Locale.ROOT)
    return LIVE_VIEWER_MARKERS.any { lower.contains(it) }
}

private val LIVE_VIEWER_MARKERS = listOf(
    "watching", "viewer",
    // German: "1.234 Zuschauer", "… sehen gerade zu"
    "zuschauer", "sehen gerade",
)
