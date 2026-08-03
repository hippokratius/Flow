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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.SourceKind
import io.github.aedev.flow.data.source.contentId

/**
 * Switches the description and comments between the two copies of one upload.
 *
 * The same video on YouTube and on PeerTube carries two separate conversations, and only one of them
 * is reachable from the copy that happens to be playing. This is the door between them; playback
 * itself does not move.
 *
 * Renders nothing when there is no counterpart, which is every video of an unlinked channel — so it
 * can sit unconditionally in the info panel.
 */
/**
 * What to call this video's source in one word: the instance for a federated video, "YouTube"
 * otherwise. The instance says more than "PeerTube" does — which server a video came from is the
 * part that actually varies.
 */
@Composable
fun videoSourceLabel(video: Video): String =
    if (video.id.contentId.kind == SourceKind.PEERTUBE) {
        video.instanceHost?.takeIf { it.isNotBlank() } ?: stringResource(R.string.video_source_peertube)
    } else {
        stringResource(R.string.video_source_youtube)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSourceTabs(
    playingVideo: Video,
    counterpartVideo: Video?,
    selectedVideoId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (counterpartVideo == null) return

    val options = listOf(playingVideo, counterpartVideo)
        .sortedBy { it.id.contentId.kind != SourceKind.PEERTUBE }

    // No horizontal padding: this sits inside VideoInfoSection's own padded column, aligned with
    // the title above it and the view count below.
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option.id == selectedVideoId,
                onClick = { onSelect(option.id) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        text = videoSourceLabel(option),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            )
        }
    }
}
