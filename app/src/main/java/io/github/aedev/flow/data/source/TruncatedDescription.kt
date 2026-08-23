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

/**
 * Whether this description is a stub that a listing cut short rather than the whole text.
 *
 * PeerTube puts a 250-character summary on every video it lists — and on the video detail too, on
 * instances older than 6.0 — with an ellipsis marking where it stopped. A video opened from the feed
 * therefore carries a description that ends mid-sentence, or, for a channel that lists its sources,
 * mid-link. The rest has to be fetched, so something has to be able to tell that it is missing.
 *
 * The ellipsis is the only marker there is: the stub is not always exactly 250 characters, because
 * the cut is made at a word boundary. A description whose author really did end on an ellipsis costs
 * one redundant request that returns the same text — the answer stays correct either way.
 */
fun String.isTruncatedDescription(): Boolean {
    val end = trimEnd()
    return end.endsWith("…") || end.endsWith("...")
}
