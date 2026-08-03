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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.source.SourceKind

/**
 * Marks which backend a video came from.
 *
 * Colour comes from the `inverseSurface`/`inverseOnSurface` pair rather than the fixed
 * black-and-white scrim the duration and LIVE pills in `VideoCard` use. That pair is opaque and
 * flips with the theme, so the badge stays legible over any thumbnail in both light and dark mode —
 * and unlike those pills it does not hardcode colours, which `AGENTS.md` forbids. The same idiom is
 * already used by the "downloaded" marker in `LibraryShelfCards`.
 *
 * Both icons are monochrome outlined glyphs so the two sources read as one family. The YouTube
 * brand vector in `res/drawable/ic_youtube.xml` is deliberately not used: it is a red plate with a
 * white triangle, so tinting it to a single colour collapses it into a solid blob, and shipping a
 * brand mark in a fork invites trademark questions for no gain.
 */
@Composable
fun VideoSourceBadge(
    source: SourceKind,
    modifier: Modifier = Modifier,
) {
    // Local files are not a "somewhere else" the user needs warning about, and the library already
    // keeps them apart.
    if (source == SourceKind.LOCAL) return

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Icon(
            imageVector = when (source) {
                // Same glyph the PeerTube entry uses in Settings — not a new piece of vocabulary.
                SourceKind.PEERTUBE -> Icons.Outlined.Hub
                else -> Icons.Outlined.SmartDisplay
            },
            contentDescription = stringResource(
                when (source) {
                    SourceKind.PEERTUBE -> R.string.source_badge_peertube
                    else -> R.string.source_badge_youtube
                }
            ),
            modifier = Modifier
                .size(20.dp)
                .padding(3.dp),
        )
    }
}
